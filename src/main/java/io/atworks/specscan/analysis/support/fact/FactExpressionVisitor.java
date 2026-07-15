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
        if (expression instanceof UnaryExpr unary) {
            Expression operand = unary.getExpression() instanceof EnclosedExpr enclosed
                ? enclosed.getInner() : unary.getExpression();
            addOperand(operand, parent, 0, "UNARY_OPERAND", owner, workspace, acc, resolver);
            return;
        }
        if (expression instanceof BinaryExpr binary) {
            if (parent.type() != FactNodeType.CONDITION) {
                FactNode nested = expressionNode(binary, owner, workspace, "BINARY_VALUE", resolver);
                relate(parent, nested, FactEdgeType.OPERAND_OF, 0, "BINARY_VALUE", acc);
                visit(binary, nested, owner, workspace, acc, resolver);
                return;
            }
            addOperand(binary.getLeft(), parent, 0, "LEFT", owner, workspace, acc, resolver);
            addOperand(binary.getRight(), parent, 1, "RIGHT", owner, workspace, acc, resolver);
            return;
        }
        if (expression instanceof MethodCallExpr call) {
            FactNode callNode = callNode(call, owner, workspace, resolver.apply(call));
            relate(parent, callNode, FactEdgeType.OPERAND_OF, 0, "CALL", acc);
            visitCallChildren(call, callNode, owner, workspace, acc, resolver);
            return;
        }
        if (expression instanceof ObjectCreationExpr creation) {
            SourceRange range = range(creation, workspace);
            FactNode creationNode = new FactNode(ids.generate(FactNodeType.OBJECT_CREATION, owner, range, "new"),
                FactNodeType.OBJECT_CREATION, range, creation.toString(), TypeResolution.notApplicable(),
                new FactNodePayload.ObjectCreationPayload(creation.getTypeAsString(), creation.getArguments().size()));
            relate(parent, creationNode, FactEdgeType.OPERAND_OF, 0, "RETURN_BODY", acc);
            visitObjectCreationArguments(creation, creationNode, owner, workspace, acc, resolver);
            return;
        }
        FactNode node = expressionNode(expression, owner, workspace, "VALUE", resolver);
        if (node != null) {
            relate(parent, node, FactEdgeType.OPERAND_OF, 0, "VALUE", acc);
            relateDeclaredOrigin(expression, node, acc);
        }
    }

    FactNode callNode(MethodCallExpr call, String owner, Path workspace, TypeResolution resolution) {
        SourceRange range = range(call, workspace);
        return new FactNode(ids.generate(FactNodeType.METHOD_CALL, owner, range, "call"), FactNodeType.METHOD_CALL, range, call.toString(), resolution,
            new FactNodePayload.MethodCallPayload(call.getNameAsString(), call.getArguments().size(), false));
    }

    FactNode lambdaNode(LambdaExpr lambda, String owner, Path workspace) {
        SourceRange range = range(lambda, workspace);
        return new FactNode(ids.generate(FactNodeType.LAMBDA, owner, range, "lambda"), FactNodeType.LAMBDA,
            range, lambda.toString(), TypeResolution.notApplicable(),
            new FactNodePayload.LambdaPayload(lambda.getParameters().size(), lambda.getExpressionBody().isPresent()));
    }

    FactNode methodReferenceNode(MethodReferenceExpr reference, String owner, Path workspace,
                                 TypeResolution resolution) {
        SourceRange range = range(reference, workspace);
        return new FactNode(ids.generate(FactNodeType.METHOD_REFERENCE, owner, range, "method-reference"),
            FactNodeType.METHOD_REFERENCE, range, reference.toString(), resolution,
            new FactNodePayload.MethodReferencePayload(reference.getIdentifier()));
    }

    void visitObjectCreationArguments(ObjectCreationExpr creation, FactNode creationNode, String owner,
                                      Path workspace, FactGraphAccumulator acc,
                                      Function<MethodCallExpr, TypeResolution> resolver) {
        for (int i = 0; i < creation.getArguments().size(); i++) {
            addOperand(creation.getArgument(i), creationNode, i, "ARGUMENT", owner, workspace, acc, resolver);
        }
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

    void visitCallArguments(MethodCallExpr call, FactNode callNode, String owner, Path workspace,
                            FactGraphAccumulator acc, Function<MethodCallExpr, TypeResolution> resolver) {
        visitCallChildren(call, callNode, owner, workspace, acc, resolver);
    }

    void relateCollectionOrigin(Expression source, FactNode elementParameter, String owner, Path workspace,
                                FactGraphAccumulator acc, Function<MethodCallExpr, TypeResolution> resolver) {
        FactNode sourceNode = expressionNode(source, owner, workspace, "COLLECTION_SOURCE", resolver);
        if (sourceNode == null) return;
        relate(elementParameter, sourceNode, FactEdgeType.ORIGINATES_FROM, -1, "COLLECTION_ELEMENT", acc);
        relateDeclaredOrigin(source, sourceNode, acc);
        if (source instanceof MethodCallExpr call) visitCallChildren(call, sourceNode, owner, workspace, acc, resolver);
    }

    void relateBuilderField(MethodCallExpr setter, String owner, Path workspace, FactGraphAccumulator acc,
                            Function<MethodCallExpr, TypeResolution> resolver) {
        if (setter.getArguments().size() != 1 || setter.getScope().isEmpty()
                || !setter.getScope().get().toString().contains("builder()")) return;
        SourceRange range = range(setter, workspace);
        FactNode field = new FactNode(ids.generate(FactNodeType.VALUE_FIELD, owner, range,
            "builder-field:" + setter.getNameAsString()), FactNodeType.VALUE_FIELD, range,
            setter.getNameAsString(), TypeResolution.notApplicable(),
            new FactNodePayload.FieldAccessPayload(setter.getNameAsString(), "BUILDER_SETTER:" + owner));
        FactNode value = expressionNode(setter.getArgument(0), owner, workspace, "BUILDER_VALUE", resolver);
        if (value == null) return;
        relate(value, field, FactEdgeType.VALUE_FLOWS_TO, 0, "BUILDER_SETTER", acc);
        relateDeclaredOrigin(setter.getArgument(0), value, acc);
        if (setter.getArgument(0) instanceof MethodCallExpr call)
            visitCallChildren(call, value, owner, workspace, acc, resolver);
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
        FactNode best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (FactNode declaration : acc.nodes()) {
            String declaredName = null;
            if (declaration.payload() instanceof FactNodePayload.ParameterPayload parameter) declaredName = parameter.name();
            if (declaration.payload() instanceof FactNodePayload.LocalVariablePayload local) declaredName = local.name();
            if (declaration.type() == FactNodeType.VALUE_FIELD
                    && declaration.payload() instanceof FactNodePayload.FieldAccessPayload field
                    && (field.rootExpressionKind().startsWith("LOMBOK_FIELD:")
                    || field.rootExpressionKind().startsWith("INSTANCE_FIELD:"))) declaredName = field.fieldName();
            if (!rootName.equals(declaredName)
                    || !declaration.sourceRange().relativePath().equals(reference.sourceRange().relativePath())
                    || declaration.sourceRange().startLine() > reference.sourceRange().startLine()) continue;
            int distance = reference.sourceRange().startLine() - declaration.sourceRange().startLine();
            if (distance < bestDistance || distance == bestDistance
                    && declaration.sourceRange().startColumn() > (best == null ? -1 : best.sourceRange().startColumn())) {
                best = declaration; bestDistance = distance;
            }
        }
        if (best != null) relate(reference, best, FactEdgeType.READS, -1, "DECLARATION", acc);
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
