package io.atworks.specscan.analysis.support;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.ResponseBinding;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EndpointExtractor {

    static final String MVC_VIEW_RESPONSE = "__mvc_view__";

    private final Path workspaceRoot;
    private static final Pattern PATH_PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}/]+)}");
    private static final Set<String> FRAMEWORK_PARAMETER_TYPES = Set.of(
        "BindingResult",
        "Errors",
        "HttpServletRequest",
        "HttpServletResponse",
        "HttpSession",
        "InputStream",
        "Locale",
        "Model",
        "ModelMap",
        "NativeWebRequest",
        "OutputStream",
        "Principal",
        "RedirectAttributes",
        "ServletRequest",
        "ServletResponse",
        "SessionStatus",
        "TimeZone",
        "UriComponentsBuilder",
        "WebRequest",
        "Writer",
        "ZoneId"
    );

    public EndpointExtractor(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
        StaticJavaParser.getConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
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

                            List<RequestBinding> requestBindings = alignPathBindings(finalPath, extractRequestBindings(method, file));
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
                return "GET";
        }
    }

    private String getMappingPath(AnnotationExpr mappingAnn) {
        return getAnnotationValue(mappingAnn).orElse("");
    }

    private Optional<String> getAnnotationValue(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return Optional.of(cleanStringLiteral(single.getMemberValue().toString()));
        } else if (annotation instanceof NormalAnnotationExpr normal) {
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
            if (isFrameworkParameter(param)) {
                continue;
            }
            String paramName = param.getNameAsString();
            String paramType = param.getType().asString();
            BindingLocation location = BindingLocation.QUERY;
            boolean isRequired = true;
            String description = null;
            String example = null;
            String defaultValue = null;
            Set<String> enumValues = new LinkedHashSet<>();

            if (param.isAnnotationPresent("RequestHeader")) {
                AnnotationExpr requestHeader = param.getAnnotationByName("RequestHeader").orElseThrow();
                location = BindingLocation.HEADER;
                paramName = resolveExplicitBindingName(requestHeader).orElse(paramName);
                isRequired = resolveRequiredAttribute(requestHeader);
                defaultValue = resolveDefaultValue(requestHeader);
            } else if (param.isAnnotationPresent("PathVariable")) {
                AnnotationExpr pathVariable = param.getAnnotationByName("PathVariable").orElseThrow();
                location = BindingLocation.PATH;
                paramName = resolveExplicitBindingName(pathVariable).orElse(paramName);
                isRequired = resolveRequiredAttribute(pathVariable);
            } else if (param.isAnnotationPresent("RequestParam")) {
                AnnotationExpr requestParam = param.getAnnotationByName("RequestParam").orElseThrow();
                location = BindingLocation.QUERY;
                paramName = resolveExplicitBindingName(requestParam).orElse(paramName);
                isRequired = resolveRequiredAttribute(requestParam);
                defaultValue = resolveDefaultValue(requestParam);
            } else if (param.isAnnotationPresent("RequestBody")) {
                location = BindingLocation.BODY;
                isRequired = resolveRequiredAttribute(param.getAnnotationByName("RequestBody").orElseThrow());
            } else if (param.isAnnotationPresent("ModelAttribute")) {
                AnnotationExpr modelAttribute = param.getAnnotationByName("ModelAttribute").orElseThrow();
                location = BindingLocation.BODY;
                paramName = resolveExplicitBindingName(modelAttribute).orElse(paramName);
                isRequired = resolveRequiredAttribute(modelAttribute);
            } else if (isPrimitiveOrSimpleType(paramType)) {
                location = BindingLocation.QUERY;
            } else {
                location = BindingLocation.QUERY;
            }

            description = resolveAnnotationMemberValue(param, "Parameter", "description")
                    .orElseGet(() -> resolveAnnotationMemberValue(param, "Schema", "description").orElse(null));
            example = resolveAnnotationMemberValue(param, "Parameter", "example")
                    .orElseGet(() -> resolveAnnotationMemberValue(param, "Schema", "example").orElse(null));
            enumValues.addAll(resolveAnnotationArrayValues(param, "Schema", "allowableValues"));
            enumValues.addAll(resolveAnnotationArrayValues(param, "Schema", "enumeration"));

            SourceTrace trace = SourceTraceResolver.resolve(param, workspaceRoot, file);
            bindings.add(new RequestBinding(
                paramName,
                location,
                paramType,
                isRequired,
                description,
                example,
                defaultValue,
                List.copyOf(enumValues),
                trace
            ));
        }
        return bindings;
    }

    private List<RequestBinding> alignPathBindings(String path, List<RequestBinding> bindings) {
        Set<String> placeholders = extractPathPlaceholders(path);
        if (placeholders.isEmpty()) {
            return bindings;
        }

        List<RequestBinding> aligned = new ArrayList<>();
        for (RequestBinding binding : bindings) {
            if (binding.targetLocation() == BindingLocation.QUERY && placeholders.contains(binding.parameterName())) {
                aligned.add(new RequestBinding(
                    binding.parameterName(),
                    BindingLocation.PATH,
                    binding.type(),
                    binding.isRequired(),
                    binding.description(),
                    binding.example(),
                    binding.defaultValue(),
                    binding.enumValues(),
                    binding.sourceTrace()
                ));
                continue;
            }
            aligned.add(binding);
        }
        return aligned;
    }

    private Set<String> extractPathPlaceholders(String path) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PATH_PLACEHOLDER_PATTERN.matcher(path);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
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

    private String resolveDefaultValue(AnnotationExpr annotation) {
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return null;
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (!pair.getNameAsString().equals("defaultValue")) {
                continue;
            }
            String value = cleanStringLiteral(pair.getValue().toString());
            if (value.contains("ValueConstants.DEFAULT_NONE")) {
                return null;
            }
            return value;
        }
        return null;
    }

    private Optional<String> resolveExplicitBindingName(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr single) {
            return Optional.of(cleanStringLiteral(single.getMemberValue().toString()));
        }
        if (!(annotation instanceof NormalAnnotationExpr normal)) {
            return Optional.empty();
        }
        for (MemberValuePair pair : normal.getPairs()) {
            String name = pair.getNameAsString();
            if (name.equals("value") || name.equals("name")) {
                String value = cleanStringLiteral(pair.getValue().toString());
                if (!value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> resolveAnnotationMemberValue(Parameter parameter, String annotationName, String memberName) {
        Optional<AnnotationExpr> annotationOpt = parameter.getAnnotationByName(annotationName);
        if (annotationOpt.isEmpty() || !(annotationOpt.get() instanceof NormalAnnotationExpr normal)) {
            return Optional.empty();
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (pair.getNameAsString().equals(memberName)) {
                return Optional.of(cleanStringLiteral(pair.getValue().toString()));
            }
        }
        return Optional.empty();
    }

    private List<String> resolveAnnotationArrayValues(Parameter parameter, String annotationName, String memberName) {
        Optional<AnnotationExpr> annotationOpt = parameter.getAnnotationByName(annotationName);
        if (annotationOpt.isEmpty() || !(annotationOpt.get() instanceof NormalAnnotationExpr normal)) {
            return List.of();
        }
        for (MemberValuePair pair : normal.getPairs()) {
            if (!pair.getNameAsString().equals(memberName)) {
                continue;
            }
            String raw = pair.getValue().toString().trim();
            if (!raw.startsWith("{") || !raw.endsWith("}")) {
                return List.of(cleanStringLiteral(raw));
            }
            String inner = raw.substring(1, raw.length() - 1).trim();
            if (inner.isEmpty()) {
                return List.of();
            }
            String[] tokens = inner.split(",");
            List<String> values = new ArrayList<>();
            for (String token : tokens) {
                values.add(cleanStringLiteral(token.trim()));
            }
            return values;
        }
        return List.of();
    }

    private boolean isPrimitiveOrSimpleType(String typeStr) {
        typeStr = typeStr.toLowerCase();
        return typeStr.equals("string") || typeStr.equals("int") || typeStr.equals("integer") ||
                typeStr.equals("long") || typeStr.equals("double") || typeStr.equals("float") ||
                typeStr.equals("boolean") || typeStr.equals("map") || typeStr.equals("list");
    }

    private ResponseBinding extractResponseBinding(MethodDeclaration method, Path file) {
        String rawType = method.getType().asString();
        String unwrappedType = classifyResponseType(method, rawType);
        SourceTrace trace = SourceTraceResolver.resolve(method.getType(), workspaceRoot, file);
        ResponseMetadata metadata = explicitResponseMetadata(method);
        return new ResponseBinding(unwrappedType, trace, metadata.status(), metadata.source(), metadata.headers());
    }

    private ResponseMetadata explicitResponseMetadata(MethodDeclaration method) {
        Set<Integer> statuses = new LinkedHashSet<>();
        List<String> sources = new ArrayList<>();
        method.getAnnotationByName("ResponseStatus").ifPresent(annotation -> {
            Integer status = parseStatus(annotation.toString());
            if (status != null) { statuses.add(status); sources.add("@ResponseStatus"); }
        });
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        for (com.github.javaparser.ast.expr.MethodCallExpr call : method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)) {
            String scope = call.getScope().map(Object::toString).orElse("");
            String name = call.getNameAsString();
            if (("ok".equals(name) || "accepted".equals(name) || "noContent".equals(name))
                    && scope.contains("ResponseEntity")) {
                statuses.add("ok".equals(name) ? 200 : "accepted".equals(name) ? 202 : 204);
                sources.add("ResponseEntity." + name);
            } else if ("status".equals(name) && scope.contains("ResponseEntity") && !call.getArguments().isEmpty()) {
                Integer status = parseStatus(call.getArgument(0).toString());
                if (status != null) { statuses.add(status); sources.add("ResponseEntity.status"); }
            } else if ("header".equals(name) && call.getArguments().size() >= 2) {
                String headerName = cleanStringLiteral(call.getArgument(0).toString());
                String headerValue = cleanStringLiteral(call.getArgument(1).toString());
                if (!headerName.isBlank() && !headerValue.isBlank()) headers.put(headerName, headerValue);
            }
        }
        if (statuses.size() > 1) return new ResponseMetadata(null, "CONFLICT:" + statuses, headers);
        Integer status = statuses.stream().findFirst().orElse(null);
        return new ResponseMetadata(status, status == null ? null : String.join("+", new LinkedHashSet<>(sources)), headers);
    }

    private Integer parseStatus(String rawValue) {
            String value = rawValue.toUpperCase(java.util.Locale.ROOT);
            Map<String, Integer> known = Map.ofEntries(
                Map.entry("CONTINUE", 100), Map.entry("OK", 200), Map.entry("CREATED", 201),
                Map.entry("ACCEPTED", 202), Map.entry("NO_CONTENT", 204), Map.entry("BAD_REQUEST", 400),
                Map.entry("UNAUTHORIZED", 401), Map.entry("FORBIDDEN", 403), Map.entry("NOT_FOUND", 404),
                Map.entry("CONFLICT", 409), Map.entry("UNPROCESSABLE_ENTITY", 422),
                Map.entry("INTERNAL_SERVER_ERROR", 500));
            for (Map.Entry<String, Integer> entry : known.entrySet()) {
                if (value.matches("(?s).*\\b" + entry.getKey() + "\\b.*")) return entry.getValue();
            }
            java.util.regex.Matcher numeric = java.util.regex.Pattern.compile("\\b([1-5][0-9]{2})\\b").matcher(value);
            return numeric.find() ? Integer.valueOf(numeric.group(1)) : null;
    }

    private record ResponseMetadata(Integer status, String source, Map<String, String> headers) {}

    private boolean isFrameworkParameter(Parameter parameter) {
        if (parameter.isAnnotationPresent("RequestParam")
            || parameter.isAnnotationPresent("PathVariable")
            || parameter.isAnnotationPresent("RequestHeader")
            || parameter.isAnnotationPresent("RequestBody")
            || parameter.isAnnotationPresent("ModelAttribute")) {
            return false;
        }

        String typeName = parameter.getType().asString();
        if (FRAMEWORK_PARAMETER_TYPES.contains(typeName)) {
            return true;
        }

        return typeName.endsWith("BindingResult")
            || typeName.endsWith("Errors")
            || typeName.endsWith("HttpServletRequest")
            || typeName.endsWith("HttpServletResponse")
            || typeName.endsWith("HttpSession")
            || typeName.endsWith("Model")
            || typeName.endsWith("ModelMap")
            || typeName.endsWith("NativeWebRequest")
            || typeName.endsWith("RedirectAttributes")
            || typeName.endsWith("ServletRequest")
            || typeName.endsWith("ServletResponse")
            || typeName.endsWith("SessionStatus")
            || typeName.endsWith("UriComponentsBuilder")
            || typeName.endsWith("WebRequest");
    }

    private String classifyResponseType(MethodDeclaration method, String rawType) {
        String unwrappedType = unwrapResponseType(rawType);
        if (isMvcViewResponse(method, rawType, unwrappedType)) {
            return MVC_VIEW_RESPONSE;
        }
        return unwrappedType;
    }

    private boolean isMvcViewResponse(MethodDeclaration method, String rawType, String unwrappedType) {
        if (producesResponseBody(method, rawType)) {
            return false;
        }
        return "ModelAndView".equals(unwrappedType)
            || unwrappedType.endsWith(".ModelAndView")
            || "View".equals(unwrappedType)
            || unwrappedType.endsWith(".View")
            || "String".equals(unwrappedType);
    }

    private boolean producesResponseBody(MethodDeclaration method, String rawType) {
        if (method.isAnnotationPresent("ResponseBody")) {
            return true;
        }
        Optional<ClassOrInterfaceDeclaration> parentClass = method.findAncestor(ClassOrInterfaceDeclaration.class);
        if (parentClass.isPresent()) {
            ClassOrInterfaceDeclaration clazz = parentClass.orElseThrow();
            if (clazz.isAnnotationPresent("RestController") || clazz.isAnnotationPresent("ResponseBody")) {
                return true;
            }
        }
        return rawType.startsWith("ResponseEntity<")
            || rawType.startsWith("HttpEntity<")
            || rawType.startsWith("Mono<ResponseEntity<")
            || rawType.startsWith("Mono<HttpEntity<");
    }

    private String unwrapResponseType(String typeStr) {
        if (typeStr.startsWith("ResponseEntity<") && typeStr.endsWith(">")) {
            return unwrapResponseType(typeStr.substring("ResponseEntity<".length(), typeStr.length() - 1));
        }
        if (typeStr.startsWith("HttpEntity<") && typeStr.endsWith(">")) {
            return unwrapResponseType(typeStr.substring("HttpEntity<".length(), typeStr.length() - 1));
        }
        if (typeStr.startsWith("Mono<") && typeStr.endsWith(">")) {
            return unwrapResponseType(typeStr.substring("Mono<".length(), typeStr.length() - 1));
        }
        if (typeStr.startsWith("Flux<") && typeStr.endsWith(">")) {
            return "List<" + unwrapResponseType(typeStr.substring("Flux<".length(), typeStr.length() - 1)) + ">";
        }
        return typeStr;
    }
}
