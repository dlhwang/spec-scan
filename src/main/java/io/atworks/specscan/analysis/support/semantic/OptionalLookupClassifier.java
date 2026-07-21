package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.*;

public final class OptionalLookupClassifier implements SemanticConstraintClassifier {
    public String id() { return "optional-lookup"; }

    public Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate) {
        return classifyLookup(context, predicate).map(OptionalLookupClassification::semanticMatch);
    }

    public Optional<OptionalLookupClassification> classifyLookup(SemanticContext context,
                                                                  SemanticPredicate predicate) {
        if (predicate.source().predicateType() != PredicateType.LOOKUP_CHAIN) return Optional.empty();
        FactNode terminal = context.index().node(predicate.source().conditionNodeId());
        if (!isCallNamed(terminal, "orElseThrow")) return Optional.empty();
        FactNode lookup = receiver(context, terminal.id());
        if (isSpringDataFindById(lookup, terminal)) {
            return Optional.of(classification(context, predicate, terminal, lookup,
                OptionalLookupKind.SPRING_DATA_FIND_BY_ID));
        }
        if (isRepositoryOptionalLookup(lookup, terminal)) {
            return Optional.of(classification(context, predicate, terminal, lookup,
                OptionalLookupKind.SPRING_DATA_REPOSITORY_LOOKUP));
        }
        if (isJdkOptionalTerminal(terminal)) {
            return Optional.of(classification(context, predicate, terminal, lookup,
                OptionalLookupKind.JDK_OPTIONAL));
        }
        return Optional.empty();
    }

    private OptionalLookupClassification classification(SemanticContext context, SemanticPredicate predicate,
                                                         FactNode terminal, FactNode lookup,
                                                         OptionalLookupKind kind) {
        String inputPath = lookupInputPath(context, lookup).orElse(null);
        ResolutionQuality quality = inputPath == null && lookup != null
            ? ResolutionQuality.PARTIAL : predicate.resolutionQuality();
        List<CandidateDiagnostic> diagnostics = inputPath == null && lookup != null
            ? List.of(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
                "OPTIONAL_LOOKUP_INPUT_UNRESOLVED", "Lookup input path could not be resolved", lookup.id()))
            : List.of();
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY,
            inputPath, "EXISTS", List.of(), lookup == null ? terminal.id() : lookup.id());
        SemanticConstraintMatch match = new SemanticConstraintMatch(constraint, quality, diagnostics);
        return new OptionalLookupClassification(kind, match, terminal.id(), lookup == null ? null : lookup.id());
    }

    private Optional<String> lookupInputPath(SemanticContext context, FactNode lookup) {
        if (lookup == null) return Optional.empty();
        List<FactNode> arguments = context.index().targets(lookup.id(), FactEdgeType.OPERAND_OF, "ARGUMENT");
        return arguments.isEmpty() ? Optional.empty()
            : new SemanticInputPathResolver(context.index()).resolve(arguments.get(0));
    }

    private FactNode receiver(SemanticContext context, String terminalId) {
        return context.index().targets(terminalId, FactEdgeType.OPERAND_OF, "RECEIVER").stream()
            .findFirst().orElse(null);
    }

    private boolean isCallNamed(FactNode node, String name) {
        return node != null && node.payload() instanceof FactNodePayload.MethodCallPayload call
            && name.equals(call.methodName());
    }

    private boolean isJdkOptionalTerminal(FactNode terminal) {
        String signature = terminal.typeResolution().resolvedSignature();
        return terminal.typeResolution().status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.startsWith("java.util.Optional.orElseThrow(");
    }

    private boolean isSpringDataFindById(FactNode lookup, FactNode terminal) {
        if (!isCallNamed(lookup, "findById")) return false;
        String signature = lookup.typeResolution().resolvedSignature();
        if (lookup.typeResolution().status() != TypeResolutionStatus.RESOLVED || signature == null
                || !signature.contains(".findById(")) return false;
        if (signature.startsWith("org.springframework.data.repository.")) return true;
        int method = signature.indexOf(".findById(");
        String owner = method < 0 ? "" : signature.substring(0, method);
        String simpleOwner = owner.substring(owner.lastIndexOf('.') + 1);
        return simpleOwner.endsWith("Repository") && isJdkOptionalTerminal(terminal);
    }

    private boolean isRepositoryOptionalLookup(FactNode lookup, FactNode terminal) {
        if (lookup == null || !(lookup.payload() instanceof FactNodePayload.MethodCallPayload)
                || !isJdkOptionalTerminal(terminal)) return false;
        String signature = lookup.typeResolution().resolvedSignature();
        if (lookup.typeResolution().status() != TypeResolutionStatus.RESOLVED || signature == null)
            return false;
        int parameters = signature.indexOf('(');
        int method = parameters < 0 ? -1 : signature.lastIndexOf('.', parameters);
        if (method < 0) return false;
        String owner = signature.substring(0, method);
        String simpleOwner = owner.substring(owner.lastIndexOf('.') + 1);
        return owner.startsWith("org.springframework.data.repository.")
            || simpleOwner.endsWith("Repository");
    }
}
