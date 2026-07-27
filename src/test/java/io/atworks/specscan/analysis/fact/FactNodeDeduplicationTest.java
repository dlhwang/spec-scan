package io.atworks.specscan.analysis.fact;

import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DeterministicFactNodeIdGenerator;
import io.atworks.specscan.analysis.support.fact.FactGraphAccumulator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FactNodeDeduplicationTest {

    private final DeterministicFactNodeIdGenerator ids = new DeterministicFactNodeIdGenerator();
    private final SourceRange range1 = new SourceRange("src/main/java/com/example/OrderService.java", 10, 5, 10, 25);
    private final SourceRange range2 = new SourceRange("src/main/java/com/example/OrderService.java", 15, 5, 15, 30);
    private final String owner = "com.example.OrderService.processOrder(OrderRequest)";

    @Test
    @DisplayName("1. 동일 AST occurrence(동일 relativePath, nodeType, startLine, startColumn, endLine, endColumn)는 동일한 노드 ID를 가짐")
    void sameAstOccurrenceProducesSingleNode() {
        String id1 = ids.generate(FactNodeType.FIELD_ACCESS, owner, range1, "field-access");
        String id2 = ids.generate(FactNodeType.FIELD_ACCESS, owner, range1, "field-access");

        assertThat(id1).isEqualTo(id2);

        FactNode node1 = new FactNode(id1, FactNodeType.FIELD_ACCESS, range1, "orderRequest",
                TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload("orderRequest", "NameExpr"));
        FactNode node2 = new FactNode(id2, FactNodeType.FIELD_ACCESS, range1, "orderRequest",
                TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload("orderRequest", "NameExpr"));

        FactGraphAccumulator acc = new FactGraphAccumulator(new FactGraphTraversalBudget(100, 100, 1000));
        acc.getOrAdd(node1);
        acc.getOrAdd(node2);

        assertThat(acc.nodes()).hasSize(1);
    }

    @Test
    @DisplayName("2. 서로 다른 role('LEFT', 'RIGHT', 'RECEIVER')으로 참조되더라도 동일 AST 표현식은 노드를 재사용하고 엣지만 구분됨")
    void differentRolesSameAstShareSingleNode() {
        String nodeId = ids.generate(FactNodeType.FIELD_ACCESS, owner, range1, "field-access");
        FactNode node = new FactNode(nodeId, FactNodeType.FIELD_ACCESS, range1, "orderRequest",
                TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload("orderRequest", "NameExpr"));

        FactNode parentNode = new FactNode("parent-1", FactNodeType.CONDITION, range2, "orderRequest != null",
                TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("BinaryExpr", "!="));

        FactGraphAccumulator acc = new FactGraphAccumulator(new FactGraphTraversalBudget(100, 100, 1000));
        acc.addNode(parentNode);

        // Referencing the same node under different roles & ordinals
        FactEdge edgeLeft = new FactEdge("edge-1", parentNode.id(), node.id(), FactEdgeType.OPERAND_OF, 0, "LEFT");
        FactEdge edgeRight = new FactEdge("edge-2", parentNode.id(), node.id(), FactEdgeType.OPERAND_OF, 1, "RIGHT");

        acc.addRelation(parentNode, node, edgeLeft);
        acc.addRelation(parentNode, node, edgeRight);

        assertThat(acc.nodes()).extracting(FactNode::id).containsOnly("parent-1", nodeId);
        assertThat(acc.nodes()).hasSize(2);
        assertThat(acc.edges()).hasSize(2);
        assertThat(acc.edges()).extracting(FactEdge::role).containsExactly("LEFT", "RIGHT");
    }

    @Test
    @DisplayName("3. 동일한 sourceRange를 공유하더라도 NodeType이 다르면 별도의 노드로 유지됨")
    void differentNodeTypeSameRangeKeepsSeparateNodes() {
        String fieldAccessId = ids.generate(FactNodeType.FIELD_ACCESS, owner, range1, "field-access");
        String enumConstantId = ids.generate(FactNodeType.ENUM_CONSTANT, owner, range1, "enum-constant");

        assertThat(fieldAccessId).isNotEqualTo(enumConstantId);

        FactNode fieldNode = new FactNode(fieldAccessId, FactNodeType.FIELD_ACCESS, range1, "STATUS",
                TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload("STATUS", "NameExpr"));
        FactNode enumNode = new FactNode(enumConstantId, FactNodeType.ENUM_CONSTANT, range1, "STATUS",
                TypeResolution.resolvedType("com.example.Status"), new FactNodePayload.EnumConstantPayload("com.example.Status", "STATUS"));

        FactGraphAccumulator acc = new FactGraphAccumulator(new FactGraphTraversalBudget(100, 100, 1000));
        acc.getOrAdd(fieldNode);
        acc.getOrAdd(enumNode);

        assertThat(acc.nodes()).hasSize(2);
    }

    @Test
    @DisplayName("4. 동일한 NodeType이라도 sourceRange가 다르면 별도의 노드로 유지됨")
    void differentSourceRangeSameNodeTypeKeepsSeparateNodes() {
        String id1 = ids.generate(FactNodeType.FIELD_ACCESS, owner, range1, "field-access");
        String id2 = ids.generate(FactNodeType.FIELD_ACCESS, owner, range2, "field-access");

        assertThat(id1).isNotEqualTo(id2);

        FactNode node1 = new FactNode(id1, FactNodeType.FIELD_ACCESS, range1, "orderRequest",
                TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload("orderRequest", "NameExpr"));
        FactNode node2 = new FactNode(id2, FactNodeType.FIELD_ACCESS, range2, "orderRequest",
                TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload("orderRequest", "NameExpr"));

        FactGraphAccumulator acc = new FactGraphAccumulator(new FactGraphTraversalBudget(100, 100, 1000));
        acc.getOrAdd(node1);
        acc.getOrAdd(node2);

        assertThat(acc.nodes()).hasSize(2);
    }

    @Test
    @DisplayName("5. FactGraphAccumulator.getOrAdd()는 캐시된 기존 노드를 반환하고 중복 노드를 추가하지 않음")
    void accumulatorGetOrAddReturnsCachedNode() {
        String id = ids.generate(FactNodeType.LITERAL, owner, range1, "literal");
        FactNode original = new FactNode(id, FactNodeType.LITERAL, range1, "0",
                TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload("0", "IntegerLiteralExpr"));

        FactGraphAccumulator acc = new FactGraphAccumulator(new FactGraphTraversalBudget(100, 100, 1000));
        FactNode first = acc.getOrAdd(original);

        FactNode duplicateCandidate = new FactNode(id, FactNodeType.LITERAL, range1, "0",
                TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload("0", "IntegerLiteralExpr"));
        FactNode second = acc.getOrAdd(duplicateCandidate);

        assertThat(first).isSameAs(original);
        assertThat(second).isSameAs(original);
        assertThat(acc.nodes()).hasSize(1);
    }

    @Test
    @DisplayName("6. 동일 노드에 대해 TypeResolution 상태 병합 시 RESOLVED 상태가 미해결(UNRESOLVED) 상태보다 우선함")
    void typeResolutionConflictMergesResolvedWins() {
        String id = ids.generate(FactNodeType.FIELD_ACCESS, owner, range1, "field-access");

        FactNode unresolvedNode = new FactNode(id, FactNodeType.FIELD_ACCESS, range1, "userId",
                TypeResolution.unresolved("DECLARATION_NOT_RESOLVED"),
                new FactNodePayload.FieldAccessPayload("userId", "NameExpr"));

        FactNode resolvedNode = new FactNode(id, FactNodeType.FIELD_ACCESS, range1, "userId",
                TypeResolution.resolvedType("java.lang.String"),
                new FactNodePayload.FieldAccessPayload("userId", "NameExpr"));

        FactGraphAccumulator acc = new FactGraphAccumulator(new FactGraphTraversalBudget(100, 100, 1000));

        // 1. Add unresolved first
        acc.getOrAdd(unresolvedNode);
        assertThat(acc.findById(id).typeResolution().status()).isEqualTo(TypeResolutionStatus.UNRESOLVED);

        // 2. Add resolved second -> should update/override
        acc.getOrAdd(resolvedNode);
        assertThat(acc.findById(id).typeResolution().status()).isEqualTo(TypeResolutionStatus.RESOLVED);
        assertThat(acc.findById(id).typeResolution().qualifiedType()).isEqualTo("java.lang.String");
        assertThat(acc.nodes()).hasSize(1);
    }
}
