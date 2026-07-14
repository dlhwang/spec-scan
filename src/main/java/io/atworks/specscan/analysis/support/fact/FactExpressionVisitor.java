package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import io.atworks.specscan.analysis.domain.fact.*;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

final class FactExpressionVisitor {
    private final DeterministicFactNodeIdGenerator ids;
    FactExpressionVisitor(DeterministicFactNodeIdGenerator ids) { this.ids = ids; }

    void visit(Expression expression, FactNode parent, String owner, Path workspace, FactGraphAccumulator acc, Function<MethodCallExpr, TypeResolution> resolver) {
        if (expression instanceof EnclosedExpr enclosed) { visit(enclosed.getInner(), parent, owner, workspace, acc, resolver); return; }
        if (expression instanceof UnaryExpr unary) { visit(unary.getExpression(), parent, owner, workspace, acc, resolver); return; }
        if (expression instanceof BinaryExpr binary) {
            addOperand(binary.getLeft(), parent, 0, "LEFT", owner, workspace, acc, resolver);
            addOperand(binary.getRight(), parent, 1, "RIGHT", owner, workspace, acc, resolver);
            return;
        }
        if (expression instanceof MethodCallExpr call) {
            FactNode callNode = callNode(call, owner, workspace, resolver.apply(call));
            relate(parent, callNode, FactEdgeType.OPERAND_OF, 0, "CALL", acc);
            visitCallChildren(call, callNode, owner, workspace, acc, resolver);
        }
    }

    FactNode callNode(MethodCallExpr call, String owner, Path workspace, TypeResolution resolution) {
        SourceRange range = range(call, workspace);
        return new FactNode(ids.generate(FactNodeType.METHOD_CALL, owner, range, "call"), FactNodeType.METHOD_CALL, range, call.toString(), resolution,
            new FactNodePayload.MethodCallPayload(call.getNameAsString(), call.getArguments().size(), false));
    }

    void visitAssigned(Expression expression, FactNode local, String owner, Path workspace, FactGraphAccumulator acc, Function<MethodCallExpr, TypeResolution> resolver) {
        FactNode node = expressionNode(expression, owner, workspace, "ASSIGNED_VALUE", resolver);
        if (node != null) {
            relate(local, node, FactEdgeType.ASSIGNED_FROM, -1, "INITIALIZER", acc);
            if (expression instanceof MethodCallExpr call) visitCallChildren(call, node, owner, workspace, acc, resolver);
        } else {
            visit(expression, local, owner, workspace, acc, resolver);
        }
    }

    private void addOperand(Expression expression, FactNode parent, int ordinal, String role, String owner, Path workspace, FactGraphAccumulator acc, Function<MethodCallExpr, TypeResolution> resolver) {
        FactNode node = expressionNode(expression, owner, workspace, role, resolver);
        if (node != null) {
            relate(parent, node, FactEdgeType.OPERAND_OF, ordinal, role, acc);
            relateDeclaredOrigin(expression, node, acc);
        }
        if (expression instanceof MethodCallExpr call && node != null) {
            visitCallChildren(call, node, owner, workspace, acc, resolver);
        } else {
            visit(expression, node == null ? parent : node, owner, workspace, acc, resolver);
        }
    }

    private void visitCallChildren(MethodCallExpr call, FactNode callNode, String owner, Path workspace, FactGraphAccumulator acc, Function<MethodCallExpr, TypeResolution> resolver) {
        call.getScope().ifPresent(scope -> addOperand(scope, callNode, -1, "RECEIVER", owner, workspace, acc, resolver));
        for (int i = 0; i < call.getArguments().size(); i++) addOperand(call.getArgument(i), callNode, i, "ARGUMENT", owner, workspace, acc, resolver);
    }

    private FactNode expressionNode(Expression expression, String owner, Path workspace, String role, Function<MethodCallExpr, TypeResolution> resolver) {
        if (expression instanceof MethodCallExpr call) return callNode(call, owner, workspace, resolver.apply(call));
        SourceRange range = range(expression, workspace);
        if (expression instanceof BinaryExpr binary) return new FactNode(
            ids.generate(FactNodeType.CONDITION, owner, range, "nested:" + role), FactNodeType.CONDITION,
            range, binary.toString(), TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload(BinaryExpr.class.getSimpleName(), binary.getOperator().asString()));
        if (expression instanceof FieldAccessExpr field) {
            try {
                var declaration = field.resolve();
                if (declaration.isEnumConstant()) return new FactNode(ids.generate(FactNodeType.ENUM_CONSTANT, owner, range, role), FactNodeType.ENUM_CONSTANT, range, field.toString(), TypeResolution.resolvedType(declaration.getType().describe()), new FactNodePayload.EnumConstantPayload(declaration.getType().describe(), field.getNameAsString()));
            } catch (RuntimeException ignored) { }
            return new FactNode(ids.generate(FactNodeType.FIELD_ACCESS, owner, range, role), FactNodeType.FIELD_ACCESS, range, field.toString(), TypeResolution.notApplicable(), new FactNodePayload.FieldAccessPayload(field.getNameAsString(), field.getScope().getClass().getSimpleName()));
        }
        if (expression instanceof NameExpr name) return new FactNode(ids.generate(FactNodeType.FIELD_ACCESS, owner, range, role), FactNodeType.FIELD_ACCESS, range, name.toString(), TypeResolution.unresolved("DECLARATION_NOT_RESOLVED"), new FactNodePayload.FieldAccessPayload(name.getNameAsString(), "NameExpr"));
        if (expression instanceof NullLiteralExpr) return new FactNode(ids.generate(FactNodeType.NULL_LITERAL, owner, range, role), FactNodeType.NULL_LITERAL, range, expression.toString(), TypeResolution.notApplicable(), new FactNodePayload.NullLiteralPayload());
        if (expression instanceof LiteralExpr literal) return new FactNode(ids.generate(FactNodeType.LITERAL, owner, range, role), FactNodeType.LITERAL, range, literal.toString(), TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload(literal.toString(), literal.getClass().getSimpleName()));
        return null;
    }

    private void relateDeclaredOrigin(Expression expression, FactNode reference, FactGraphAccumulator acc) {
        String rootName = rootName(expression);
        if (rootName == null) return;
        for (FactNode declaration : acc.nodes()) {
            String declaredName = null;
            if (declaration.payload() instanceof FactNodePayload.ParameterPayload parameter) declaredName = parameter.name();
            if (declaration.payload() instanceof FactNodePayload.LocalVariablePayload local) declaredName = local.name();
            if (rootName.equals(declaredName)) {
                relate(reference, declaration, FactEdgeType.READS, -1, "DECLARATION", acc);
                return;
            }
        }
    }

    private String rootName(Expression expression) {
        Expression current = expression;
        while (current instanceof FieldAccessExpr field) current = field.getScope();
        return current instanceof NameExpr name ? name.getNameAsString() : null;
    }

    private void relate(FactNode source, FactNode target, FactEdgeType type, int ordinal, String role, FactGraphAccumulator acc) {
        String id = ids.edgeId(source.id(), target.id(), type.name(), ordinal, role);
        acc.addRelation(source, target, new FactEdge(id, source.id(), target.id(), type, ordinal, role));
    }

    static SourceRange range(Node node, Path workspace) {
        String path = node.findCompilationUnit().flatMap(c -> c.getStorage()).map(s -> workspace.relativize(s.getPath()).toString().replace('\\', '/')).orElse("unknown.java");
        var begin = node.getBegin().orElse(new com.github.javaparser.Position(1, 1));
        var end = node.getEnd().orElse(begin);
        return new SourceRange(path, begin.line, begin.column, end.line, end.column);
    }
}
