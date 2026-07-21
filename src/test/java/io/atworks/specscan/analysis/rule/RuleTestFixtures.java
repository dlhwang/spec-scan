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
    static FactCodeGraph delegatedBooleanGraph(boolean negatedGuard, boolean helperReturn,
                                                boolean failureOutcome) {
        SourceRange range = range();
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range, "api()",
            TypeResolution.resolvedSignature("sample.Api.api()"),
            new FactNodePayload.MethodPayload("sample.Api", "api()", true));
        FactNode outer = new FactNode("outer", FactNodeType.CONDITION, range,
            negatedGuard ? "!isValid(value)" : "isInvalid(value)", TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload(negatedGuard ? "UnaryExpr" : "MethodCallExpr",
                negatedGuard ? "!" : "MethodCallExpr"));
        FactNode outcome = new FactNode("outcome", failureOutcome ? FactNodeType.THROW : FactNodeType.RETURN,
            range, failureOutcome ? "throw failure" : "return success", TypeResolution.notApplicable(),
            new FactNodePayload.OutcomePayload(failureOutcome ? "THROW" : "RETURN", "statement"));
        FactNode call = new FactNode("call", FactNodeType.METHOD_CALL, range,
            negatedGuard ? "isValid(value)" : "isInvalid(value)", TypeResolution.resolvedSignature(
            negatedGuard ? "sample.Api.isValid(java.lang.String)" : "sample.Api.isInvalid(java.lang.String)"),
            new FactNodePayload.MethodCallPayload(negatedGuard ? "isValid" : "isInvalid", 1, true));
        FactNode helper = new FactNode("helper", FactNodeType.METHOD, range,
            negatedGuard ? "isValid(String)" : "isInvalid(String)", TypeResolution.resolvedSignature(
            negatedGuard ? "sample.Api.isValid(java.lang.String)" : "sample.Api.isInvalid(java.lang.String)"),
            new FactNodePayload.MethodPayload("sample.Api", negatedGuard ? "isValid(String)" : "isInvalid(String)", false));
        FactNode inner = new FactNode("inner", FactNodeType.CONDITION, range, "value == null",
            TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("BinaryExpr", "=="));
        FactNode returned = new FactNode("returned", FactNodeType.RETURN, range,
            "return " + helperReturn, TypeResolution.notApplicable(),
            new FactNodePayload.OutcomePayload("RETURN", "ReturnStmt"));
        FactNode literal = new FactNode("literal", FactNodeType.LITERAL, range,
            Boolean.toString(helperReturn), TypeResolution.notApplicable(),
            new FactNodePayload.LiteralPayload(Boolean.toString(helperReturn), "BooleanLiteralExpr"));
        return new FactCodeGraph("graph", root.id(),
            List.of(root, outer, outcome, call, helper, inner, returned, literal), List.of(
            new FactEdge("root-outer", root.id(), outer.id(), FactEdgeType.CONTROLS, -1, "IF"),
            new FactEdge("outer-outcome", outer.id(), outcome.id(), FactEdgeType.THEN_OUTCOME, -1,
                failureOutcome ? "THROW" : "RETURN"),
            new FactEdge("outer-call", outer.id(), call.id(), FactEdgeType.OPERAND_OF, 0, "CALL"),
            new FactEdge("call-helper", call.id(), helper.id(), FactEdgeType.CALLS, -1, "TARGET"),
            new FactEdge("helper-inner", helper.id(), inner.id(), FactEdgeType.CONTROLS, -1, "IF"),
            new FactEdge("inner-return", inner.id(), returned.id(), FactEdgeType.THEN_OUTCOME, -1, "RETURN"),
            new FactEdge("return-literal", returned.id(), literal.id(), FactEdgeType.OPERAND_OF, 0, "VALUE")));
    }
    static FactCodeGraph delegatedDepthGraph(boolean cycle) {
        SourceRange range = range();
        List<FactNode> nodes = new ArrayList<>(); List<FactEdge> edges = new ArrayList<>();
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range, "api()",
            TypeResolution.resolvedSignature("sample.Api.api()"),
            new FactNodePayload.MethodPayload("sample.Api", "api()", true));
        FactNode outer = new FactNode("outer", FactNodeType.CONDITION, range, "invalid()",
            TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("MethodCallExpr", "MethodCallExpr"));
        FactNode failure = outcome("failure", range);
        nodes.addAll(List.of(root, outer, failure));
        edges.add(new FactEdge("root-outer", "root", "outer", FactEdgeType.CONTROLS, -1, "IF"));
        edges.add(new FactEdge("outer-failure", "outer", "failure", FactEdgeType.THEN_OUTCOME, -1, "THROW"));
        String parentCondition = "outer";
        for (int depth = 1; depth <= 3; depth++) {
            String callId = "call-" + depth, helperId = "helper-" + depth, conditionId = "condition-" + depth;
            FactNode call = new FactNode(callId, FactNodeType.METHOD_CALL, range, "helper" + depth + "()",
                TypeResolution.resolvedSignature("sample.Api.helper" + depth + "()"),
                new FactNodePayload.MethodCallPayload("helper" + depth, 0, true));
            FactNode helper = new FactNode(helperId, FactNodeType.METHOD, range, "helper" + depth + "()",
                TypeResolution.resolvedSignature("sample.Api.helper" + depth + "()"),
                new FactNodePayload.MethodPayload("sample.Api", "helper" + depth + "()", false));
            FactNode condition = new FactNode(conditionId, FactNodeType.CONDITION, range, "guard" + depth,
                TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("MethodCallExpr", "MethodCallExpr"));
            FactNode returned = new FactNode("return-" + depth, FactNodeType.RETURN, range, "return true",
                TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("RETURN", "ReturnStmt"));
            FactNode literal = new FactNode("true-" + depth, FactNodeType.LITERAL, range, "true",
                TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload("true", "BooleanLiteralExpr"));
            nodes.addAll(List.of(call, helper, condition, returned, literal));
            edges.add(new FactEdge("operand-" + depth, parentCondition, callId, FactEdgeType.OPERAND_OF, 0, "CALL"));
            String target = cycle && depth == 3 ? "helper-1" : helperId;
            edges.add(new FactEdge("target-" + depth, callId, target, FactEdgeType.CALLS, -1, "TARGET"));
            edges.add(new FactEdge("control-" + depth, helperId, conditionId, FactEdgeType.CONTROLS, -1, "IF"));
            edges.add(new FactEdge("return-branch-" + depth, conditionId, returned.id(),
                FactEdgeType.THEN_OUTCOME, -1, "RETURN"));
            edges.add(new FactEdge("return-value-" + depth, returned.id(), literal.id(),
                FactEdgeType.OPERAND_OF, 0, "VALUE"));
            parentCondition = conditionId;
        }
        return new FactCodeGraph("graph", root.id(), nodes, edges);
    }
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
