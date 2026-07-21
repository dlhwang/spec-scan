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
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.TypeResolver;
import java.nio.file.Path;

final class FactMethodVisitor {
    private final DeterministicFactNodeIdGenerator ids;
    private final FactExpressionVisitor expressions;
    FactMethodVisitor(DeterministicFactNodeIdGenerator ids) { this.ids = ids; this.expressions = new FactExpressionVisitor(ids); }

    void visit(CallableDeclaration<?> method, FactNode methodNode, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        registerInstanceFields(method, owner, workspace, acc);
        registerMethodAnnotations(method, methodNode, owner, workspace, acc);
        for (int i = 0; i < method.getParameters().size(); i++) {
            var p = method.getParameter(i); SourceRange r = FactExpressionVisitor.range(p, workspace);
            String bindingLocation = bindingLocation(p.getAnnotations());
            String bindingName = bindingName(p.getAnnotations(), p.getNameAsString(), bindingLocation);
            boolean required = bindingRequired(p.getAnnotations(), bindingLocation);
            String qualifiedType = resolveParameterType(p);
            TypeResolution parameterType = qualifiedType == null
                ? TypeResolution.unresolved("PARAMETER_TYPE_RESOLUTION_FAILED")
                : TypeResolution.resolvedType(qualifiedType);
            FactNode n = new FactNode(ids.generate(FactNodeType.PARAMETER, owner, r,
                "parameter:" + i), FactNodeType.PARAMETER, r, p.toString(), parameterType,
                new FactNodePayload.ParameterPayload(p.getNameAsString(), i,
                    p.getTypeAsString(), bindingLocation, bindingName, required));
            relation(methodNode, n, FactEdgeType.ORIGINATES_FROM, i, "PARAMETER", acc);
            FactNode typeNode = parameterTypeNode(p, owner, r, i, qualifiedType);
            relation(n, typeNode, FactEdgeType.HAS_TYPE, 0, "DECLARED_TYPE", acc);
            for (int annotationIndex = 0; annotationIndex < p.getAnnotations().size(); annotationIndex++) {
                AnnotationExpr annotation = p.getAnnotation(annotationIndex);
                SourceRange annotationRange = FactExpressionVisitor.range(annotation, workspace);
                FactNode annotationNode = new FactNode(ids.generate(FactNodeType.ANNOTATION, owner,
                    annotationRange, "parameter-annotation:" + i + ":" + annotationIndex),
                    FactNodeType.ANNOTATION, annotationRange, annotation.toString(),
                    TypeResolution.notApplicable(), new FactNodePayload.AnnotationPayload(
                        annotation.getNameAsString(), annotationAttributes(annotation)));
                relation(n, annotationNode, FactEdgeType.HAS_ANNOTATION, annotationIndex,
                    "PARAMETER_ANNOTATION", acc);
            }
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
            for (ReturnStmt statement : lambda.findAll(ReturnStmt.class)) {
                if (findExecutableScope(statement) == lambda) {
                    FactNode returned = getOrCreateReturnNode(statement, owner, workspace, acc, resolver);
                    relation(lambdaNode, returned, FactEdgeType.RETURNS, -1, "RETURN", acc);
                }
            }
            for (ThrowStmt statement : lambda.findAll(ThrowStmt.class)) {
                if (findExecutableScope(statement) == lambda) {
                    FactNode thrown = getOrCreateThrowNode(statement, owner, workspace, acc, resolver);
                    relation(lambdaNode, thrown, FactEdgeType.THROWS, -1, "THROW", acc);
                }
            }
        }
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            expressions.relateBuilderField(call, owner, workspace, acc, nested -> resolve(nested, resolver));
            recordResponseFactory(call, methodNode, owner, workspace, acc);
            recordDeclaredExceptions(call, owner, workspace, acc, resolver);
        }
        for (IfStmt statement : method.findAll(IfStmt.class)) {
            SourceRange r = FactExpressionVisitor.range(statement.getCondition(), workspace);
            FactNode condition = new FactNode(ids.generate(FactNodeType.CONDITION, owner, r, "if-condition"), FactNodeType.CONDITION, r, statement.getCondition().toString(), TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload(statement.getCondition().getClass().getSimpleName(), operator(statement.getCondition())));
            relation(methodNode, condition, FactEdgeType.CONTROLS, -1, "IF", acc);
            expressions.visit(statement.getCondition(), condition, owner, workspace, acc, call -> resolve(call, resolver));
            directOutcomes(statement.getThenStmt(), condition, FactEdgeType.THEN_OUTCOME, owner, workspace, acc, resolver);
            statement.getElseStmt().ifPresent(branch -> directOutcomes(branch, condition, FactEdgeType.ELSE_OUTCOME, owner, workspace, acc, resolver));
            if (statement.getElseStmt().isEmpty() && returnsDirectly(statement.getThenStmt()))
                trailingStatement(statement).ifPresent(next -> directOutcomes(next, condition,
                    FactEdgeType.ELSE_OUTCOME, owner, workspace, acc, resolver));
        }
        for (SwitchStmt statement : method.findAll(SwitchStmt.class)) {
            switchEntries(statement.getSelector(), statement.getEntries(), methodNode, owner, workspace, acc, resolver, false);
        }
        for (SwitchExpr expression : method.findAll(SwitchExpr.class)) {
            switchEntries(expression.getSelector(), expression.getEntries(), methodNode, owner, workspace, acc, resolver, true);
        }
        for (ReturnStmt statement : method.findAll(ReturnStmt.class)) {
            if (findExecutableScope(statement) == method) {
                FactNode returned = getOrCreateReturnNode(statement, owner, workspace, acc, resolver);
                relation(methodNode, returned, FactEdgeType.RETURNS, -1, "RETURN", acc);
            }
        }
        for (ThrowStmt statement : method.findAll(ThrowStmt.class)) {
            if (findExecutableScope(statement) == method) {
                FactNode thrown = getOrCreateThrowNode(statement, owner, workspace, acc, resolver);
                relation(methodNode, thrown, FactEdgeType.THROWS, -1, "THROW", acc);
            }
        }
    }

    private void registerMethodAnnotations(CallableDeclaration<?> method, FactNode methodNode,
        String owner, Path workspace, FactGraphAccumulator acc) {
        for (int index = 0; index < method.getAnnotations().size(); index++) {
            AnnotationExpr annotation = method.getAnnotation(index);
            SourceRange range = FactExpressionVisitor.range(annotation, workspace);
            FactNode annotationNode = new FactNode(ids.generate(FactNodeType.ANNOTATION, owner,
                range, "method-annotation:" + index), FactNodeType.ANNOTATION, range,
                annotation.toString(), TypeResolution.notApplicable(),
                new FactNodePayload.AnnotationPayload(annotation.getNameAsString(),
                    annotationAttributes(annotation)));
            relation(methodNode, annotationNode, FactEdgeType.HAS_ANNOTATION, index,
                "METHOD_ANNOTATION", acc);
        }
    }

    private void recordResponseFactory(MethodCallExpr call, FactNode methodNode, String owner,
        Path workspace, FactGraphAccumulator acc) {
        String factoryMethod = call.getNameAsString();
        if (!java.util.Set.of("ok", "noContent", "status").contains(factoryMethod)
            || !call.toString().contains("ResponseEntity")) return;
        Integer statusCode = switch (factoryMethod) {
            case "ok" -> 200;
            case "noContent" -> 204;
            default -> call.getArguments().isEmpty() ? null : statusCode(call.getArgument(0));
        };
        String statusExpression = call.getArguments().isEmpty()
            ? factoryMethod : call.getArgument(0).toString();
        SourceRange range = FactExpressionVisitor.range(call, workspace);
        FactNode factory = new FactNode(ids.generate(FactNodeType.RESPONSE_FACTORY, owner, range,
            "response-factory:" + factoryMethod), FactNodeType.RESPONSE_FACTORY, range,
            call.toString(), TypeResolution.notApplicable(),
            new FactNodePayload.ResponseFactoryPayload(factoryMethod, statusCode,
                statusExpression));
        relation(methodNode, factory, FactEdgeType.EXECUTES, -1, "RESPONSE_FACTORY", acc);
        if (statusCode != null) {
            FactNode status = new FactNode(ids.generate(FactNodeType.HTTP_STATUS, owner, range,
                "response-status:" + statusCode), FactNodeType.HTTP_STATUS, range,
                String.valueOf(statusCode), TypeResolution.notApplicable(),
                new FactNodePayload.HttpStatusPayload(statusCode, statusExpression));
            relation(factory, status, FactEdgeType.MAPS_TO, 0, "HTTP_STATUS", acc);
        }
    }

    private Integer statusCode(Expression expression) {
        if (expression.isIntegerLiteralExpr()) {
            try { return expression.asIntegerLiteralExpr().asInt(); }
            catch (RuntimeException ignored) { return null; }
        }
        String value = expression.toString();
        int separator = value.lastIndexOf('.');
        String name = separator < 0 ? value : value.substring(separator + 1);
        return switch (name) {
            case "OK" -> 200;
            case "CREATED" -> 201;
            case "ACCEPTED" -> 202;
            case "NO_CONTENT" -> 204;
            case "BAD_REQUEST" -> 400;
            case "UNAUTHORIZED" -> 401;
            case "FORBIDDEN" -> 403;
            case "NOT_FOUND" -> 404;
            case "CONFLICT" -> 409;
            case "UNPROCESSABLE_ENTITY" -> 422;
            case "INTERNAL_SERVER_ERROR" -> 500;
            default -> null;
        };
    }

    private void recordDeclaredExceptions(MethodCallExpr call, String owner, Path workspace,
        FactGraphAccumulator acc, TypeResolver resolver) {
        resolver.resolveMethodCall(call).ifPresent(resolved -> {
            FactNode callNode = expressions.callNode(call, owner, workspace,
                TypeResolution.resolvedSignature(resolved.getQualifiedSignature()));
            for (int index = 0; index < resolved.getNumberOfSpecifiedExceptions(); index++) {
                String exceptionType = resolved.getSpecifiedException(index).describe();
                SourceRange range = FactExpressionVisitor.range(call, workspace);
                FactNode exceptionNode = new FactNode(ids.generate(FactNodeType.EXCEPTION, owner,
                    range, "declared-exception:" + index + ":" + exceptionType),
                    FactNodeType.EXCEPTION, range, exceptionType,
                    TypeResolution.resolvedType(exceptionType),
                    new FactNodePayload.ExceptionPayload(exceptionType, false));
                relation(callNode, exceptionNode, FactEdgeType.MAY_THROW, index,
                    "DECLARED_EXCEPTION", acc);
            }
        });
    }

    private FactNode parameterTypeNode(com.github.javaparser.ast.body.Parameter parameter,
        String owner, SourceRange range, int index, String qualifiedType) {
        String declaredType = parameter.getTypeAsString();
        String elementType = parameter.getType().isArrayType()
            ? parameter.getType().asArrayType().getComponentType().asString()
            : parameter.getType().isClassOrInterfaceType()
                ? parameter.getType().asClassOrInterfaceType().getTypeArguments()
                    .filter(arguments -> !arguments.isEmpty())
                    .map(arguments -> arguments.get(0).asString()).orElse(null)
                : null;
        String rawType = parameter.getType().isClassOrInterfaceType()
            ? parameter.getType().asClassOrInterfaceType().getNameAsString() : declaredType;
        boolean collection = parameter.getType().isArrayType()
            || java.util.Set.of("Collection", "Iterable", "List", "Set", "Queue", "Deque")
                .contains(rawType);
        return new FactNode(ids.generate(FactNodeType.TYPE, owner, range,
            "parameter-type:" + index), FactNodeType.TYPE, range, declaredType,
            qualifiedType == null ? TypeResolution.unresolved("PARAMETER_TYPE_RESOLUTION_FAILED")
                : TypeResolution.resolvedType(qualifiedType),
            new FactNodePayload.TypePayload(declaredType, qualifiedType,
                parameter.getType().isPrimitiveType(), collection, elementType));
    }

    private String resolveParameterType(com.github.javaparser.ast.body.Parameter parameter) {
        try {
            return parameter.resolve().getType().describe();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String bindingLocation(com.github.javaparser.ast.NodeList<AnnotationExpr> annotations) {
        for (AnnotationExpr annotation : annotations) {
            String name = annotation.getName().getIdentifier();
            if (name.equals("PathVariable")) return "PATH";
            if (name.equals("RequestParam")) return "QUERY";
            if (name.equals("RequestHeader")) return "HEADER";
            if (name.equals("RequestBody")) return "REQUEST_BODY";
        }
        return "UNKNOWN";
    }

    private boolean bindingRequired(com.github.javaparser.ast.NodeList<AnnotationExpr> annotations,
        String location) {
        if (location.equals("UNKNOWN")) return false;
        for (AnnotationExpr annotation : annotations) {
            if (java.util.Set.of("PathVariable", "RequestParam", "RequestHeader", "RequestBody")
                .contains(annotation.getName().getIdentifier())) {
                Object required = annotationAttributes(annotation).get("required");
                return !(required instanceof Boolean value) || value;
            }
        }
        return true;
    }

    private String bindingName(com.github.javaparser.ast.NodeList<AnnotationExpr> annotations,
        String javaName, String location) {
        if (location.equals("UNKNOWN") || location.equals("REQUEST_BODY")) return javaName;
        for (AnnotationExpr annotation : annotations) {
            if (!java.util.Set.of("PathVariable", "RequestParam", "RequestHeader")
                .contains(annotation.getName().getIdentifier())) continue;
            java.util.Map<String, Object> attributes = annotationAttributes(annotation);
            Object explicit = attributes.containsKey("name") ? attributes.get("name") : attributes.get("value");
            if (explicit instanceof String value && !value.isBlank()) return value;
        }
        return javaName;
    }

    private java.util.Map<String, Object> annotationAttributes(AnnotationExpr annotation) {
        java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        if (annotation.isNormalAnnotationExpr()) {
            annotation.asNormalAnnotationExpr().getPairs().forEach(pair ->
                attributes.put(pair.getNameAsString(), annotationValue(pair.getValue())));
        } else if (annotation instanceof SingleMemberAnnotationExpr single) {
            attributes.put("value", annotationValue(single.getMemberValue()));
        }
        return attributes;
    }

    private Object annotationValue(Expression value) {
        if (value instanceof StringLiteralExpr literal) return literal.asString();
        if (value instanceof BooleanLiteralExpr literal) return literal.getValue();
        if (value instanceof IntegerLiteralExpr literal) {
            try { return literal.asInt(); } catch (RuntimeException ignored) { return literal.toString(); }
        }
        if (value instanceof LongLiteralExpr literal) {
            try { return literal.asLong(); } catch (RuntimeException ignored) { return literal.toString(); }
        }
        if (value instanceof DoubleLiteralExpr literal) {
            try { return literal.asDouble(); } catch (RuntimeException ignored) { return literal.toString(); }
        }
        if (value instanceof CharLiteralExpr literal) return literal.asChar();
        return value.toString();
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
                            field.getNameAsString(), declaredFieldType(field),
                            new FactNodePayload.FieldAccessPayload(field.getNameAsString(),
                                (lombokConstructor ? "LOMBOK_FIELD:" : "INSTANCE_FIELD:") + typeOwner));
                        acc.addNode(valueField);
                        field.findAncestor(com.github.javaparser.ast.body.FieldDeclaration.class)
                            .ifPresent(declaration -> {
                                for (int index = 0; index < declaration.getAnnotations().size(); index++) {
                                    AnnotationExpr annotation = declaration.getAnnotation(index);
                                    SourceRange annotationRange = FactExpressionVisitor.range(annotation, workspace);
                                    FactNode annotationNode = new FactNode(ids.generate(FactNodeType.ANNOTATION,
                                        typeOwner, annotationRange,
                                        "instance-field-annotation:" + field.getNameAsString() + ":" + index),
                                        FactNodeType.ANNOTATION, annotationRange, annotation.toString(),
                                        TypeResolution.notApplicable(), new FactNodePayload.AnnotationPayload(
                                            annotation.getNameAsString(), annotationAttributes(annotation)));
                                    relation(valueField, annotationNode, FactEdgeType.HAS_ANNOTATION, index,
                                        "FIELD_ANNOTATION", acc);
                                }
                            });
                    });
        });
    }

    private TypeResolution declaredFieldType(VariableDeclarator field) {
        String declaredType = field.getTypeAsString();
        String simpleType = declaredType.replaceAll("<.*>", "");
        return field.findCompilationUnit().flatMap(unit -> unit.getImports().stream()
                .filter(imported -> !imported.isAsterisk() && !imported.isStatic())
                .map(imported -> imported.getNameAsString())
                .filter(name -> name.endsWith("." + simpleType))
                .findFirst())
            .map(TypeResolution::resolvedType)
            .orElseGet(() -> TypeResolution.unresolved("DECLARED_FIELD"));
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
                               String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver, boolean isExpression) {
        for (SwitchEntry entry : entries) {
            if (entry.getLabels().isEmpty()) continue;
            for (Expression label : entry.getLabels()) {
                SourceRange range = FactExpressionVisitor.range(entry, workspace);
                String conditionSnippet = switchConditionSnippet(selector, label, entry, isExpression);
                FactNode condition = new FactNode(ids.generate(FactNodeType.CONDITION, owner, range,
                    "switch-case:" + label), FactNodeType.CONDITION, range,
                    conditionSnippet, TypeResolution.notApplicable(),
                    new FactNodePayload.ConditionPayload("SwitchEntry", "=="));
                relation(methodNode, condition, FactEdgeType.CONTROLS, -1, "SWITCH_CASE", acc);
                expressions.visit(selector, condition, owner, workspace, acc, call -> resolve(call, resolver));
                FactNode enumLabel = enumSwitchLabel(selector, label, owner, workspace);
                if (enumLabel == null) {
                    expressions.visit(label, condition, owner, workspace, acc, call -> resolve(call, resolver));
                } else {
                    relation(condition, enumLabel, FactEdgeType.OPERAND_OF, 1, "RIGHT", acc);
                }
                for (Statement child : entry.getStatements()) {
                    if (child instanceof ThrowStmt thrown) {
                        outcome(thrown, condition, FactEdgeType.THEN_OUTCOME,
                            owner, workspace, acc, resolver);
                    } else if (child instanceof ReturnStmt returned) {
                        outcome(returned, condition, FactEdgeType.THEN_OUTCOME,
                            owner, workspace, acc, resolver);
                    } else if (child instanceof YieldStmt yieldStmt && isExpression) {
                        SourceRange resultRange = FactExpressionVisitor.range(yieldStmt, workspace);
                        FactNode returned = new FactNode(ids.generate(FactNodeType.RETURN, owner, resultRange,
                            "switch-result"), FactNodeType.RETURN, resultRange, yieldStmt.toString(),
                            TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("RETURN",
                            "SwitchExpressionResult"));
                        relation(condition, returned, FactEdgeType.THEN_OUTCOME, -1, "SWITCH_RESULT", acc);
                        Expression expr = yieldStmt.getExpression();
                        if (expr != null) {
                            expressions.visit(expr, returned, owner, workspace, acc, call -> resolve(call, resolver));
                        }
                    } else if (child instanceof ExpressionStmt result && isExpression) {
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
    private FactNode enumSwitchLabel(Expression selector, Expression label, String owner, Path workspace) {
        try {
            var selectorType = selector.calculateResolvedType();
            if (!selectorType.isReferenceType()) return null;
            var declaration = selectorType.asReferenceType().getTypeDeclaration().orElse(null);
            if (declaration == null || !declaration.isEnum()) return null;
            String qualifiedType = selectorType.describe();
            SourceRange range = FactExpressionVisitor.range(label, workspace);
            return new FactNode(ids.generate(FactNodeType.ENUM_CONSTANT, owner, range,
                "switch-label:" + label), FactNodeType.ENUM_CONSTANT, range, label.toString(),
                TypeResolution.resolvedType(qualifiedType),
                new FactNodePayload.EnumConstantPayload(qualifiedType, label.toString()));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
    private String switchConditionSnippet(Expression selector, Expression label, SwitchEntry entry,
                                          boolean isExpression) {
        String caseCondition = selector + " == " + label;
        if (!isExpression || entry.getStatements().size() != 1) return caseCondition;
        Statement statement = entry.getStatement(0);
        Expression result = statement instanceof ExpressionStmt expressionStmt
            ? expressionStmt.getExpression()
            : statement instanceof YieldStmt yieldStmt ? yieldStmt.getExpression() : null;
        if (result == null || result.isBooleanLiteralExpr()) return caseCondition;
        return caseCondition + " && (" + result + ")";
    }
    private void directOutcomes(Statement branch, FactNode condition, FactEdgeType edge, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        if (branch instanceof ThrowStmt thrown) outcome(thrown, condition, edge, owner, workspace, acc, resolver);
        else if (branch instanceof ReturnStmt returned) outcome(returned, condition, edge, owner, workspace, acc, resolver);
        else if (branch instanceof BlockStmt block) for (Statement statement : block.getStatements()) {
            if (statement instanceof ThrowStmt thrown) outcome(thrown, condition, edge, owner, workspace, acc, resolver);
            else if (statement instanceof ReturnStmt returned) outcome(returned, condition, edge, owner, workspace, acc, resolver);
        }
    }
    private TypeResolution resolve(MethodCallExpr call, TypeResolver resolver) { return resolver.resolveMethodCall(call).map(r -> TypeResolution.resolvedSignature(r.getQualifiedSignature())).orElseGet(() -> TypeResolution.unresolved("TYPE_RESOLUTION_FAILED")); }
    
    private FactNode getOrCreateReturnNode(ReturnStmt statement, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        SourceRange range = FactExpressionVisitor.range(statement, workspace);
        String id = ids.generate(FactNodeType.RETURN, owner, range, "outcome");
        FactNode returned = acc.nodes().stream()
            .filter(n -> n.id().equals(id))
            .findFirst()
            .orElseGet(() -> {
                FactNode node = new FactNode(id, FactNodeType.RETURN, range, statement.toString(),
                    TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("RETURN", statement.getClass().getSimpleName()));
                acc.addNode(node);
                return node;
            });
        statement.getExpression().ifPresent(expression -> expressions.visit(expression, returned, owner,
            workspace, acc, call -> resolve(call, resolver)));
        return returned;
    }

    private FactNode getOrCreateThrowNode(ThrowStmt statement, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        SourceRange range = FactExpressionVisitor.range(statement, workspace);
        String id = ids.generate(FactNodeType.THROW, owner, range, "outcome");
        FactNode thrown = acc.nodes().stream()
            .filter(n -> n.id().equals(id))
            .findFirst()
            .orElseGet(() -> {
                FactNode node = new FactNode(id, FactNodeType.THROW, range, statement.toString(),
                    TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("THROW", statement.getClass().getSimpleName()));
                acc.addNode(node);
                return node;
            });
        Expression expression = statement.getExpression();
        if (expression != null) {
            expressions.visit(expression, thrown, owner, workspace, acc, call -> resolve(call, resolver));
            String exceptionType = resolveThrownType(expression);
            SourceRange exceptionRange = FactExpressionVisitor.range(expression, workspace);
            FactNode exceptionNode = new FactNode(ids.generate(FactNodeType.EXCEPTION, owner,
                exceptionRange, "thrown-exception:" + exceptionType), FactNodeType.EXCEPTION,
                exceptionRange, exceptionType,
                exceptionType.equals("UNKNOWN") ? TypeResolution.unresolved("THROWN_TYPE_FAILED")
                    : TypeResolution.resolvedType(exceptionType),
                new FactNodePayload.ExceptionPayload(exceptionType, false));
            relation(thrown, exceptionNode, FactEdgeType.THROWS, 0, "EXCEPTION_TYPE", acc);
        }
        return thrown;
    }

    private String resolveThrownType(Expression expression) {
        try { return expression.calculateResolvedType().describe(); }
        catch (RuntimeException e) {
            if (expression.isObjectCreationExpr()) {
                return expression.asObjectCreationExpr().getTypeAsString();
            }
            return "UNKNOWN";
        }
    }

    private void outcome(com.github.javaparser.ast.Node node, FactNode condition, FactEdgeType edge, String owner, Path workspace, FactGraphAccumulator acc, TypeResolver resolver) {
        if (node instanceof ReturnStmt returned) {
            FactNode n = getOrCreateReturnNode(returned, owner, workspace, acc, resolver);
            relation(condition, n, edge, -1, "RETURN", acc);
        } else if (node instanceof ThrowStmt thrown) {
            FactNode n = getOrCreateThrowNode(thrown, owner, workspace, acc, resolver);
            relation(condition, n, edge, -1, "THROW", acc);
            acc.edges().stream().filter(candidate -> candidate.sourceNodeId().equals(n.id())
                    && candidate.type() == FactEdgeType.THROWS)
                .findFirst().flatMap(candidate -> acc.nodes().stream()
                    .filter(target -> target.id().equals(candidate.targetNodeId())).findFirst())
                .ifPresent(exception -> relation(condition, exception, FactEdgeType.THROWS, -1,
                    edge == FactEdgeType.THEN_OUTCOME ? "THEN_EXCEPTION" : "ELSE_EXCEPTION", acc));
        }
    }
    private com.github.javaparser.ast.Node findExecutableScope(com.github.javaparser.ast.Node node) {
        return node.findAncestor(com.github.javaparser.ast.Node.class, parent ->
            parent instanceof CallableDeclaration || parent instanceof LambdaExpr
        ).orElse(null);
    }
    private void relation(FactNode a, FactNode b, FactEdgeType type, int ordinal, String role, FactGraphAccumulator acc) { acc.addRelation(a, b, new FactEdge(ids.edgeId(a.id(), b.id(), type.name(), ordinal, role), a.id(), b.id(), type, ordinal, role)); }
    private String operator(com.github.javaparser.ast.expr.Expression expression) { if (expression instanceof com.github.javaparser.ast.expr.BinaryExpr b) return b.getOperator().asString(); if (expression instanceof com.github.javaparser.ast.expr.UnaryExpr u) return u.getOperator().asString(); return expression.getClass().getSimpleName(); }
}
