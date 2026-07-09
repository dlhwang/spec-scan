package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ValidationEvidenceGraphBuilder {

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
        
        // 1. Scan exception handlers in the workspace
        Map<String, ExceptionHandlerInfo> exceptionHandlers = scanExceptionHandlers(sourceRoots);

        // 2. Loop through endpoints
        for (ApiEndpoint endpoint : scanResult.endpoints()) {
            String endpointId = "ENDPOINT:" + endpoint.httpMethod() + ":" + endpoint.path();
            String endpointLabel = endpoint.httpMethod() + " " + endpoint.path();
            String epFile = endpoint.sourceTrace() != null ? endpoint.sourceTrace().fileRelativePath() : "";
            int epLine = endpoint.sourceTrace() != null ? endpoint.sourceTrace().startLine() : 0;
            String epSnippet = endpoint.controllerClass() + "." + endpoint.controllerMethod();

            addNode(nodes, nodeIds, endpointId, GraphNodeType.ENDPOINT, endpointLabel, epFile, epLine, epSnippet);

            // 2.1 Endpoint -> DTO Field -> Validator relationships
            for (RequestBinding binding : endpoint.requestBindings()) {
                String typeStr = binding.type();
                typeResolver.resolveClassDeclaration(typeStr).ifPresent(dtoClass -> {
                    Path dtoFile = getFilePath(dtoClass, workspacePath);
                    String dtoRelFile = workspacePath.relativize(dtoFile).toString().replace("\\", "/");
                    String dtoClassSimpleName = dtoClass.getNameAsString();

                    dtoClass.getFields().forEach(field -> {
                        if (field.getVariables().isEmpty()) return;
                        String fieldName = field.getVariable(0).getNameAsString();
                        String fieldId = "DTO_FIELD:" + dtoClassSimpleName + "." + fieldName;
                        String fieldLabel = dtoClassSimpleName + "." + fieldName;
                        int fieldLine = field.getBegin().map(pos -> pos.line).orElse(0);
                        String fieldSnippet = field.toString().trim();

                        addNode(nodes, nodeIds, fieldId, GraphNodeType.DTO_FIELD, fieldLabel, dtoRelFile, fieldLine, fieldSnippet);
                        addEdge(edges, endpointId, fieldId, GraphEdgeType.ACCEPTS, binding.parameterName());

                        field.getAnnotations().forEach(ann -> {
                            String annName = ann.getNameAsString();
                            if (isIgnoredAnnotation(annName)) return;

                            String validatorId = "VALIDATOR:" + annName;
                            int annLine = ann.getBegin().map(pos -> pos.line).orElse(0);
                            addNode(nodes, nodeIds, validatorId, GraphNodeType.VALIDATOR, annName, dtoRelFile, annLine, ann.toString());
                            addEdge(edges, fieldId, validatorId, GraphEdgeType.ANNOTATED_WITH, ann.toString());

                            // Check custom validation -> validator mapping
                            if (!isStandardValidationAnnotation(annName)) {
                                findTypeInSourceRoots(annName, sourceRoots).ifPresent(annDecl -> {
                                    annDecl.getAnnotationByName("Constraint").ifPresent(constraintAnn -> {
                                        String validatorClassName = resolveConstraintValidatorClass(constraintAnn);
                                        if (validatorClassName != null) {
                                            findTypeInSourceRoots(validatorClassName, sourceRoots).ifPresent(validatorDecl -> {
                                                Path valFile = getFilePath(validatorDecl, workspacePath);
                                                String valRelFile = workspacePath.relativize(valFile).toString().replace("\\", "/");
                                                
                                                String valNodeId = "VALIDATOR:" + validatorClassName;
                                                int valLine = validatorDecl.getBegin().map(pos -> pos.line).orElse(0);
                                                addNode(nodes, nodeIds, valNodeId, GraphNodeType.VALIDATOR, validatorClassName, valRelFile, valLine, validatorDecl.getNameAsString());
                                                addEdge(edges, validatorId, valNodeId, GraphEdgeType.EVALUATES, "Constraint(validatedBy = " + validatorClassName + ".class)");

                                                // Parse isValid implementation in Custom Validator
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
                                        }
                                    });
                                });
                            }
                        });
                    });
                });
            }

            // 2.2 Endpoint -> Service Method -> Business Rule -> Exception -> HTTP Status relationships
            Optional<ClassOrInterfaceDeclaration> controllerClassOpt = typeResolver.resolveClassDeclaration(endpoint.controllerClass());
            controllerClassOpt.ifPresent(controllerDecl -> {
                controllerDecl.getMethodsByName(endpoint.controllerMethod()).forEach(method -> {
                    method.findAll(MethodCallExpr.class).forEach(call -> {
                        String calledMethod = call.getNameAsString();
                        call.getScope().ifPresent(scope -> {
                            String scopeVar = scope.toString();
                            String serviceType = findFieldType(controllerDecl, scopeVar);
                            if (serviceType != null) {
                                findTypeInSourceRoots(serviceType, sourceRoots).ifPresent(serviceDecl -> {
                                    Path svcFile = getFilePath(serviceDecl, workspacePath);
                                    String svcRelFile = workspacePath.relativize(svcFile).toString().replace("\\", "/");

                                    String svcMethodId = "SERVICE_METHOD:" + serviceType + "." + calledMethod;
                                    String svcMethodLabel = serviceType + "." + calledMethod;
                                    
                                    if (serviceDecl instanceof ClassOrInterfaceDeclaration classDecl) {
                                        classDecl.getMethodsByName(calledMethod).forEach(svcMethod -> {
                                            int svcLine = svcMethod.getBegin().map(pos -> pos.line).orElse(0);
                                            String svcSnippet = svcMethod.toString().trim();
                                            addNode(nodes, nodeIds, svcMethodId, GraphNodeType.SERVICE_METHOD, svcMethodLabel, svcRelFile, svcLine, svcSnippet);
                                            addEdge(edges, endpointId, svcMethodId, GraphEdgeType.CALLS, call.toString());

                                            // Trace internal throw/reject conditions
                                            svcMethod.findAll(IfStmt.class).forEach(ifStmt -> {
                                                ifStmt.findFirst(ThrowStmt.class).ifPresent(throwStmt -> {
                                                    String ruleId = "BUSINESS_RULE:" + serviceType + "." + calledMethod + ":" + Math.abs(ifStmt.getCondition().toString().hashCode());
                                                    String ruleLabel = ifStmt.getCondition().toString();
                                                    int ruleLine = ifStmt.getBegin().map(pos -> pos.line).orElse(0);
                                                    String ruleSnippet = ifStmt.toString().trim();

                                                    addNode(nodes, nodeIds, ruleId, GraphNodeType.BUSINESS_RULE, ruleLabel, svcRelFile, ruleLine, ruleSnippet);
                                                    addEdge(edges, svcMethodId, ruleId, GraphEdgeType.EVALUATES, "if statement");

                                                    // Extract Exception thrown
                                                    String exceptionName = resolveThrownExceptionName(throwStmt);
                                                    String excId = "EXCEPTION:" + exceptionName;
                                                    int excLine = throwStmt.getBegin().map(pos -> pos.line).orElse(0);
                                                    addNode(nodes, nodeIds, excId, GraphNodeType.EXCEPTION, exceptionName, svcRelFile, excLine, throwStmt.toString().trim());
                                                    addEdge(edges, ruleId, excId, GraphEdgeType.THROWS, throwStmt.toString().trim());

                                                    // Map Exception to HTTP status if handler exists
                                                    if (exceptionHandlers.containsKey(exceptionName)) {
                                                        ExceptionHandlerInfo handlerInfo = exceptionHandlers.get(exceptionName);
                                                        String handlerRelFile = workspacePath.relativize(handlerInfo.file()).toString().replace("\\", "/");
                                                        
                                                        String statusId = "HTTP_STATUS:" + handlerInfo.httpStatus();
                                                        addNode(nodes, nodeIds, statusId, GraphNodeType.HTTP_STATUS, handlerInfo.httpStatus(), handlerRelFile, handlerInfo.line(), handlerInfo.snippet());
                                                        addEdge(edges, excId, statusId, GraphEdgeType.MAPS_TO, "ExceptionHandler: " + exceptionName);
                                                    }
                                                });
                                            });
                                        });
                                    }
                                });
                            }
                        });
                    });
                });
            });
        }

        return new ValidationEvidenceGraph(nodes, edges);
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
        return name.equals("NotNull") || name.equals("NotEmpty") || name.equals("NotBlank") ||
               name.equals("Size") || name.equals("Min") || name.equals("Max") ||
               name.equals("Pattern") || name.equals("Email") ||
               name.equals("AssertTrue") || name.equals("AssertFalse");
    }

    private boolean isIgnoredAnnotation(String name) {
        return name.equals("Getter") || name.equals("Setter") || name.equals("Builder") ||
               name.equals("NoArgsConstructor") || name.equals("AllArgsConstructor") ||
               name.equals("Override") || name.equals("Deprecated");
    }

    private String resolveConstraintValidatorClass(AnnotationExpr constraintAnn) {
        if (constraintAnn instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("validatedBy")) {
                    String val = pair.getValue().toString();
                    val = val.replace("{", "").replace("}", "").replace(".class", "").trim();
                    return val;
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

    private String findFieldType(ClassOrInterfaceDeclaration clazz, String fieldName) {
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(fieldName))) {
                return field.getElementType().asString();
            }
        }
        return null;
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
            } catch (IOException ignored) {}
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
                            cu.findAll(MethodDeclaration.class).forEach(method -> {
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
                                });
                            });
                        } catch (Exception ignored) {}
                    });
            } catch (IOException ignored) {}
        }
        return handlers;
    }

    private String resolveHandledException(AnnotationExpr ann, MethodDeclaration method) {
        String val = null;
        if (ann instanceof SingleMemberAnnotationExpr single) {
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
            return parseResponseStatusValue(statusAnnOpt.get());
        }

        Optional<ClassOrInterfaceDeclaration> classDeclOpt = method.findAncestor(ClassOrInterfaceDeclaration.class);
        if (classDeclOpt.isPresent()) {
            Optional<AnnotationExpr> classStatusAnnOpt = classDeclOpt.get().getAnnotationByName("ResponseStatus");
            if (classStatusAnnOpt.isPresent()) {
                return parseResponseStatusValue(classStatusAnnOpt.get());
            }
        }

        String bodyStr = method.getBody().map(Object::toString).orElse("");
        if (bodyStr.contains("BAD_REQUEST") || bodyStr.contains("400")) {
            return "400 BAD_REQUEST";
        }
        if (bodyStr.contains("UNAUTHORIZED") || bodyStr.contains("401")) {
            return "401 UNAUTHORIZED";
        }
        if (bodyStr.contains("FORBIDDEN") || bodyStr.contains("403")) {
            return "403 FORBIDDEN";
        }
        if (bodyStr.contains("NOT_FOUND") || bodyStr.contains("404")) {
            return "404 NOT_FOUND";
        }
        if (bodyStr.contains("CONFLICT") || bodyStr.contains("409")) {
            return "409 CONFLICT";
        }
        
        return "500 INTERNAL_SERVER_ERROR";
    }

    private String parseResponseStatusValue(AnnotationExpr statusAnn) {
        String val = statusAnn.toString();
        if (val.contains("BAD_REQUEST") || val.contains("400")) return "400 BAD_REQUEST";
        if (val.contains("UNAUTHORIZED") || val.contains("401")) return "401 UNAUTHORIZED";
        if (val.contains("FORBIDDEN") || val.contains("403")) return "403 FORBIDDEN";
        if (val.contains("NOT_FOUND") || val.contains("404")) return "404 NOT_FOUND";
        if (val.contains("CONFLICT") || val.contains("409")) return "409 CONFLICT";
        if (val.contains("INTERNAL_SERVER_ERROR") || val.contains("500")) return "500 INTERNAL_SERVER_ERROR";
        
        if (statusAnn instanceof SingleMemberAnnotationExpr single) {
            return single.getMemberValue().toString().replace("HttpStatus.", "");
        } else if (statusAnn instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("code")) {
                    return pair.getValue().toString().replace("HttpStatus.", "");
                }
            }
        }
        return "500 INTERNAL_SERVER_ERROR";
    }

    private static record ExceptionHandlerInfo(
        String exceptionName,
        String httpStatus,
        Path file,
        int line,
        String snippet
    ) {}
}
