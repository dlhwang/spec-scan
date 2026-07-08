package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.ResponseBinding;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class EndpointExtractor {

    private final Path workspaceRoot;

    public EndpointExtractor(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public List<ApiEndpoint> extract(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<ApiEndpoint> endpoints = new ArrayList<>();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
                if (isSpringController(clazz)) {
                    String controllerClass = clazz.getFullyQualifiedName().orElse(clazz.getNameAsString());
                    String basePath = getControllerBasePath(clazz);

                    clazz.getMethods().forEach(method -> {
                        getMappingAnnotation(method).ifPresent(mappingAnn -> {
                            String httpMethod = resolveHttpMethod(mappingAnn);
                            String methodPath = getMappingPath(mappingAnn);
                            String finalPath = combinePaths(basePath, methodPath);

                            List<RequestBinding> requestBindings = extractRequestBindings(method, file);
                            ResponseBinding responseBinding = extractResponseBinding(method, file);
                            SourceTrace methodTrace = SourceTraceResolver.resolve(method, workspaceRoot, file);

                            endpoints.add(new ApiEndpoint(
                                httpMethod,
                                finalPath,
                                controllerClass,
                                method.getNameAsString(),
                                requestBindings,
                                responseBinding,
                                methodTrace
                            ));
                        });
                    });
                }
            });

            return endpoints;
        } catch (IOException e) {
            System.err.println("Warning: Failed to parse file for endpoint extraction " + file + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean isSpringController(ClassOrInterfaceDeclaration clazz) {
        return clazz.isAnnotationPresent("RestController") || clazz.isAnnotationPresent("Controller");
    }

    private String getControllerBasePath(ClassOrInterfaceDeclaration clazz) {
        return clazz.getAnnotationByName("RequestMapping")
                .flatMap(this::getAnnotationValue)
                .orElse("");
    }

    private Optional<AnnotationExpr> getMappingAnnotation(MethodDeclaration method) {
        String[] mappingNames = {"RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"};
        for (String name : mappingNames) {
            Optional<AnnotationExpr> ann = method.getAnnotationByName(name);
            if (ann.isPresent()) {
                return ann;
            }
        }
        return Optional.empty();
    }

    private String resolveHttpMethod(AnnotationExpr mappingAnn) {
        String name = mappingAnn.getNameAsString();
        switch (name) {
            case "GetMapping": return "GET";
            case "PostMapping": return "POST";
            case "PutMapping": return "PUT";
            case "DeleteMapping": return "DELETE";
            case "PatchMapping": return "PATCH";
            default:
                // RequestMapping인 경우 method 속성을 읽음
                if (mappingAnn instanceof NormalAnnotationExpr normal) {
                    for (MemberValuePair pair : normal.getPairs()) {
                        if (pair.getNameAsString().equals("method")) {
                            String val = pair.getValue().toString();
                            if (val.contains("RequestMethod.POST")) return "POST";
                            if (val.contains("RequestMethod.PUT")) return "PUT";
                            if (val.contains("RequestMethod.DELETE")) return "DELETE";
                            if (val.contains("RequestMethod.PATCH")) return "PATCH";
                        }
                    }
                }
                return "GET"; // 기본값 GET
        }
    }

    private String getMappingPath(AnnotationExpr mappingAnn) {
        return getAnnotationValue(mappingAnn).orElse("");
    }

    private Optional<String> getAnnotationValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return Optional.of(cleanStringLiteral(single.getMemberValue().toString()));
        } else if (annotation instanceof NormalAnnotationExpr normal) {
            // value 또는 path 속성 탐색
            for (MemberValuePair pair : normal.getPairs()) {
                String name = pair.getNameAsString();
                if (name.equals("value") || name.equals("path")) {
                    return Optional.of(cleanStringLiteral(pair.getValue().toString()));
                }
            }
        }
        return Optional.empty();
    }

    private String cleanStringLiteral(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String combinePaths(String base, String methodPath) {
        base = base.trim();
        methodPath = methodPath.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (methodPath.startsWith("/")) {
            methodPath = methodPath.substring(1);
        }
        String combined = base + "/" + methodPath;
        if (!combined.startsWith("/")) {
            combined = "/" + combined;
        }
        if (combined.endsWith("/") && combined.length() > 1) {
            combined = combined.substring(0, combined.length() - 1);
        }
        return combined;
    }

    private List<RequestBinding> extractRequestBindings(MethodDeclaration method, Path file) {
        List<RequestBinding> bindings = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            String paramName = param.getNameAsString();
            String paramType = param.getType().asString();
            BindingLocation location = BindingLocation.QUERY; // 기본값
            boolean isRequired = true; // 기본값

            if (param.isAnnotationPresent("RequestHeader")) {
                location = BindingLocation.HEADER;
                isRequired = resolveRequiredAttribute(param.getAnnotationByName("RequestHeader").get());
            } else if (param.isAnnotationPresent("PathVariable")) {
                location = BindingLocation.PATH;
                // PathVariable은 항상 required = true가 기본
                isRequired = resolveRequiredAttribute(param.getAnnotationByName("PathVariable").get());
            } else if (param.isAnnotationPresent("RequestParam")) {
                location = BindingLocation.QUERY;
                isRequired = resolveRequiredAttribute(param.getAnnotationByName("RequestParam").get());
            } else if (param.isAnnotationPresent("RequestBody")) {
                location = BindingLocation.BODY;
                isRequired = resolveRequiredAttribute(param.getAnnotationByName("RequestBody").get());
            } else {
                // 애노테이션이 없는 경우
                if (isPrimitiveOrSimpleType(paramType)) {
                    location = BindingLocation.QUERY;
                } else {
                    // 사용자 정의 DTO(POJO)인 경우
                    location = BindingLocation.QUERY; // Spring MVC @ModelAttribute 성격 반영
                }
            }

            SourceTrace trace = SourceTraceResolver.resolve(param, workspaceRoot, file);
            bindings.add(new RequestBinding(paramName, location, paramType, isRequired, trace));
        }
        return bindings;
    }

    private boolean resolveRequiredAttribute(AnnotationExpr annotation) {
        if (annotation instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("required")) {
                    return Boolean.parseBoolean(pair.getValue().toString());
                }
            }
        }
        return true;
    }

    private boolean isPrimitiveOrSimpleType(String typeStr) {
        typeStr = typeStr.toLowerCase();
        return typeStr.equals("string") || typeStr.equals("int") || typeStr.equals("integer") ||
                typeStr.equals("long") || typeStr.equals("double") || typeStr.equals("float") ||
                typeStr.equals("boolean") || typeStr.equals("map") || typeStr.equals("list");
    }

    private ResponseBinding extractResponseBinding(MethodDeclaration method, Path file) {
        String rawType = method.getType().asString();
        String unwrappedType = unwrapResponseType(rawType);
        SourceTrace trace = SourceTraceResolver.resolve(method.getType(), workspaceRoot, file);
        return new ResponseBinding(unwrappedType, trace);
    }

    private String unwrapResponseType(String typeStr) {
        if (typeStr.startsWith("ResponseEntity<") && typeStr.endsWith(">")) {
            return typeStr.substring("ResponseEntity<".length(), typeStr.length() - 1);
        }
        if (typeStr.startsWith("HttpEntity<") && typeStr.endsWith(">")) {
            return typeStr.substring("HttpEntity<".length(), typeStr.length() - 1);
        }
        return typeStr;
    }
}
