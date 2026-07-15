package io.atworks.specscan.analysis.candidate;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.candidate.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CandidateSupportTest {
    @Test
    void classifiesCompositeAndEnumComparisonFromTypedFacts() {
        FactNode condition = condition("condition", "&&");
        FactCodeGraph compositeGraph = graph(condition, List.of());
        assertThat(new PredicateTypeClassifier().classify(compositeGraph, condition))
            .isEqualTo(PredicateType.COMPOSITE);

        FactNode enumNode = new FactNode("enum", FactNodeType.ENUM_CONSTANT, range(), "CANCELLED",
            TypeResolution.resolvedType("sample.Status"),
            new FactNodePayload.EnumConstantPayload("sample.Status", "CANCELLED"));
        FactNode comparison = condition("comparison", "==");
        FactEdge operand = new FactEdge("edge", enumNode.id(), comparison.id(), FactEdgeType.OPERAND_OF, 1, "RIGHT");
        FactCodeGraph enumGraph = graph(comparison, List.of(enumNode), List.of(operand));

        assertThat(new PredicateTypeClassifier().classify(enumGraph, comparison))
            .isEqualTo(PredicateType.ENUM_COMPARISON);
    }

    @Test
    void accumulatorSortsCandidatesAndRejectsDanglingEvidence() {
        FactNode condition = condition("condition", "==");
        FactCodeGraph graph = graph(condition, List.of());
        EvidenceRef evidence = new EvidenceMapper().fromFact(condition, EvidenceRole.PREDICATE);
        DeterministicCandidateIdGenerator ids = new DeterministicCandidateIdGenerator();
        PredicateCandidate candidate = new PredicateCandidate(ids.forPredicate(graph.graphId(), condition.id()),
            graph.graphId(), condition.id(), PredicateType.COMPARISON, ExtractionStatus.EXTRACTED,
            List.of(evidence), List.of());
        CandidateResolutionAccumulator accumulator = new CandidateResolutionAccumulator(graph);
        accumulator.add(candidate);

        assertThat(accumulator.snapshot().predicates()).containsExactly(candidate);

        PredicateCandidate dangling = new PredicateCandidate("predicate:dangling", graph.graphId(), condition.id(),
            PredicateType.UNKNOWN, ExtractionStatus.EXTRACTED,
            List.of(new EvidenceRef("missing", "src/Test.java", 1, 1, 1, 2, EvidenceRole.PREDICATE, null)), List.of());
        CandidateResolutionAccumulator invalid = new CandidateResolutionAccumulator(graph);
        invalid.add(dangling);
        assertThatThrownBy(invalid::snapshot).hasMessageContaining("DANGLING_EVIDENCE_REFERENCE");
    }

    @Test
    void duplicateCandidateIsRejected() {
        FactNode condition = condition("condition", "==");
        FactCodeGraph graph = graph(condition, List.of());
        PredicateCandidate candidate = new PredicateCandidate("same", graph.graphId(), condition.id(),
            PredicateType.COMPARISON, ExtractionStatus.EXTRACTED,
            List.of(new EvidenceMapper().fromFact(condition, EvidenceRole.PREDICATE)), List.of());
        CandidateResolutionAccumulator accumulator = new CandidateResolutionAccumulator(graph);
        accumulator.add(candidate);
        assertThatThrownBy(() -> accumulator.add(candidate)).hasMessageContaining("DUPLICATE_CANDIDATE_ID");
    }

    @Test
    void accumulatorRejectsEvidenceThatExistsButIsNotReachableFromApiRoot() {
        FactNode condition = condition("condition", "==");
        FactNode disconnected = condition("disconnected", "!=");
        FactCodeGraph graph = graph(condition, List.of(disconnected));
        PredicateCandidate candidate = new PredicateCandidate("predicate:disconnected", graph.graphId(),
            condition.id(), PredicateType.COMPARISON, ExtractionStatus.EXTRACTED,
            List.of(new EvidenceMapper().fromFact(disconnected, EvidenceRole.PREDICATE)), List.of());
        CandidateResolutionAccumulator accumulator = new CandidateResolutionAccumulator(graph);
        accumulator.add(candidate);

        assertThatThrownBy(accumulator::snapshot).hasMessageContaining("UNREACHABLE_EVIDENCE_REFERENCE");
    }

    private FactCodeGraph graph(FactNode condition, List<FactNode> extras) { return graph(condition, extras, List.of()); }
    private FactCodeGraph graph(FactNode condition, List<FactNode> extras, List<FactEdge> edges) {
        FactNode root = new FactNode("root", FactNodeType.API_METHOD, range(), "void api()",
            TypeResolution.resolvedSignature("sample.Api.api()"), new FactNodePayload.MethodPayload("sample.Api", "api()", true));
        List<FactNode> nodes = new java.util.ArrayList<>(List.of(root, condition));
        nodes.addAll(extras);
        List<FactEdge> connected = new java.util.ArrayList<>();
        connected.add(new FactEdge("root-condition", root.id(), condition.id(), FactEdgeType.CONTROLS, 0, "BODY"));
        connected.addAll(edges);
        return new FactCodeGraph("graph-1", root.id(), nodes, connected);
    }
    private FactNode condition(String id, String operator) {
        return new FactNode(id, FactNodeType.CONDITION, range(), "condition", TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("BinaryExpr", operator));
    }
    private SourceRange range() { return new SourceRange("src/Test.java", 1, 1, 1, 20); }
}
