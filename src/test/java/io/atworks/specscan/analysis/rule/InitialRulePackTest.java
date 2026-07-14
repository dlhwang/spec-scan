package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.EvidenceMapper;
import io.atworks.specscan.analysis.support.rule.*;
import io.atworks.specscan.analysis.support.rule.pack.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InitialRulePackTest {
    @Test void nullGuardRequiresTheNullPathToFail() {
        Fixture matching = comparison("==", FactNodeType.NULL_LITERAL, false, true);
        assertThat(new NullRejectionGuardRule().match(matching.graph, matching.predicate))
            .singleElement().extracting(BusinessRuleCandidate::ruleId).isEqualTo(NullRejectionGuardRule.ID);

        Fixture successNull = comparison("==", FactNodeType.NULL_LITERAL, false, false);
        assertThat(new NullRejectionGuardRule().match(successNull.graph, successNull.predicate)).isEmpty();
    }

    @Test void mismatchRequiresExactlyOneInputOrigin() {
        Fixture matching = comparison("!=", FactNodeType.FIELD_ACCESS, true, true);
        assertThat(new InputDomainMismatchGuardRule().match(matching.graph, matching.predicate))
            .singleElement().satisfies(candidate -> {
                assertThat(candidate.constraint().kind()).isEqualTo(ConstraintKind.INPUT_TO_DOMAIN);
                assertThat(candidate.evidence()).extracting(EvidenceRef::role)
                    .contains(EvidenceRole.INPUT_ORIGIN, EvidenceRole.DOMAIN_ORIGIN);
            });

        Fixture bothDomain = comparison("!=", FactNodeType.FIELD_ACCESS, false, true);
        assertThat(new InputDomainMismatchGuardRule().match(bothDomain.graph, bothDomain.predicate)).isEmpty();
    }

    @Test void enumGuardExtractsOnlySourceConstantsFromAnAllowedSet() {
        FactNode condition = condition("condition", "&&", "renamed expression without operator text");
        FactNode firstComparison = condition("first-comparison", "!=", "first comparison");
        FactNode secondComparison = condition("second-comparison", "!=", "second comparison");
        FactNode firstValue = field("first-value", "phase"); FactNode secondValue = field("second-value", "phase");
        FactNode ready = enumConstant("ready", "READY"); FactNode retry = enumConstant("retry", "RETRYABLE");
        FactNode outcome = outcome(); FactNode root = root();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, condition, firstComparison,
            secondComparison, firstValue, secondValue, ready, retry, outcome), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("first-comparison-edge", condition, firstComparison, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("second-comparison-edge", condition, secondComparison, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("first-value-edge", firstComparison, firstValue, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("ready-edge", firstComparison, ready, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("second-value-edge", secondComparison, secondValue, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("retry-edge", secondComparison, retry, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("failure", condition, outcome, FactEdgeType.THEN_OUTCOME, -1, "THROW")));
        PredicateCandidate predicate = predicate(graph, condition, PredicateType.COMPOSITE, outcome);

        assertThat(new EnumAllowedValueGuardRule().match(graph, predicate)).singleElement()
            .satisfies(candidate -> assertThat(candidate.constraint().expectedValues())
                .containsExactly("READY", "RETRYABLE"));
    }

    @Test void optionalCandidateRequiresTheResolvedJdkSignature() {
        SourceRange range = range(); FactNode root = root();
        FactNode call = new FactNode("call", FactNodeType.METHOD_CALL, range, "value.orElseThrow()",
            TypeResolution.resolvedSignature("java.util.Optional.orElseThrow()"),
            new FactNodePayload.MethodCallPayload("orElseThrow", 0, false));
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, call),
            List.of(edge("call-edge", root, call, FactEdgeType.CALLS, -1, "CALL")));
        CandidateDetectionResult detection = new DefaultValidationCandidateDetector(List.of())
            .detectReported(graph, new MethodScope(graph.graphId(), Set.of(root.id())));

        assertThat(detection.candidates()).singleElement().satisfies(predicate ->
            assertThat(new OptionalLookupFailureRule().match(graph, predicate)).singleElement()
                .extracting(BusinessRuleCandidate::category).isEqualTo(BusinessRuleCategory.EXISTENCE));
    }

    @Test void initialPacksHaveStableUniqueRuleIds() {
        assertThat(new RulePackRegistry(InitialRulePacks.all()).rules()).extracting(GraphRule::id)
            .containsExactlyInAnyOrder(EnumAllowedValueGuardRule.ID, InputDomainMismatchGuardRule.ID,
                NullRejectionGuardRule.ID, DelegatedGuardRule.ID, AuthorizationGuardCallRule.ID,
                OptionalLookupFailureRule.ID, PasswordEncoderMatchFailureRule.ID,
                SpringDataFindByIdOrElseThrowRule.ID);
        assertThat(InitialRuleCatalog.descriptors()).extracting(RuleDescriptor::ruleId)
            .containsExactlyInAnyOrder(EnumAllowedValueGuardRule.ID, InputDomainMismatchGuardRule.ID,
                NullRejectionGuardRule.ID, DelegatedGuardRule.ID, AuthorizationGuardCallRule.ID,
                OptionalLookupFailureRule.ID, PasswordEncoderMatchFailureRule.ID,
                SpringDataFindByIdOrElseThrowRule.ID);
    }

    @Test void passwordRuleUsesResolvedSignatureAndPreservesAnUnresolvedTarget() {
        PasswordFixture resolved = passwordFixture(true,
            "org.springframework.security.crypto.password.PasswordEncoder.matches(java.lang.CharSequence, java.lang.String)");
        assertThat(new PasswordEncoderMatchFailureRule().match(resolved.graph, resolved.predicate)).singleElement()
            .satisfies(candidate -> assertThat(candidate.targetStatus()).isEqualTo(TargetResolutionStatus.RESOLVED));

        PasswordFixture unresolved = passwordFixture(false,
            "org.springframework.security.crypto.password.PasswordEncoder.matches(java.lang.CharSequence, java.lang.String)");
        assertThat(new PasswordEncoderMatchFailureRule().match(unresolved.graph, unresolved.predicate)).singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.semanticStatus()).isEqualTo(SemanticStatus.RESOLVED);
                assertThat(candidate.targetStatus()).isEqualTo(TargetResolutionStatus.UNRESOLVED);
                assertThat(candidate.diagnostics()).extracting(CandidateDiagnostic::code)
                    .contains("PASSWORD_INPUT_ORIGIN_UNRESOLVED");
            });

        PasswordFixture sameName = passwordFixture(true,
            "sample.CustomEncoder.matches(java.lang.CharSequence, java.lang.String)");
        assertThat(new PasswordEncoderMatchFailureRule().match(sameName.graph, sameName.predicate)).isEmpty();

        PasswordFixture explicitFalse = passwordFixture(true,
            "org.springframework.security.crypto.password.PasswordEncoder.matches(java.lang.CharSequence, java.lang.String)",
            "==", "false");
        assertThat(new PasswordEncoderMatchFailureRule().match(explicitFalse.graph, explicitFalse.predicate))
            .singleElement().extracting(BusinessRuleCandidate::category)
            .isEqualTo(BusinessRuleCategory.AUTHENTICATION);
    }

    @Test void springDataRefinementHasExplicitPrecedenceIndependentOfPackOrder() {
        FactNode root = root();
        FactNode terminal = new FactNode("terminal", FactNodeType.METHOD_CALL, range(), "lookup.orElseThrow()",
            TypeResolution.resolvedSignature("java.util.Optional.orElseThrow()"),
            new FactNodePayload.MethodCallPayload("orElseThrow", 0, false));
        FactNode lookup = new FactNode("lookup", FactNodeType.METHOD_CALL, range(), "repository.findById(id)",
            TypeResolution.resolvedSignature("org.springframework.data.repository.CrudRepository.findById(java.lang.Object)"),
            new FactNodePayload.MethodCallPayload("findById", 1, false));
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, terminal, lookup), List.of(
            edge("root-call", root, terminal, FactEdgeType.CALLS, -1, "CALL"),
            edge("receiver", terminal, lookup, FactEdgeType.OPERAND_OF, -1, "RECEIVER")));
        List<RulePack> reversed = new ArrayList<>(InitialRulePacks.all()); Collections.reverse(reversed);
        GraphRuleEngineResult result = new DefaultGraphRuleEngine(new DefaultValidationCandidateDetector(List.of()), reversed)
            .evaluate(graph, new MethodScope(graph.graphId(), Set.of(root.id())));

        assertThat(result.candidates().businessRules()).extracting(BusinessRuleCandidate::ruleId)
            .contains(OptionalLookupFailureRule.ID, SpringDataFindByIdOrElseThrowRule.ID);
        assertThat(result.candidates().businessRules()).filteredOn(candidate ->
            candidate.ruleId().equals(OptionalLookupFailureRule.ID)).singleElement()
            .satisfies(candidate -> assertThat(candidate.diagnostics()).extracting(CandidateDiagnostic::code)
                .contains("LOWER_PRECEDENCE_MATCH"));
    }

    private PasswordFixture passwordFixture(boolean inputOrigin, String signature) {
        return passwordFixture(inputOrigin, signature, "!", null);
    }

    private PasswordFixture passwordFixture(boolean inputOrigin, String signature,
                                            String operator, String booleanValue) {
        FactNode root = root(); FactNode condition = condition("password-condition", operator, "password condition");
        FactNode call = new FactNode("matches-call", FactNodeType.METHOD_CALL, range(), "encoder.matches(first, second)",
            TypeResolution.resolvedSignature(signature), new FactNodePayload.MethodCallPayload("matches", 2, false));
        FactNode input = field("password-input", "first"); FactNode stored = field("password-stored", "second");
        FactNode parameter = new FactNode("password-parameter", FactNodeType.PARAMETER, range(), "String first",
            TypeResolution.unresolved("DECLARED_ONLY"), new FactNodePayload.ParameterPayload("first", 0, "String"));
        FactNode outcome = outcome();
        List<FactEdge> edges = new ArrayList<>(List.of(
            edge("password-control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("call-operand", condition, call, FactEdgeType.OPERAND_OF, 0, "CALL"),
            edge("input-argument", call, input, FactEdgeType.OPERAND_OF, 0, "ARGUMENT"),
            edge("stored-argument", call, stored, FactEdgeType.OPERAND_OF, 1, "ARGUMENT"),
            edge("password-failure", condition, outcome, FactEdgeType.THEN_OUTCOME, -1, "THROW")));
        List<FactNode> nodes = new ArrayList<>(List.of(root, condition, call, input, stored, parameter, outcome));
        if (booleanValue != null) {
            FactNode literal = new FactNode("boolean-literal", FactNodeType.LITERAL, range(), booleanValue,
                TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload(booleanValue, "BooleanLiteralExpr"));
            nodes.add(literal);
            edges.add(edge("boolean-operand", condition, literal, FactEdgeType.OPERAND_OF, 1, "RIGHT"));
        }
        if (inputOrigin) edges.add(edge("password-read", input, parameter, FactEdgeType.READS, -1, "DECLARATION"));
        FactCodeGraph graph = new FactCodeGraph("password-graph", root.id(), nodes, edges);
        return new PasswordFixture(graph, predicate(graph, condition, PredicateType.COMPOSITE, outcome));
    }

    private Fixture comparison(String operator, FactNodeType rightType, boolean inputOrigin, boolean failureThen) {
        FactNode root = root(); FactNode condition = condition("condition", operator, "requestValue " + operator + " storedValue");
        FactNode parameter = new FactNode("parameter", FactNodeType.PARAMETER, range(), "String requestValue",
            TypeResolution.unresolved("DECLARED_ONLY"), new FactNodePayload.ParameterPayload("requestValue", 0, "String"));
        FactNode left = field("left", "requestValue");
        FactNode right = rightType == FactNodeType.NULL_LITERAL
            ? new FactNode("right", FactNodeType.NULL_LITERAL, range(), "null", TypeResolution.notApplicable(), new FactNodePayload.NullLiteralPayload())
            : field("right", "storedValue");
        FactNode outcome = outcome();
        List<FactEdge> edges = new ArrayList<>(List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("left-edge", condition, left, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("right-edge", condition, right, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("failure", condition, outcome, failureThen ? FactEdgeType.THEN_OUTCOME : FactEdgeType.ELSE_OUTCOME, -1, "THROW")));
        if (inputOrigin) edges.add(edge("read", left, parameter, FactEdgeType.READS, -1, "DECLARATION"));
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, condition, parameter, left, right, outcome), edges);
        return new Fixture(graph, predicate(graph, condition,
            rightType == FactNodeType.NULL_LITERAL ? PredicateType.NULL_CHECK : PredicateType.COMPARISON, outcome));
    }

    private PredicateCandidate predicate(FactCodeGraph graph, FactNode condition, PredicateType type, FactNode outcome) {
        EvidenceMapper mapper = new EvidenceMapper();
        return new PredicateCandidate("predicate:" + condition.id(), graph.graphId(), condition.id(), type,
            ExtractionStatus.EXTRACTED, List.of(mapper.fromFact(condition, EvidenceRole.PREDICATE),
                mapper.fromFact(outcome, EvidenceRole.FAILURE_OUTCOME)), List.of());
    }
    private FactNode root() { return new FactNode("root", FactNodeType.API_METHOD, range(), "api()", TypeResolution.resolvedSignature("sample.Api.api()"), new FactNodePayload.MethodPayload("sample.Api", "api()", true)); }
    private FactNode condition(String id, String operator, String snippet) { return new FactNode(id, FactNodeType.CONDITION, range(), snippet, TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("BinaryExpr", operator)); }
    private FactNode field(String id, String name) { return new FactNode(id, FactNodeType.FIELD_ACCESS, range(), name, TypeResolution.unresolved("DECLARATION_NOT_RESOLVED"), new FactNodePayload.FieldAccessPayload(name, "NameExpr")); }
    private FactNode enumConstant(String id, String name) { return new FactNode(id, FactNodeType.ENUM_CONSTANT, range(), "Phase." + name, TypeResolution.resolvedType("sample.Phase"), new FactNodePayload.EnumConstantPayload("sample.Phase", name)); }
    private FactNode outcome() { return new FactNode("outcome", FactNodeType.THROW, range(), "throw failure", TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("THROW", "ThrowStmt")); }
    private FactEdge edge(String id, FactNode source, FactNode target, FactEdgeType type, int ordinal, String role) { return new FactEdge(id, source.id(), target.id(), type, ordinal, role); }
    private SourceRange range() { return new SourceRange("src/Test.java", 1, 1, 1, 30); }
    private record Fixture(FactCodeGraph graph, PredicateCandidate predicate) {}
    private record PasswordFixture(FactCodeGraph graph, PredicateCandidate predicate) {}
}
