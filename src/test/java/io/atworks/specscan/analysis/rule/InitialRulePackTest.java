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
        SourceRange range = range();
        FactNode condition = condition("condition", "&&", "phase != Phase.READY && phase != Phase.RETRYABLE");
        FactNode ready = enumConstant("ready", "READY"); FactNode retry = enumConstant("retry", "RETRYABLE");
        FactNode outcome = outcome(); FactNode root = root();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, condition, ready, retry, outcome), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("ready-edge", condition, ready, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("retry-edge", condition, retry, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
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
                NullRejectionGuardRule.ID, OptionalLookupFailureRule.ID, PasswordEncoderMatchFailureRule.ID,
                SpringDataFindByIdOrElseThrowRule.ID);
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
}
