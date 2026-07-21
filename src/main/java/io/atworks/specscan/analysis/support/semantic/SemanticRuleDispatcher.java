package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import io.atworks.specscan.analysis.support.candidate.BusinessRuleCandidateFactory;
import io.atworks.specscan.analysis.support.candidate.EvidenceMapper;
import io.atworks.specscan.analysis.support.rule.pack.*;
import java.util.*;

public final class SemanticRuleDispatcher {
    private final Set<String> enabledRuleIds;
    private final OptionalLookupClassifier optional = new OptionalLookupClassifier();
    private final PasswordEncoderSemanticClassifier password = new PasswordEncoderSemanticClassifier();
    private final BinaryConstraintClassifier binary = new BinaryConstraintClassifier();
    private final StandardGuardMethodClassifier standardGuards = new StandardGuardMethodClassifier(
        StandardGuardMethodRegistry.defaults());
    private final SemanticTargetResolver targets = new SemanticTargetResolver();
    private final OptimisticLockClassifier optimisticLock = new OptimisticLockClassifier();
    private final CompositeValidationClassifier composite = new CompositeValidationClassifier();
    private final BusinessRuleCandidateFactory factory = new BusinessRuleCandidateFactory();
    private final EvidenceMapper evidenceMapper = new EvidenceMapper();

    public SemanticRuleDispatcher(Set<String> enabledRuleIds) {
        this.enabledRuleIds = Set.copyOf(Objects.requireNonNull(enabledRuleIds, "enabledRuleIds"));
    }

    public SemanticDispatchResult dispatch(SemanticContext context, SemanticPredicate predicate) {
        FactNode condition = context.index().node(predicate.source().conditionNodeId());
        if (condition == null) return SemanticDispatchResult.empty();
        if (predicate.source().predicateType() == PredicateType.LOOKUP_CHAIN
                && (enabledRuleIds.contains(OptionalLookupFailureRule.ID)
                    || enabledRuleIds.contains(SpringDataFindByIdOrElseThrowRule.ID))) {
            return optional(context, predicate);
        }
        if (enabledRuleIds.contains(PasswordEncoderMatchFailureRule.ID)
                && containsResolvedPasswordCall(context, condition.id())) {
            return single(predicate, password.classify(context, predicate),
                PasswordEncoderMatchFailureRule.ID, BusinessRuleCategory.AUTHENTICATION,
                RuleEffect.BUSINESS_RESTRICTION, Set.of(PasswordEncoderMatchFailureRule.ID));
        }
        SemanticDispatchResult compositeResult = composite(context, predicate);
        if (!compositeResult.candidates().isEmpty()) return compositeResult;
        SemanticDispatchResult nullOrEmpty = nullOrEmpty(context, predicate, condition);
        if (!nullOrEmpty.candidates().isEmpty()) return nullOrEmpty;
        if (enabledRuleIds.contains(InputDomainMismatchGuardRule.ID)) {
            Optional<SemanticConstraintMatch> optimistic = optimisticLock.classify(context, predicate);
            if (optimistic.isPresent()) return single(predicate, optimistic,
                InputDomainMismatchGuardRule.ID, BusinessRuleCategory.VERSION_CONSISTENCY,
                RuleEffect.BUSINESS_RESTRICTION, Set.of(InputDomainMismatchGuardRule.ID));
        }
        return SemanticDispatchResult.empty();
    }

    private SemanticDispatchResult composite(SemanticContext context, SemanticPredicate predicate) {
        Optional<CompositeConstraintMatch> classified = composite.classify(context, predicate);
        if (classified.isEmpty()) return SemanticDispatchResult.empty();
        CompositeConstraintMatch value = classified.get();
        List<EvidenceRef> guardEvidence = value.activationGuards().stream()
            .map(NormalizedConstraint::expectedSource).filter(Objects::nonNull)
            .map(context.index()::node).filter(Objects::nonNull)
            .map(node -> evidenceMapper.fromFact(node, EvidenceRole.DOMAIN_ORIGIN)).toList();
        List<BusinessRuleCandidate> candidates = new ArrayList<>();
        for (SemanticConstraintMatch requirement : value.requirements()) {
            NormalizedConstraint resolved = resolveCompositeTarget(context, requirement.constraint(),
                value.activationGuards());
            List<EvidenceRef> evidence = new ArrayList<>(predicate.source().evidence());
            evidence.addAll(guardEvidence);
            FactNode source = context.index().node(requirement.constraint().expectedSource());
            if (source != null) evidence.add(evidenceMapper.fromFact(source, EvidenceRole.PREDICATE));
            TargetResolutionStatus target = resolved.targetPath() == null
                ? TargetResolutionStatus.UNRESOLVED : TargetResolutionStatus.RESOLVED;
            List<CandidateDiagnostic> diagnostics = new ArrayList<>(requirement.diagnostics());
            if (target == TargetResolutionStatus.UNRESOLVED) diagnostics.add(new CandidateDiagnostic(
                CandidateDiagnosticSeverity.WARNING, "CONDITIONAL_TARGET_UNRESOLVED",
                "Conditional numeric target could not be mapped to an API input", resolved.expectedSource()));
            String guardFingerprint = value.activationGuards().stream()
                .map(guard -> guard.operator() + guard.expectedValues()).sorted()
                .reduce((left, right) -> left + "|" + right).orElse("guard");
            candidates.add(factory.create(predicate.source().candidateId(),
                CompositeValidationClassifier.RULE_ID, BusinessRuleCategory.RANGE,
                RuleEffect.BUSINESS_RESTRICTION, target == TargetResolutionStatus.RESOLVED
                    ? ExtractionStatus.EXTRACTED : ExtractionStatus.PARTIAL,
                SemanticStatus.RESOLVED, target, new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
                    resolved.targetPath(), resolved.operator(), resolved.expectedValues(),
                    resolved.expectedSource()), 1.0, evidence, diagnostics,
                guardFingerprint + "|" + resolved.expectedSource()));
        }
        return new SemanticDispatchResult(candidates, Set.of());
    }

    private NormalizedConstraint resolveCompositeTarget(SemanticContext context,
                                                         NormalizedConstraint constraint,
                                                         List<NormalizedConstraint> guards) {
        if (constraint.targetPath() != null) return constraint;
        FactNode source = context.index().node(constraint.expectedSource());
        if (source == null) return constraint;
        List<FactNode> operands = context.index().targets(source.id(), FactEdgeType.OPERAND_OF).stream()
            .filter(node -> node.type() != FactNodeType.LITERAL && node.type() != FactNodeType.NULL_LITERAL
                && node.type() != FactNodeType.ENUM_CONSTANT)
            .toList();
        String path = operands.stream().map(node -> targets.resolve(context, node))
            .filter(target -> target.role() == SemanticTargetRole.REQUEST && target.path() != null)
            .map(SemanticTargetResolution::path).findFirst().orElse(null);
        if (path == null) {
            String field = operands.stream()
                .filter(node -> node.payload() instanceof FactNodePayload.FieldAccessPayload)
                .map(node -> ((FactNodePayload.FieldAccessPayload) node.payload()).fieldName())
                .findFirst().orElseGet(() -> operands.stream().map(FactNode::snippet)
                    .filter(value -> value.matches("[A-Za-z_$][A-Za-z0-9_$]*"))
                    .findFirst().orElse(null));
            String guardPath = guards.stream().map(NormalizedConstraint::targetPath)
                .filter(Objects::nonNull).findFirst().orElse(null);
            if (field != null && guardPath != null && guardPath.contains("."))
                path = guardPath.substring(0, guardPath.lastIndexOf('.') + 1) + field;
        }
        return new NormalizedConstraint(constraint.kind(), path, constraint.operator(),
            constraint.expectedValues(), constraint.expectedSource());
    }

    private SemanticDispatchResult nullOrEmpty(SemanticContext context, SemanticPredicate predicate,
                                               FactNode condition) {
        Optional<SemanticConstraintMatch> match;
        FactNode value;
        String ruleId;
        if (isBinary(condition)) {
            match = binary.classify(context, predicate);
            if (match.isEmpty() || !"NOT_NULL".equals(match.get().constraint().operator())) {
                return SemanticDispatchResult.empty();
            }
            value = context.index().targets(condition.id(), FactEdgeType.OPERAND_OF).stream()
                .filter(node -> node.type() != FactNodeType.NULL_LITERAL).findFirst().orElse(null);
            ruleId = NullRejectionGuardRule.ID;
        } else {
            match = standardGuards.classify(context, predicate);
            if (match.isEmpty()) match = instanceEmpty(context, predicate, condition);
            if (match.isEmpty()) return SemanticDispatchResult.empty();
            ruleId = "NOT_EMPTY".equals(match.get().constraint().operator())
                ? EmptyRejectionGuardRule.ID : NullRejectionGuardRule.ID;
            FactNode call = condition.type() == FactNodeType.METHOD_CALL ? condition
                : descendantCall(context, condition.id());
            value = call == null ? null : context.index().targets(call.id(),
                FactEdgeType.OPERAND_OF, "ARGUMENT").stream().findFirst().orElseGet(() ->
                    context.index().targets(call.id(), FactEdgeType.OPERAND_OF, "RECEIVER").stream()
                        .findFirst().orElse(null));
        }
        if (value == null || !enabledRuleIds.contains(ruleId)) return SemanticDispatchResult.empty();
        SemanticTargetResolution target = targets.resolve(context, value);
        if (target.role() != SemanticTargetRole.REQUEST) return SemanticDispatchResult.empty();
        NormalizedConstraint original = match.get().constraint();
        SemanticConstraintMatch directed = new SemanticConstraintMatch(new NormalizedConstraint(
            original.kind(), target.path(), original.operator(), original.expectedValues(),
            original.expectedSource()), match.get().resolutionQuality(), match.get().diagnostics());
        return single(predicate, Optional.of(directed), ruleId, BusinessRuleCategory.INVARIANT,
            RuleEffect.REQUEST_REQUIREMENT, Set.of(ruleId));
    }

    private Optional<SemanticConstraintMatch> instanceEmpty(SemanticContext context,
                                                              SemanticPredicate predicate,
                                                              FactNode condition) {
        FactNode call = condition.type() == FactNodeType.METHOD_CALL ? condition
            : descendantCall(context, condition.id());
        if (call == null || !(call.payload() instanceof FactNodePayload.MethodCallPayload payload)
                || !"isEmpty".equals(payload.methodName())
                || predicate.failurePolarity() != FailurePolarity.WHEN_TRUE) return Optional.empty();
        String signature = call.typeResolution().resolvedSignature();
        if (signature == null || !(signature.startsWith("java.util.Collection.isEmpty(")
                || signature.startsWith("java.util.List.isEmpty(")
                || signature.startsWith("java.util.Set.isEmpty("))) return Optional.empty();
        FactNode receiver = context.index().targets(call.id(), FactEdgeType.OPERAND_OF, "RECEIVER")
            .stream().findFirst().orElse(null);
        Optional<String> path = new SemanticInputPathResolver(context.index()).resolve(receiver);
        return path.map(value -> new SemanticConstraintMatch(new NormalizedConstraint(
            ConstraintKind.INPUT_LITERAL, value, "NOT_EMPTY", List.of(), call.id()),
            predicate.resolutionQuality(), List.of()));
    }

    private SemanticDispatchResult optional(SemanticContext context, SemanticPredicate predicate) {
        Optional<OptionalLookupClassification> classified = optional.classifyLookup(context, predicate);
        if (classified.isEmpty()) return SemanticDispatchResult.empty();
        OptionalLookupClassification value = classified.get();
        boolean repositoryLookup = value.kind() != OptionalLookupKind.JDK_OPTIONAL;
        String ruleId = repositoryLookup
            ? SpringDataFindByIdOrElseThrowRule.ID : OptionalLookupFailureRule.ID;
        if (!enabledRuleIds.contains(ruleId)) return SemanticDispatchResult.empty();
        Set<String> suppressed = repositoryLookup
            ? Set.of(SpringDataFindByIdOrElseThrowRule.ID, OptionalLookupFailureRule.ID)
            : Set.of(OptionalLookupFailureRule.ID);
        return single(predicate, Optional.of(value.semanticMatch()), ruleId,
            BusinessRuleCategory.EXISTENCE, RuleEffect.BUSINESS_RESTRICTION, suppressed);
    }

    private SemanticDispatchResult single(SemanticPredicate predicate,
                                          Optional<SemanticConstraintMatch> match,
                                          String ruleId, BusinessRuleCategory category,
                                          RuleEffect effect, Set<String> suppressed) {
        if (match.isEmpty()) return SemanticDispatchResult.empty();
        SemanticConstraintMatch semantic = match.get();
        ExtractionStatus extraction = predicate.source().extractionStatus() == ExtractionStatus.PARTIAL
                || semantic.resolutionQuality() == ResolutionQuality.PARTIAL
            ? ExtractionStatus.PARTIAL : predicate.source().extractionStatus();
        TargetResolutionStatus target = semantic.constraint().targetPath() == null
            ? TargetResolutionStatus.UNRESOLVED : TargetResolutionStatus.RESOLVED;
        List<CandidateDiagnostic> diagnostics = new ArrayList<>(predicate.source().diagnostics());
        semantic.diagnostics().forEach(item -> {
            if (diagnostics.stream().noneMatch(existing -> existing.code().equals(item.code())
                    && Objects.equals(existing.nodeId(), item.nodeId()))) diagnostics.add(item);
        });
        if (extraction == ExtractionStatus.PARTIAL && diagnostics.isEmpty()) {
            diagnostics.add(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
                "SEMANTIC_RESOLUTION_PARTIAL",
                "Semantic constraint was resolved with partial graph evidence",
                predicate.source().conditionNodeId()));
        }
        String fingerprint = predicate.source().evidence().stream()
            .map(item -> item.nodeId() + ":" + item.role()).distinct().sorted()
            .reduce((left, right) -> left + "|" + right).orElse(predicate.source().conditionNodeId());
        BusinessRuleCandidate candidate = factory.create(predicate.source().candidateId(), ruleId,
            category, effect, extraction, SemanticStatus.RESOLVED, target, semantic.constraint(), 1.0,
            predicate.source().evidence(), diagnostics, fingerprint);
        return new SemanticDispatchResult(List.of(candidate), suppressed);
    }

    private boolean containsResolvedPasswordCall(SemanticContext context, String root) {
        Deque<Map.Entry<String, Integer>> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(Map.entry(root, 0));
        while (!pending.isEmpty()) {
            Map.Entry<String, Integer> step = pending.removeFirst();
            String current = step.getKey();
            if (!visited.add(current)) continue;
            FactNode node = context.index().node(current);
            if (node != null && SpringSecurityPasswordMatcher.matches(context.index(), node)) return true;
            context.index().targets(current, FactEdgeType.OPERAND_OF)
                .forEach(target -> pending.addLast(Map.entry(target.id(), step.getValue())));
            if (step.getValue() < 2) {
                context.index().targets(current, FactEdgeType.CALLS, "TARGET")
                    .forEach(target -> pending.addLast(Map.entry(target.id(), step.getValue() + 1)));
                context.index().targets(current, FactEdgeType.RETURNS)
                    .forEach(target -> pending.addLast(Map.entry(target.id(), step.getValue())));
            }
        }
        return false;
    }

    private boolean isBinary(FactNode node) {
        return node.payload() instanceof FactNodePayload.ConditionPayload condition
            && condition.rootOperator() != null
            && Set.of("==", "!=").contains(condition.rootOperator());
    }

    private FactNode descendantCall(SemanticContext context, String root) {
        Deque<String> pending = new ArrayDeque<>(); Set<String> visited = new HashSet<>(); pending.add(root);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            for (FactNode target : context.index().targets(current, FactEdgeType.OPERAND_OF)) {
                if (target.type() == FactNodeType.METHOD_CALL) return target;
                pending.addLast(target.id());
            }
        }
        return null;
    }
}
