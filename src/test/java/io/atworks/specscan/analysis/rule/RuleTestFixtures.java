package io.atworks.specscan.analysis.rule;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import io.atworks.specscan.analysis.support.candidate.*;
import io.atworks.specscan.analysis.support.rule.CandidateMatchKeyFactory;
import java.util.*;

final class RuleTestFixtures {
    static FactCodeGraph graph(FactNodeType outcomeType) {
        SourceRange range = range();
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range, "api()",
            TypeResolution.resolvedSignature("sample.Api.api()"), new FactNodePayload.MethodPayload("sample.Api", "api()", true));
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range, "allowed",
            TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("NameExpr", null));
        FactNode outcome = new FactNode("outcome", outcomeType, range, outcomeType.name(), TypeResolution.notApplicable(),
            new FactNodePayload.OutcomePayload(outcomeType.name(), outcomeType.name()));
        return new FactCodeGraph("graph", root.id(), List.of(root, condition, outcome), List.of(
            new FactEdge("controls", root.id(), condition.id(), FactEdgeType.CONTROLS, -1, "IF"),
            new FactEdge("outcome-edge", condition.id(), outcome.id(), FactEdgeType.THEN_OUTCOME, -1, outcomeType.name())));
    }
    static MethodScope scope() { return new MethodScope("graph", Set.of("root")); }
    static FactCodeGraph graphWithTwoThrows() {
        SourceRange range = range();
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range, "api()",
            TypeResolution.resolvedSignature("sample.Api.api()"), new FactNodePayload.MethodPayload("sample.Api", "api()", true));
        FactNode first = condition("condition-1", range);
        FactNode second = condition("condition-2", range);
        FactNode firstThrow = outcome("throw-1", range);
        FactNode secondThrow = outcome("throw-2", range);
        return new FactCodeGraph("graph", root.id(), List.of(root, first, second, firstThrow, secondThrow), List.of(
            new FactEdge("control-1", root.id(), first.id(), FactEdgeType.CONTROLS, -1, "IF"),
            new FactEdge("control-2", root.id(), second.id(), FactEdgeType.CONTROLS, -1, "IF"),
            new FactEdge("outcome-1", first.id(), firstThrow.id(), FactEdgeType.THEN_OUTCOME, -1, "THROW"),
            new FactEdge("outcome-2", second.id(), secondThrow.id(), FactEdgeType.THEN_OUTCOME, -1, "THROW")));
    }
    static PredicateCandidate predicate() {
        EvidenceRef evidence = evidence("condition", EvidenceRole.PREDICATE);
        return new PredicateCandidate(new DeterministicCandidateIdGenerator().forPredicate("graph", "condition"),
            "graph", "condition", PredicateType.UNKNOWN, ExtractionStatus.EXTRACTED, List.of(evidence), List.of());
    }
    static BusinessRuleCandidate ruleCandidate(String ruleId, BusinessRuleCategory category, String evidenceNode) {
        PredicateCandidate predicate = predicate();
        EvidenceRef evidence = evidence(evidenceNode, EvidenceRole.PREDICATE);
        String fingerprint = evidence.nodeId() + ":" + evidence.role();
        return new BusinessRuleCandidateFactory().create(predicate.candidateId(), ruleId, category,
            ExtractionStatus.EXTRACTED, SemanticStatus.RESOLVED, TargetResolutionStatus.NOT_APPLICABLE,
            null, 0.9, List.of(evidence), List.of(), fingerprint);
    }
    static GraphRule rule(String id, BusinessRuleCandidate... candidates) {
        return new GraphRule() {
            public String id() { return id; }
            public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }
            public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate candidate) {
                return List.of(candidates);
            }
        };
    }
    static GraphRule throwingRule(String id) {
        return new GraphRule() {
            public String id() { return id; }
            public RuleLayer layer() { return RuleLayer.JAVA_LANGUAGE; }
            public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate candidate) {
                throw new IllegalStateException("broken rule");
            }
        };
    }
    static EvidenceRef evidence(String nodeId, EvidenceRole role) {
        return new EvidenceRef(nodeId, "src/Test.java", 1, 1, 1, 10, role, "evidence");
    }
    static SourceRange range() { return new SourceRange("src/Test.java", 1, 1, 1, 10); }
    private static FactNode condition(String id, SourceRange range) {
        return new FactNode(id, FactNodeType.CONDITION, range, id, TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("NameExpr", null));
    }
    private static FactNode outcome(String id, SourceRange range) {
        return new FactNode(id, FactNodeType.THROW, range, "throw", TypeResolution.notApplicable(),
            new FactNodePayload.OutcomePayload("THROW", "ThrowStmt"));
    }
    private RuleTestFixtures() {}
}
