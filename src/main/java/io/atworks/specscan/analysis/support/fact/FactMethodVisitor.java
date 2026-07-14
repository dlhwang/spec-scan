package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.TypeResolver;
import java.nio.file.Path;

final class FactMethodVisitor {
    private final DeterministicFactNodeIdGenerator ids;
    private final FactExpressionVisitor expressions;
    FactMethodVisitor(DeterministicFactNodeIdGenerator ids) { this.ids = ids; this.expressions = new FactExpressionVisitor(ids); }

    void visit(MethodDeclaration method, FactNode methodNode, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        for (int i = 0; i < method.getParameters().size(); i++) {
            var p = method.getParameter(i); SourceRange r = FactExpressionVisitor.range(p, workspace);
            FactNode n = new FactNode(ids.generate(FactNodeType.PARAMETER, owner, r, "parameter:" + i), FactNodeType.PARAMETER, r, p.toString(), TypeResolution.unresolved("DECLARED_ONLY"), new FactNodePayload.ParameterPayload(p.getNameAsString(), i, p.getTypeAsString()));
            relation(methodNode, n, FactEdgeType.ORIGINATES_FROM, i, "PARAMETER", acc);
        }
        for (VariableDeclarator variable : method.findAll(VariableDeclarator.class)) {
            SourceRange r = FactExpressionVisitor.range(variable, workspace);
            FactNode local = new FactNode(ids.generate(FactNodeType.LOCAL_VARIABLE, owner, r, "local:" + variable.getNameAsString()), FactNodeType.LOCAL_VARIABLE, r,
                variable.toString(), TypeResolution.unresolved("DECLARED_ONLY"), new FactNodePayload.LocalVariablePayload(variable.getNameAsString(), variable.getTypeAsString()));
            relation(methodNode, local, FactEdgeType.ORIGINATES_FROM, -1, "LOCAL_VARIABLE", acc);
            variable.getInitializer().ifPresent(initializer -> expressions.visitAssigned(initializer, local, owner, workspace, acc, call -> resolve(call, resolver)));
        }
        for (IfStmt statement : method.findAll(IfStmt.class)) {
            SourceRange r = FactExpressionVisitor.range(statement.getCondition(), workspace);
            FactNode condition = new FactNode(ids.generate(FactNodeType.CONDITION, owner, r, "if-condition"), FactNodeType.CONDITION, r, statement.getCondition().toString(), TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload(statement.getCondition().getClass().getSimpleName(), operator(statement.getCondition())));
            relation(methodNode, condition, FactEdgeType.CONTROLS, -1, "IF", acc);
            expressions.visit(statement.getCondition(), condition, owner, workspace, acc, call -> resolve(call, resolver));
            directOutcomes(statement.getThenStmt(), condition, FactEdgeType.THEN_OUTCOME, owner, workspace, acc);
            statement.getElseStmt().ifPresent(branch -> directOutcomes(branch, condition, FactEdgeType.ELSE_OUTCOME, owner, workspace, acc));
        }
        for (ReturnStmt statement : method.findAll(ReturnStmt.class)) {
            SourceRange range = FactExpressionVisitor.range(statement, workspace);
            FactNode returned = new FactNode(ids.generate(FactNodeType.RETURN, owner, range, "method-return"),
                FactNodeType.RETURN, range, statement.toString(), TypeResolution.notApplicable(),
                new FactNodePayload.OutcomePayload("RETURN", statement.getClass().getSimpleName()));
            relation(methodNode, returned, FactEdgeType.RETURNS, -1, "RETURN", acc);
            statement.getExpression().ifPresent(expression -> expressions.visit(expression, returned, owner,
                workspace, acc, call -> resolve(call, resolver)));
        }
    }
    private void directOutcomes(Statement branch, FactNode condition, FactEdgeType edge, String owner, Path workspace, FactGraphAccumulator acc) {
        if (branch instanceof ThrowStmt thrown) outcome(thrown, condition, FactNodeType.THROW, edge, owner, workspace, acc);
        else if (branch instanceof ReturnStmt returned) outcome(returned, condition, FactNodeType.RETURN, edge, owner, workspace, acc);
        else if (branch instanceof BlockStmt block) for (Statement statement : block.getStatements()) {
            if (statement instanceof ThrowStmt thrown) outcome(thrown, condition, FactNodeType.THROW, edge, owner, workspace, acc);
            else if (statement instanceof ReturnStmt returned) outcome(returned, condition, FactNodeType.RETURN, edge, owner, workspace, acc);
        }
    }
    private TypeResolution resolve(MethodCallExpr call, TypeResolver resolver) { return resolver.resolveMethodCall(call).map(r -> TypeResolution.resolvedSignature(r.getQualifiedSignature())).orElseGet(() -> TypeResolution.unresolved("TYPE_RESOLUTION_FAILED")); }
    private void outcome(com.github.javaparser.ast.Node node, FactNode condition, FactNodeType type, FactEdgeType edge, String owner, Path workspace, FactGraphAccumulator acc) { SourceRange r = FactExpressionVisitor.range(node, workspace); FactNode n = new FactNode(ids.generate(type, owner, r, edge.name()), type, r, node.toString(), TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload(type.name(), node.getClass().getSimpleName())); relation(condition, n, edge, -1, type.name(), acc); }
    private void relation(FactNode a, FactNode b, FactEdgeType type, int ordinal, String role, FactGraphAccumulator acc) { acc.addRelation(a, b, new FactEdge(ids.edgeId(a.id(), b.id(), type.name(), ordinal, role), a.id(), b.id(), type, ordinal, role)); }
    private String operator(com.github.javaparser.ast.expr.Expression expression) { if (expression instanceof com.github.javaparser.ast.expr.BinaryExpr b) return b.getOperator().asString(); if (expression instanceof com.github.javaparser.ast.expr.UnaryExpr u) return u.getOperator().asString(); return expression.getClass().getSimpleName(); }
}
