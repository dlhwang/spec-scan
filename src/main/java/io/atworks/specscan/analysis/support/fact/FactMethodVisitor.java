package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.TypeResolver;
import java.nio.file.Path;

final class FactMethodVisitor {
    private final DeterministicFactNodeIdGenerator ids;
    private final FactExpressionVisitor expressions;
    FactMethodVisitor(DeterministicFactNodeIdGenerator ids) { this.ids = ids; this.expressions = new FactExpressionVisitor(ids); }

    void visit(CallableDeclaration<?> method, FactNode methodNode, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        registerInstanceFields(method, owner, workspace, acc);
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
        for (LambdaExpr lambda : method.findAll(LambdaExpr.class)) {
            FactNode lambdaNode = expressions.lambdaNode(lambda, owner, workspace);
            relation(methodNode, lambdaNode, FactEdgeType.EXECUTES, -1, "LAMBDA", acc);
            for (int i = 0; i < lambda.getParameters().size(); i++) {
                var parameter = lambda.getParameter(i);
                SourceRange parameterRange = FactExpressionVisitor.range(parameter, workspace);
                FactNode parameterNode = new FactNode(ids.generate(FactNodeType.PARAMETER, owner,
                    parameterRange, "lambda-parameter:" + i), FactNodeType.PARAMETER, parameterRange,
                    parameter.toString(), TypeResolution.unresolved("DECLARED_ONLY"),
                    new FactNodePayload.ParameterPayload(parameter.getNameAsString(), i,
                        parameter.getTypeAsString()));
                relation(lambdaNode, parameterNode, FactEdgeType.ORIGINATES_FROM, i,
                    "LAMBDA_PARAMETER", acc);
                lambda.findAncestor(MethodCallExpr.class)
                    .filter(call -> call.getArguments().stream().anyMatch(argument -> argument == lambda))
                    .flatMap(MethodCallExpr::getScope)
                    .ifPresent(scope -> expressions.relateCollectionOrigin(collectionSource(scope), parameterNode,
                        owner, workspace, acc, call -> resolve(call, resolver)));
            }
        }
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            expressions.relateBuilderField(call, owner, workspace, acc, nested -> resolve(nested, resolver));
        }
        for (IfStmt statement : method.findAll(IfStmt.class)) {
            SourceRange r = FactExpressionVisitor.range(statement.getCondition(), workspace);
            FactNode condition = new FactNode(ids.generate(FactNodeType.CONDITION, owner, r, "if-condition"), FactNodeType.CONDITION, r, statement.getCondition().toString(), TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload(statement.getCondition().getClass().getSimpleName(), operator(statement.getCondition())));
            relation(methodNode, condition, FactEdgeType.CONTROLS, -1, "IF", acc);
            expressions.visit(statement.getCondition(), condition, owner, workspace, acc, call -> resolve(call, resolver));
            directOutcomes(statement.getThenStmt(), condition, FactEdgeType.THEN_OUTCOME, owner, workspace, acc);
            statement.getElseStmt().ifPresent(branch -> directOutcomes(branch, condition, FactEdgeType.ELSE_OUTCOME, owner, workspace, acc));
            if (statement.getElseStmt().isEmpty() && returnsDirectly(statement.getThenStmt()))
                trailingStatement(statement).ifPresent(next -> directOutcomes(next, condition,
                    FactEdgeType.ELSE_OUTCOME, owner, workspace, acc));
        }
        for (SwitchStmt statement : method.findAll(SwitchStmt.class)) {
            switchEntries(statement.getSelector(), statement.getEntries(), methodNode, owner, workspace, acc, resolver);
        }
        for (SwitchExpr expression : method.findAll(SwitchExpr.class)) {
            switchEntries(expression.getSelector(), expression.getEntries(), methodNode, owner, workspace, acc, resolver);
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
    private void registerInstanceFields(CallableDeclaration<?> method, String owner, Path workspace,
                                      FactGraphAccumulator acc) {
        method.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(type -> {
                boolean lombokConstructor = type.isAnnotationPresent("Value")
                    || type.isAnnotationPresent("AllArgsConstructor");
                String callable = "." + method.getNameAsString() + "(";
                int boundary = owner.indexOf(callable);
                String typeOwner = boundary > 0 ? owner.substring(0, boundary) : owner;
                type.getFields().stream().filter(field -> !field.isStatic())
                    .flatMap(field -> field.getVariables().stream()).forEach(field -> {
                        SourceRange range = FactExpressionVisitor.range(field, workspace);
                        FactNode valueField = new FactNode(ids.generate(FactNodeType.VALUE_FIELD, typeOwner, range,
                            "lombok-field:" + field.getNameAsString()), FactNodeType.VALUE_FIELD, range,
                            field.getNameAsString(), TypeResolution.unresolved("DECLARED_FIELD"),
                            new FactNodePayload.FieldAccessPayload(field.getNameAsString(),
                                (lombokConstructor ? "LOMBOK_FIELD:" : "INSTANCE_FIELD:") + typeOwner));
                        acc.addNode(valueField);
                    });
            });
    }
    private boolean returnsDirectly(Statement statement) {
        if (statement instanceof ReturnStmt) return true;
        return statement instanceof BlockStmt block && block.getStatements().stream().anyMatch(ReturnStmt.class::isInstance);
    }
    private java.util.Optional<Statement> trailingStatement(IfStmt statement) {
        return statement.getParentNode().filter(BlockStmt.class::isInstance).map(BlockStmt.class::cast)
            .flatMap(block -> {
                int index = block.getStatements().indexOf(statement);
                return index >= 0 && index + 1 < block.getStatements().size()
                    ? java.util.Optional.of(block.getStatement(index + 1)) : java.util.Optional.empty();
            });
    }
    private Expression collectionSource(Expression scope) {
        Expression current = scope;
        while (current instanceof MethodCallExpr call && (call.getNameAsString().equals("stream")
                || call.getNameAsString().equals("parallelStream"))) {
            current = call.getScope().orElse(current);
        }
        return current;
    }
    private void switchEntries(Expression selector, java.util.List<SwitchEntry> entries, FactNode methodNode,
                               String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        for (SwitchEntry entry : entries) {
            if (entry.getLabels().isEmpty()) continue;
            for (Expression label : entry.getLabels()) {
                SourceRange range = FactExpressionVisitor.range(entry, workspace);
                FactNode condition = new FactNode(ids.generate(FactNodeType.CONDITION, owner, range,
                    "switch-case:" + label), FactNodeType.CONDITION, range,
                    selector + " == " + label, TypeResolution.notApplicable(),
                    new FactNodePayload.ConditionPayload("SwitchEntry", "=="));
                relation(methodNode, condition, FactEdgeType.CONTROLS, -1, "SWITCH_CASE", acc);
                expressions.visit(selector, condition, owner, workspace, acc, call -> resolve(call, resolver));
                expressions.visit(label, condition, owner, workspace, acc, call -> resolve(call, resolver));
                for (Statement child : entry.getStatements()) {
                    if (child instanceof ThrowStmt thrown) {
                        outcome(thrown, condition, FactNodeType.THROW, FactEdgeType.THEN_OUTCOME,
                            owner, workspace, acc);
                    } else if (child instanceof ExpressionStmt result) {
                        SourceRange resultRange = FactExpressionVisitor.range(result, workspace);
                        FactNode returned = new FactNode(ids.generate(FactNodeType.RETURN, owner, resultRange,
                            "switch-result"), FactNodeType.RETURN, resultRange, result.toString(),
                            TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("RETURN",
                            "SwitchExpressionResult"));
                        relation(condition, returned, FactEdgeType.THEN_OUTCOME, -1, "SWITCH_RESULT", acc);
                        expressions.visit(result.getExpression(), returned, owner, workspace, acc,
                            call -> resolve(call, resolver));
                    }
                }
            }
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
