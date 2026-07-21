package io.atworks.specscan.analysis.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import io.atworks.specscan.analysis.support.semantic.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SemanticContextTest {
    @Test void indexesOperandsCallsAndOutcomesOncePerContext() {
        Fixture fixture = fixture();
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(context.index()).isSameAs(context.index());
        assertThat(context.index().targets("condition", FactEdgeType.OPERAND_OF))
            .extracting(FactNode::id).containsExactly("call", "null");
        assertThat(context.index().targets("call", FactEdgeType.CALLS, "TARGET"))
            .extracting(FactNode::id).containsExactly("helper");
        assertThat(context.index().targets("condition", FactEdgeType.THEN_OUTCOME))
            .extracting(FactNode::id).containsExactly("throw");
    }

    @Test void normalizesExpressionPolarityMethodAndEvidenceWithoutChangingCandidate() {
        Fixture fixture = fixture();
        SemanticPredicate semantic = new SemanticContext(fixture.graph()).normalize(fixture.predicate());

        assertThat(semantic.source()).isSameAs(fixture.predicate());
        assertThat(semantic.expression().operator()).isEqualTo("==");
        assertThat(semantic.expression().operands()).extracting(SemanticExpression::nodeId)
            .containsExactly("call", "null");
        assertThat(semantic.failurePolarity()).isEqualTo(FailurePolarity.WHEN_TRUE);
        assertThat(semantic.methodSignature()).isEqualTo("sample.Guard.isMissing(java.lang.String)");
        assertThat(semantic.evidence()).isEqualTo(fixture.predicate().evidence());
        assertThat(semantic.resolutionQuality()).isEqualTo(ResolutionQuality.RESOLVED);
        assertThat(semantic.diagnostics()).isEmpty();
    }

    @Test void marksConditionalExpressionOutsideMvpAsPartial() {
        Fixture fixture = fixture();
        FactNode unsupported = new FactNode("unsupported", FactNodeType.CONDITION, range(),
            "flag ? left : right", TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("ConditionalExpr", "?:"));
        FactCodeGraph graph = new FactCodeGraph(fixture.graph().graphId(), fixture.graph().apiMethodNodeId(),
            java.util.stream.Stream.concat(fixture.graph().nodes().stream(), java.util.stream.Stream.of(unsupported)).toList(),
            java.util.stream.Stream.concat(fixture.graph().edges().stream(), java.util.stream.Stream.of(
                new FactEdge("unsupported-edge", "condition", unsupported.id(),
                    FactEdgeType.OPERAND_OF, 2, "VALUE"))).toList());

        SemanticPredicate semantic = new SemanticContext(graph).normalize(fixture.predicate());

        assertThat(semantic.resolutionQuality()).isEqualTo(ResolutionQuality.PARTIAL);
        assertThat(semantic.diagnostics()).extracting(CandidateDiagnostic::code)
            .containsExactly("UNSUPPORTED_SEMANTIC_EXPRESSION");
    }

    private Fixture fixture() {
        SourceRange range = new SourceRange("src/Test.java", 1, 1, 1, 30);
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range, "api()",
            TypeResolution.resolvedSignature("sample.Api.api()"),
            new FactNodePayload.MethodPayload("sample.Api", "api()", true));
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range,
            "isMissing(value) == null", TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("BinaryExpr", "=="));
        FactNode call = new FactNode("call", FactNodeType.METHOD_CALL, range, "isMissing(value)",
            TypeResolution.resolvedSignature("sample.Guard.isMissing(java.lang.String)"),
            new FactNodePayload.MethodCallPayload("isMissing", 1, true));
        FactNode nullValue = new FactNode("null", FactNodeType.NULL_LITERAL, range, "null",
            TypeResolution.notApplicable(), new FactNodePayload.NullLiteralPayload());
        FactNode helper = new FactNode("helper", FactNodeType.METHOD, range, "isMissing(String)",
            TypeResolution.resolvedSignature("sample.Guard.isMissing(java.lang.String)"),
            new FactNodePayload.MethodPayload("sample.Guard", "isMissing(String)", false));
        FactNode failure = new FactNode("throw", FactNodeType.THROW, range, "throw failure",
            TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("THROW", "ThrowStmt"));
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, condition, call, nullValue, helper, failure), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("call-operand", condition, call, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("null-operand", condition, nullValue, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("target", call, helper, FactEdgeType.CALLS, -1, "TARGET"),
            edge("failure", condition, failure, FactEdgeType.THEN_OUTCOME, -1, "THROW")));
        EvidenceRef predicateEvidence = new EvidenceRef(condition.id(), range.relativePath(), 1, 1, 1, 30,
            EvidenceRole.PREDICATE, condition.snippet());
        EvidenceRef failureEvidence = new EvidenceRef(failure.id(), range.relativePath(), 1, 1, 1, 30,
            EvidenceRole.FAILURE_OUTCOME, failure.snippet());
        PredicateCandidate predicate = new PredicateCandidate("predicate", graph.graphId(), condition.id(),
            PredicateType.COMPOSITE, ExtractionStatus.EXTRACTED,
            List.of(predicateEvidence, failureEvidence), List.of());
        return new Fixture(graph, predicate);
    }

    private SourceRange range() { return new SourceRange("src/Test.java", 1, 1, 1, 30); }

    private FactEdge edge(String id, FactNode source, FactNode target, FactEdgeType type,
                          int ordinal, String role) {
        return new FactEdge(id, source.id(), target.id(), type, ordinal, role);
    }

    private record Fixture(FactCodeGraph graph, PredicateCandidate predicate) {}
}
