package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.GraphEdge;
import io.atworks.specscan.analysis.domain.GraphEdgeType;
import io.atworks.specscan.analysis.domain.GraphNode;
import io.atworks.specscan.analysis.domain.GraphNodeType;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValidationEvidenceGraphBuilder {

    private static final int DEFAULT_TRAVERSAL_BUDGET = 12;

    public ValidationEvidenceGraph build(
        StaticScanResult scanResult,
        ValidationExtractionResult extractResult,
        RepositorySource source
    ) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();

        WorkspaceContext workspace = source.workspaceContext();
        if (workspace == null || workspace.workspacePath() == null) {
            return new ValidationEvidenceGraph(nodes, edges);
        }

        Path workspacePath = Paths.get(workspace.workspacePath());
        List<Path> sourceRoots = source.sourceRoots().stream()
            .map(r -> workspacePath.resolve(r.rootPath()))
            .filter(Files::exists)
            .collect(Collectors.toList());

        TypeResolver typeResolver = new TypeResolver(sourceRoots);
        Map<String, ExceptionHandlerInfo> exceptionHandlers = scanExceptionHandlers(sourceRoots);

        for (ApiEndpoint endpoint : scanResult.endpoints()) {
            String endpointId = "ENDPOINT:" + endpoint.httpMethod() + ":" + endpoint.path();
            String endpointLabel = endpoint.httpMethod() + " " + endpoint.path();
            String epFile = endpoint.sourceTrace() != null ? endpoint.sourceTrace().fileRelativePath() : "";
            int epLine = endpoint.sourceTrace() != null ? endpoint.sourceTrace().startLine() : 0;
            String epSnippet = endpoint.controllerClass() + "." + endpoint.controllerMethod();

            addNode(nodes, nodeIds, endpointId, GraphNodeType.ENDPOINT, endpointLabel, epFile, epLine, epSnippet);
            addRequestBindingNodes(endpoint, typeResolver, workspacePath, sourceRoots, nodes, edges, nodeIds);

            typeResolver.resolveClassDeclaration(endpoint.controllerClass()).ifPresent(controllerDecl ->
                controllerDecl.getMethodsByName(endpoint.controllerMethod()).forEach(controllerMethod -> {
                    Set<String> visitedMethods = new HashSet<>();
                    controllerMethod.findAll(MethodCallExpr.class).forEach(call ->
                        resolveSourceMethod(call, typeResolver, workspacePath).ifPresent(target -> {
                            addMethodNode(target, nodes, nodeIds);
                            addEdge(edges, endpointId, target.nodeId(), GraphEdgeType.CALLS, call.toString());
                            traceMethodEvidence(
                                target,
                                nodes,
                                nodeIds,
                                edges,
                                exceptionHandlers,
                                workspacePath,
                                sourceRoots,
                                typeResolver,
                                DEFAULT_TRAVERSAL_BUDGET - 1,
                                visitedMethods
                            );
                        })
                    );
                })
            );
        }

        return new ValidationEvidenceGraph(nodes, edges);
    }

    private void addRequestBindingNodes(
        ApiEndpoint endpoint,
        TypeResolver typeResolver,
        Path workspacePath,
        List<Path> sourceRoots,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        Set<String> nodeIds
    ) {
        String endpointId = "ENDPOINT:" + endpoint.httpMethod() + ":" + endpoint.path();
        for (RequestBinding binding : endpoint.requestBindings()) {
            typeResolver.resolveClassDeclaration(binding.type()).ifPresent(dtoClass -> {
                Path dtoFile = getFilePath(dtoClass, workspacePath);
                String dtoRelFile = workspacePath.relativize(dtoFile).toString().replace("\\", "/");
                String dtoClassSimpleName = dtoClass.getNameAsString();

                dtoClass.getFields().forEach(field -> {
                    if (field.getVariables().isEmpty()) {
                        return;
                    }
                    String fieldName = field.getVariable(0).getNameAsString();
                    String fieldId = "DTO_FIELD:" + dtoClassSimpleName + "." + fieldName;
                    String fieldLabel = dtoClassSimpleName + "." + fieldName;
                    int fieldLine = field.getBegin().map(pos -> pos.line).orElse(0);
                    String fieldSnippet = field.toString().trim();

                    addNode(nodes, nodeIds, fieldId, GraphNodeType.DTO_FIELD, fieldLabel, dtoRelFile, fieldLine, fieldSnippet);
                    addEdge(edges, endpointId, fieldId, GraphEdgeType.ACCEPTS, binding.parameterName());

                    field.getAnnotations().forEach(ann -> {
                        String annName = ann.getNameAsString();
                        if (isIgnoredAnnotation(annName)) {
                            return;
                        }

                        String validatorId = "VALIDATOR:" + annName;
                        int annLine = ann.getBegin().map(pos -> pos.line).orElse(0);
                        addNode(nodes, nodeIds, validatorId, GraphNodeType.VALIDATOR, annName, dtoRelFile, annLine, ann.toString());
                        addEdge(edges, fieldId, validatorId, GraphEdgeType.ANNOTATED_WITH, ann.toString());

                        if (!isStandardValidationAnnotation(annName)) {
                            findTypeInSourceRoots(annName, sourceRoots).ifPresent(annDecl ->
                                annDecl.getAnnotationByName("Constraint").ifPresent(constraintAnn -> {
                                    String validatorClassName = resolveConstraintValidatorClass(constraintAnn);
                                    if (validatorClassName == null) {
                                        return;
                                    }
                                    findTypeInSourceRoots(validatorClassName, sourceRoots).ifPresent(validatorDecl -> {
                                        Path valFile = getFilePath(validatorDecl, workspacePath);
                                        String valRelFile = workspacePath.relativize(valFile).toString().replace("\\", "/");
                                        String valNodeId = "VALIDATOR:" + validatorClassName;
                                        int valLine = validatorDecl.getBegin().map(pos -> pos.line).orElse(0);

                                        addNode(nodes, nodeIds, valNodeId, GraphNodeType.VALIDATOR, validatorClassName, valRelFile, valLine, validatorDecl.getNameAsString());
                                        addEdge(edges, validatorId, valNodeId, GraphEdgeType.EVALUATES, "Constraint(validatedBy = " + validatorClassName + ".class)");

                                        if (validatorDecl instanceof ClassOrInterfaceDeclaration classDecl) {
                                            classDecl.getMethodsByName("isValid").forEach(method -> {
                                                int methodLine = method.getBegin().map(pos -> pos.line).orElse(0);
                                                String methodSnippet = method.toString().trim();
                                                String ruleId = "BUSINESS_RULE:" + validatorClassName + ".isValid";
                                                addNode(nodes, nodeIds, ruleId, GraphNodeType.BUSINESS_RULE, "isValid implementation", valRelFile, methodLine, methodSnippet);
                                                addEdge(edges, valNodeId, ruleId, GraphEdgeType.EVALUATES, "isValid()");
                                            });
                                        }
                                    });
                                })
                            );
                        }
                    });
                });
            });
        }
    }

    private void traceMethodEvidence(
        ResolvedSourceMethod methodRef,
        List<GraphNode> nodes,
        Set<String> nodeIds,
        List<GraphEdge> edges,
        Map<String, ExceptionHandlerInfo> exceptionHandlers,
        Path workspacePath,
        List<Path> sourceRoots,
        TypeResolver typeResolver,
        int remainingBudget,
        Set<String> visitedMethods
    ) {
        if (!visitedMethods.add(methodRef.qualifiedSignature())) {
            return;
        }

        addValidationRules(methodRef, nodes, nodeIds, edges, exceptionHandlers, workspacePath);

        if (remainingBudget <= 0) {
            return;
        }

        methodRef.methodDecl().findAll(MethodCallExpr.class).forEach(call ->
            resolveSourceMethod(call, typeResolver, workspacePath).ifPresent(target -> {
                addMethodNode(target, nodes, nodeIds);
                addEdge(edges, methodRef.nodeId(), target.nodeId(), GraphEdgeType.CALLS, call.toString());
                traceMethodEvidence(
                    target,
                    nodes,
                    nodeIds,
                    edges,
                    exceptionHandlers,
                    workspacePath,
                    sourceRoots,
                    typeResolver,
                    remainingBudget - 1,
                    visitedMethods
                );
            })
        );
    }

    private void addValidationRules(
        ResolvedSourceMethod methodRef,
        List<GraphNode> nodes,
        Set<String> nodeIds,
        List<GraphEdge> edges,
        Map<String, ExceptionHandlerInfo> exceptionHandlers,
        Path workspacePath
    ) {
        methodRef.methodDecl().findAll(IfStmt.class).forEach(ifStmt -> {
            List<ValidationOutcome> outcomes = collectValidationOutcomes(ifStmt);
            if (outcomes.isEmpty()) {
                return;
            }

            String ruleId = "BUSINESS_RULE:" + methodRef.qualifiedSignature() + ":" + Math.abs(ifStmt.getCondition().toString().hashCode());
            String ruleLabel = ifStmt.getCondition().toString();
            int ruleLine = ifStmt.getBegin().map(pos -> pos.line).orElse(0);
            String ruleSnippet = ifStmt.toString().trim();

            addNode(nodes, nodeIds, ruleId, GraphNodeType.BUSINESS_RULE, ruleLabel, methodRef.relativeFile(), ruleLine, ruleSnippet);
            addEdge(edges, methodRef.nodeId(), ruleId, GraphEdgeType.EVALUATES, "if statement");

            for (ValidationOutcome outcome : outcomes) {
                if (outcome.throwStmt() != null) {
                    addThrowEvidence(outcome.throwStmt(), ruleId, methodRef.relativeFile(), nodes, nodeIds, edges, exceptionHandlers, workspacePath);
                }
            }
        });
    }

    private List<ValidationOutcome> collectValidationOutcomes(IfStmt ifStmt) {
        List<ValidationOutcome> outcomes = new ArrayList<>();
        collectOutcomesFromBranch(ifStmt.getThenStmt(), outcomes);
        ifStmt.getElseStmt().ifPresent(elseStmt -> collectOutcomesFromBranch(elseStmt, outcomes));
        return outcomes;
    }

    private void collectOutcomesFromBranch(Statement branch, List<ValidationOutcome> outcomes) {
        branch.findAll(ThrowStmt.class).forEach(throwStmt -> outcomes.add(ValidationOutcome.throwing(throwStmt)));
        branch.findAll(ReturnStmt.class).forEach(returnStmt -> outcomes.add(ValidationOutcome.returning(returnStmt)));
    }

    private void addThrowEvidence(
        ThrowStmt throwStmt,
        String ruleId,
        String relativeFile,
        List<GraphNode> nodes,
        Set<String> nodeIds,
        List<GraphEdge> edges,
        Map<String, ExceptionHandlerInfo> exceptionHandlers,
        Path workspacePath
    ) {
        String exceptionName = resolveThrownExceptionName(throwStmt);
        String excId = "EXCEPTION:" + exceptionName;
        int excLine = throwStmt.getBegin().map(pos -> pos.line).orElse(0);
        addNode(nodes, nodeIds, excId, GraphNodeType.EXCEPTION, exceptionName, relativeFile, excLine, throwStmt.toString().trim());
        addEdge(edges, ruleId, excId, GraphEdgeType.THROWS, throwStmt.toString().trim());

        if (!exceptionHandlers.containsKey(exceptionName)) {
            return;
        }

        ExceptionHandlerInfo handlerInfo = exceptionHandlers.get(exceptionName);
        String handlerRelFile = workspacePath.relativize(handlerInfo.file()).toString().replace("\\", "/");
        String statusId = "HTTP_STATUS:" + handlerInfo.httpStatus();
        addNode(nodes, nodeIds, statusId, GraphNodeType.HTTP_STATUS, handlerInfo.httpStatus(), handlerRelFile, handlerInfo.line(), handlerInfo.snippet());
        addEdge(edges, excId, statusId, GraphEdgeType.MAPS_TO, "ExceptionHandler: " + exceptionName);
    }

    private Optional<ResolvedSourceMethod> resolveSourceMethod(
        MethodCallExpr call,
        TypeResolver typeResolver,
        Path workspacePath
    ) {
        return typeResolver.resolveMethodCall(call)
            .flatMap(resolvedMethod -> resolveSourceMethod(resolvedMethod, typeResolver, workspacePath));
    }

    private Optional<ResolvedSourceMethod> resolveSourceMethod(
        ResolvedMethodDeclaration resolvedMethod,
        TypeResolver typeResolver,
        Path workspacePath
    ) {
        Optional<ClassOrInterfaceDeclaration> ownerDeclOpt = typeResolver.resolveClassDeclaration(resolvedMethod.declaringType());
        Optional<MethodDeclaration> methodDeclOpt = typeResolver.resolveMethodDeclaration(resolvedMethod);
        if (ownerDeclOpt.isEmpty() || methodDeclOpt.isEmpty()) {
            return Optional.empty();
        }

        ClassOrInterfaceDeclaration ownerDecl = ownerDeclOpt.get();
        MethodDeclaration methodDecl = methodDeclOpt.get();
        String qualifiedSignature = resolvedMethod.getQualifiedSignature();
        Path methodFile = getFilePath(ownerDecl, workspacePath);
        String relativeFile = workspacePath.relativize(methodFile).toString().replace("\\", "/");
        String displayOwner = ownerDecl.getNameAsString();
        String displayLabel = displayOwner + "." + methodDecl.getNameAsString();
        String nodeId = "SERVICE_METHOD:" + qualifiedSignature;

        return Optional.of(new ResolvedSourceMethod(
            qualifiedSignature,
            nodeId,
            displayLabel,
            relativeFile,
            ownerDecl,
            methodDecl
        ));
    }

    private void addMethodNode(ResolvedSourceMethod methodRef, List<GraphNode> nodes, Set<String> nodeIds) {
        int line = methodRef.methodDecl().getBegin().map(pos -> pos.line).orElse(0);
        String snippet = methodRef.methodDecl().toString().trim();
        addNode(nodes, nodeIds, methodRef.nodeId(), GraphNodeType.SERVICE_METHOD, methodRef.displayLabel(), methodRef.relativeFile(), line, snippet);
    }

    private void addNode(List<GraphNode> nodes, Set<String> nodeIds, String id, GraphNodeType type, String label, String filepath, int line, String snippet) {
        if (nodeIds.add(id)) {
            nodes.add(new GraphNode(id, type, label, filepath, line, snippet));
        }
    }

    private void addEdge(List<GraphEdge> edges, String sourceId, String targetId, GraphEdgeType type, String evidence) {
        edges.add(new GraphEdge(sourceId, targetId, type, evidence));
    }

    private boolean isStandardValidationAnnotation(String name) {
        return name.equals("NotNull") || name.equals("NotEmpty") || name.equals("NotBlank")
            || name.equals("Size") || name.equals("Min") || name.equals("Max")
            || name.equals("Pattern") || name.equals("Email")
            || name.equals("AssertTrue") || name.equals("AssertFalse");
    }

    private boolean isIgnoredAnnotation(String name) {
        return name.equals("Getter") || name.equals("Setter") || name.equals("Builder")
            || name.equals("NoArgsConstructor") || name.equals("AllArgsConstructor")
            || name.equals("Override") || name.equals("Deprecated");
    }

    private String resolveConstraintValidatorClass(AnnotationExpr constraintAnn) {
        if (constraintAnn instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("validatedBy")) {
                    String val = pair.getValue().toString();
                    return val.replace("{", "").replace("}", "").replace(".class", "").trim();
                }
            }
        }
        return null;
    }

    private String resolveThrownExceptionName(ThrowStmt throwStmt) {
        String expr = throwStmt.getExpression().toString();
        if (expr.startsWith("new ")) {
            int braceIdx = expr.indexOf('(');
            if (braceIdx != -1) {
                return expr.substring(4, braceIdx).trim();
            }
            return expr.substring(4).trim();
        }
        return "Exception";
    }

    private Path getFilePath(TypeDeclaration<?> clazz, Path workspacePath) {
        if (clazz.findCompilationUnit().isPresent() && clazz.findCompilationUnit().get().getStorage().isPresent()) {
            return clazz.findCompilationUnit().get().getStorage().get().getPath();
        }
        return workspacePath;
    }

    private Optional<TypeDeclaration<?>> findTypeInSourceRoots(String typeName, List<Path> sourceRoots) {
        int angleIdx = typeName.indexOf('<');
        if (angleIdx != -1) {
            typeName = typeName.substring(0, angleIdx);
        }
        int dotIdx = typeName.lastIndexOf('.');
        if (dotIdx != -1) {
            typeName = typeName.substring(dotIdx + 1);
        }
        String finalSimpleName = typeName.trim();

        for (Path root : sourceRoots) {
            try (Stream<Path> walk = Files.walk(root)) {
                Path found = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(finalSimpleName + ".java"))
                    .findFirst()
                    .orElse(null);

                if (found != null) {
                    CompilationUnit cu = StaticJavaParser.parse(found);
                    if (!cu.getTypes().isEmpty()) {
                        return Optional.of(cu.getType(0));
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return Optional.empty();
    }

    private Map<String, ExceptionHandlerInfo> scanExceptionHandlers(List<Path> sourceRoots) {
        Map<String, ExceptionHandlerInfo> handlers = new HashMap<>();
        for (Path root : sourceRoots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            CompilationUnit cu = StaticJavaParser.parse(file);
                            cu.findAll(MethodDeclaration.class).forEach(method ->
                                method.getAnnotationByName("ExceptionHandler").ifPresent(ann -> {
                                    String exceptionName = resolveHandledException(ann, method);
                                    if (exceptionName != null) {
                                        String httpStatus = resolveHttpStatus(method, cu);
                                        handlers.put(exceptionName, new ExceptionHandlerInfo(
                                            exceptionName,
                                            httpStatus,
                                            file,
                                            method.getBegin().map(pos -> pos.line).orElse(0),
                                            method.toString().trim()
                                        ));
                                    }
                                })
                            );
                        } catch (Exception ignored) {
                        }
                    });
            } catch (IOException ignored) {
            }
        }
        return handlers;
    }

    private String resolveHandledException(AnnotationExpr ann, MethodDeclaration method) {
        String val = null;
        if (ann instanceof com.github.javaparser.ast.expr.SingleMemberAnnotationExpr single) {
            val = single.getMemberValue().toString();
        } else if (ann instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("value")) {
                    val = pair.getValue().toString();
                }
            }
        }
        if (val != null) {
            if (val.startsWith("{") && val.endsWith("}")) {
                val = val.substring(1, val.length() - 1);
            }
            String[] parts = val.split(",");
            if (parts.length > 0) {
                return cleanClassLiteral(parts[0].trim());
            }
        }
        if (!method.getParameters().isEmpty()) {
            return method.getParameter(0).getType().asString();
        }
        return null;
    }

    private String cleanClassLiteral(String val) {
        val = val.replace("{", "").replace("}", "").replace(".class", "").trim();
        int dotIdx = val.lastIndexOf('.');
        if (dotIdx != -1) {
            val = val.substring(dotIdx + 1);
        }
        return val.trim();
    }

    private String resolveHttpStatus(MethodDeclaration method, CompilationUnit cu) {
        Optional<AnnotationExpr> statusAnnOpt = method.getAnnotationByName("ResponseStatus");
        if (statusAnnOpt.isPresent()) {
            String statusExpr = extractStatusValue(statusAnnOpt.get());
            if (statusExpr != null) {
                return statusExpr;
            }
        }

        if (cu.getType(0).getAnnotationByName("ResponseStatus").isPresent()) {
            return extractStatusValue(cu.getType(0).getAnnotationByName("ResponseStatus").get());
        }

        return "UNKNOWN";
    }

    private String extractStatusValue(AnnotationExpr ann) {
        String raw = ann.toString();
        if (raw.contains("HttpStatus.")) {
            int idx = raw.indexOf("HttpStatus.") + "HttpStatus.".length();
            int end = raw.indexOf(')', idx);
            if (end == -1) {
                end = raw.length();
            }
            String enumName = raw.substring(idx, end).replace("}", "").replace(")", "").trim();
            return mapHttpStatus(enumName);
        }
        return null;
    }

    private String mapHttpStatus(String enumName) {
        return switch (enumName) {
            case "BAD_REQUEST" -> "400 BAD_REQUEST";
            case "CONFLICT" -> "409 CONFLICT";
            case "NOT_FOUND" -> "404 NOT_FOUND";
            case "UNAUTHORIZED" -> "401 UNAUTHORIZED";
            case "FORBIDDEN" -> "403 FORBIDDEN";
            default -> enumName;
        };
    }

    private record ResolvedSourceMethod(
        String qualifiedSignature,
        String nodeId,
        String displayLabel,
        String relativeFile,
        ClassOrInterfaceDeclaration ownerDecl,
        MethodDeclaration methodDecl
    ) {
    }

    private record ValidationOutcome(ThrowStmt throwStmt, ReturnStmt returnStmt) {
        private static ValidationOutcome throwing(ThrowStmt throwStmt) {
            return new ValidationOutcome(throwStmt, null);
        }

        private static ValidationOutcome returning(ReturnStmt returnStmt) {
            return new ValidationOutcome(null, returnStmt);
        }
    }

    private record ExceptionHandlerInfo(
        String exceptionName,
        String httpStatus,
        Path file,
        int line,
        String snippet
    ) {
    }
}
