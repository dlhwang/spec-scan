package io.specscan.extract;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.specscan.graph.CodeGraph;
import io.specscan.graph.GraphBuilder;
import io.specscan.model.ApiEndpoint;
import io.specscan.model.Condition;
import io.specscan.model.Operator;
import io.specscan.model.ParamSpec;
import io.specscan.model.TypeSchema;
import io.specscan.parse.AstUtils;
import io.specscan.parse.ProjectIndex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Finds Spring controllers and assembles complete {@link ApiEndpoint} specs. */
public class EndpointExtractor {

    private static final Map<String, String> MAPPINGS = Map.of(
            "GetMapping", "GET", "PostMapping", "POST", "PutMapping", "PUT",
            "DeleteMapping", "DELETE", "PatchMapping", "PATCH", "RequestMapping", "");

    private static final Set<String> SKIP_PARAM_TYPES = Set.of(
            "HttpServletRequest", "HttpServletResponse", "HttpSession", "Principal", "Authentication",
            "Model", "ModelMap", "BindingResult", "Errors", "RedirectAttributes", "UriComponentsBuilder",
            "Locale", "TimeZone", "ZoneId", "WebRequest", "NativeWebRequest");

    private static final Set<String> SKIP_PARAM_ANNOTATIONS = Set.of(
            "AuthenticationPrincipal", "RequestAttribute", "SessionAttribute", "CurrentUser");

    private final ProjectIndex index;
    private final SchemaBuilder schemas;
    private final CodeGraph graph;

    public EndpointExtractor(ProjectIndex index, CodeGraph graph) {
        this.index = index;
        this.schemas = new SchemaBuilder(index);
        this.graph = graph;
    }

    public List<ApiEndpoint> extract() {
        List<ApiEndpoint> endpoints = new ArrayList<>();
        for (TypeDeclaration<?> type : index.typesByQName.values().stream().distinct().toList()) {
            if (!AstUtils.hasAnnotation(type, "RestController", "Controller")) continue;
            String basePath = mappingPath(AstUtils.annotation(type, "RequestMapping").orElse(null));
            for (MethodDeclaration m : type.getMethods()) {
                extractHandler(type, m, basePath).ifPresent(endpoints::add);
            }
        }
        endpoints.sort((a, b) -> (a.path + a.httpMethod).compareTo(b.path + b.httpMethod));
        return endpoints;
    }

    private Optional<ApiEndpoint> extractHandler(TypeDeclaration<?> controller, MethodDeclaration m, String basePath) {
        AnnotationExpr mapping = null;
        String httpMethod = null;
        for (Map.Entry<String, String> e : MAPPINGS.entrySet()) {
            Optional<AnnotationExpr> a = AstUtils.annotation(m, e.getKey());
            if (a.isPresent()) {
                mapping = a.get();
                httpMethod = e.getValue();
                break;
            }
        }
        if (mapping == null) return Optional.empty();
        if (httpMethod.isEmpty()) {
            Object methodMember = AstUtils.annotationMember(mapping, "method");
            httpMethod = methodMember != null ? String.valueOf(methodMember) : "ANY";
        }

        ApiEndpoint ep = new ApiEndpoint();
        ep.controller = controller.getFullyQualifiedName().orElse(controller.getNameAsString());
        ep.operationId = controller.getNameAsString() + "." + m.getNameAsString();
        ep.handler = m.getDeclarationAsString(false, false, false);
        ep.httpMethod = httpMethod;
        ep.path = combine(basePath, mappingPath(mapping));
        ep.consumes = joined(AstUtils.annotationMember(mapping, "consumes"));
        ep.produces = joined(AstUtils.annotationMember(mapping, "produces"));
        ep.at = AstUtils.at(m);

        AstUtils.annotation(m, "Operation").ifPresent(op -> {
            Object s = AstUtils.annotationMember(op, "summary");
            Object d = AstUtils.annotationMember(op, "description");
            if (s != null) ep.summary = String.valueOf(s);
            if (d != null) ep.description = String.valueOf(d);
        });
        AstUtils.annotation(m, "ResponseStatus").ifPresent(rs -> {
            Object v = AstUtils.annotationMember(rs, "value", "code");
            if (v instanceof String name) ep.successStatus = ResponseAssertionExtractor.HTTP_STATUS.get(name);
        });

        Map<String, SymValue> bindings = new LinkedHashMap<>();
        List<Condition> validationConditions = new ArrayList<>();

        boolean boundFormObject = false;
        for (Parameter p : m.getParameters()) {
            boundFormObject |= processParam(controller, p, ep, bindings, validationConditions);
        }

        boolean isRest = AstUtils.hasAnnotation(controller, "RestController")
                || AstUtils.hasAnnotation(controller, "ResponseBody")
                || AstUtils.hasAnnotation(m, "ResponseBody");
        String returnSimple = m.getType().asString().replaceAll("<.*>", "");
        boolean viewEndpoint = !isRest
                && (returnSimple.equals("String") || returnSimple.equals("ModelAndView"));

        if (boundFormObject && ep.consumes == null
                && ("POST".equals(httpMethod) || "PUT".equals(httpMethod) || "PATCH".equals(httpMethod))) {
            ep.consumes = "application/x-www-form-urlencoded";
        }

        // flow analysis: preconditions + domain rules
        FlowAnalyzer.Result flow = FlowAnalyzer.analyzeEndpoint(index, m, bindings);
        mergeConditions(ep.preConditions, validationConditions);
        mergeConditions(ep.preConditions, flow.preConditions());
        mergeConditions(ep.others, flow.others());

        if (viewEndpoint) {
            ep.responseBodyType = "text/html (view)";
            if (ep.produces == null) ep.produces = "text/html";
            if (ep.successStatus == null) ep.successStatus = 200;
            List<String> views = m.getBody()
                    .map(b -> b.findAll(com.github.javaparser.ast.stmt.ReturnStmt.class).stream()
                            .map(rs -> rs.getExpression().orElse(null))
                            .filter(e -> e instanceof com.github.javaparser.ast.expr.StringLiteralExpr)
                            .map(e -> ((com.github.javaparser.ast.expr.StringLiteralExpr) e).asString())
                            .distinct().toList())
                    .orElse(List.of());
            if (!views.isEmpty()) {
                ep.responseAssertions.add(new Condition("$view",
                        views.size() == 1 ? Operator.EQ : Operator.IN,
                        views.size() == 1 ? views.get(0) : views,
                        "BODY", "view-name", "핸들러가 반환하는 뷰 이름", ep.at));
            }
        } else {
            // declared response schema (ResponseEntity unwrapped by SchemaBuilder)
            TypeSchema responseSchema = schemas.fromAstType(m.getType());
            ep.responseBody = responseSchema;
            ep.responseBodyType = responseSchema != null ? responseSchema.javaType : null;

            // response assertions + actual constructed type
            ResponseAssertionExtractor rae = new ResponseAssertionExtractor(index);
            ResponseAssertionExtractor.Result res = rae.extract(m, responseSchema);
            if (ep.successStatus == null) ep.successStatus = res.status() != null ? res.status() : 200;
            if (res.actualTypeQName() != null && (ep.responseBodyType == null
                    || !res.actualTypeQName().equals(ep.responseBodyType))) {
                index.byQualifiedName(res.actualTypeQName()).ifPresent(actual -> {
                    ep.responseBodyType = res.actualTypeQName();
                    ep.responseBody = schemas.objectSchema(res.actualTypeQName(), actual, Map.of(), new ArrayDeque<>());
                });
            }
            mergeConditions(ep.responseAssertions, res.assertions());
        }

        registerInGraph(ep, controller, m);
        return Optional.of(ep);
    }

    /** Returns true when the parameter was flattened as a form/query object. */
    private boolean processParam(TypeDeclaration<?> controller, Parameter p, ApiEndpoint ep,
                                 Map<String, SymValue> bindings, List<Condition> validationConditions) {
        String typeText = p.getType().asString();
        String simpleType = typeText.replaceAll("<.*>", "");
        simpleType = simpleType.substring(simpleType.lastIndexOf('.') + 1);
        if (SKIP_PARAM_TYPES.contains(simpleType)) return false;
        for (String skip : SKIP_PARAM_ANNOTATIONS) {
            if (AstUtils.hasAnnotation(p, skip)) return false;
        }

        String swaggerDesc = AstUtils.annotation(p, "Parameter")
                .map(a -> AstUtils.annotationMember(a, "description"))
                .map(String::valueOf).orElse(null);
        boolean validated = AstUtils.hasAnnotation(p, "Valid", "Validated");
        List<TypeSchema.FieldAnnotation> paramAnnotations = new ArrayList<>();
        for (AnnotationExpr a : p.getAnnotations()) {
            paramAnnotations.add(new TypeSchema.FieldAnnotation(a.getName().getIdentifier(), AstUtils.annotationMembers(a)));
        }

        Optional<AnnotationExpr> pathVar = AstUtils.annotation(p, "PathVariable");
        Optional<AnnotationExpr> reqParam = AstUtils.annotation(p, "RequestParam");
        Optional<AnnotationExpr> reqHeader = AstUtils.annotation(p, "RequestHeader");
        Optional<AnnotationExpr> reqBody = AstUtils.annotation(p, "RequestBody");

        if (simpleType.equals("Pageable")) {
            Object defaultSize = AstUtils.annotation(p, "PageableDefault")
                    .map(a -> AstUtils.annotationMember(a, "size", "value")).orElse(null);
            ep.queryParams.add(new ParamSpec("page", "int", "integer", false, "0", null, "페이지 번호 (0부터)", null));
            ep.queryParams.add(new ParamSpec("size", "int", "integer", false,
                    defaultSize != null ? String.valueOf(defaultSize) : "20", null, "페이지 크기", null));
            ep.queryParams.add(new ParamSpec("sort", "String", "string", false, null, null,
                    "정렬: property,asc|desc", null));
            return false;
        }

        if (pathVar.isPresent()) {
            String name = nameOf(pathVar.get(), p);
            TypeSchema s = schemas.fromAstType(p.getType());
            ep.pathVariables.add(new ParamSpec(name, typeText, s != null ? s.type : "string", true, null,
                    s != null ? s.enumValues : null, swaggerDesc, null));
            bindings.put(p.getNameAsString(), SymValue.path("$." + name, "PATH", null));
            validationConditions.addAll(ValidationExtractor.fromParamAnnotations("$." + name, "PATH", paramAnnotations));
            return false;
        }
        if (reqHeader.isPresent()) {
            String name = nameOf(reqHeader.get(), p);
            Object required = AstUtils.annotationMember(reqHeader.get(), "required");
            Object defVal = AstUtils.annotationMember(reqHeader.get(), "defaultValue");
            TypeSchema s = schemas.fromAstType(p.getType());
            ep.headers.add(new ParamSpec(name, typeText, s != null ? s.type : "string",
                    required instanceof Boolean b ? b : defVal == null, str(defVal),
                    s != null ? s.enumValues : null, swaggerDesc, null));
            bindings.put(p.getNameAsString(), SymValue.path("$." + name, "HEADER", null));
            validationConditions.addAll(ValidationExtractor.fromParamAnnotations("$." + name, "HEADER", paramAnnotations));
            return false;
        }
        if (reqParam.isPresent()) {
            String name = nameOf(reqParam.get(), p);
            Object required = AstUtils.annotationMember(reqParam.get(), "required");
            Object defVal = AstUtils.annotationMember(reqParam.get(), "defaultValue");
            TypeSchema s = schemas.fromAstType(p.getType());
            ep.queryParams.add(new ParamSpec(name, typeText, s != null ? s.type : "string",
                    required instanceof Boolean b ? b : defVal == null, str(defVal),
                    s != null ? s.enumValues : null, swaggerDesc, null));
            bindings.put(p.getNameAsString(), SymValue.path("$." + name, "QUERY", null));
            validationConditions.addAll(ValidationExtractor.fromParamAnnotations("$." + name, "QUERY", paramAnnotations));
            if (s != null && s.enumValues != null) {
                validationConditions.add(new Condition("$." + name, Operator.IN, s.enumValues,
                        "QUERY", "type-constraint", "enum " + simpleName(s.javaType), null));
            }
            return false;
        }
        if (reqBody.isPresent()) {
            TypeSchema schema = schemas.fromAstType(p.getType());
            ep.requestBody = schema;
            ep.requestBodyType = schema != null ? schema.javaType : typeText;
            if (ep.consumes == null) ep.consumes = "application/json";
            bindings.put(p.getNameAsString(), SymValue.path("$", "BODY", ep.requestBodyType));
            validationConditions.addAll(ValidationExtractor.fromSchema(schema, validated, "BODY"));
            return false;
        }

        // @ModelAttribute or annotation-less POJO -> query string object
        TypeSchema schema = schemas.fromAstType(p.getType());
        if (schema != null && "object".equals(schema.type) && schema.fields != null) {
            flattenQueryParams(schema, "", ep, 0);
            bindings.put(p.getNameAsString(), SymValue.path("$", "QUERY", schema.javaType));
            validationConditions.addAll(ValidationExtractor.fromSchema(schema, validated, "QUERY"));
            return true;
        } else if (schema != null) {
            // simple type without any annotation: Spring binds it as a query param by name
            String name = p.getNameAsString();
            ep.queryParams.add(new ParamSpec(name, typeText, schema.type, false, null,
                    schema.enumValues, swaggerDesc, null));
            bindings.put(name, SymValue.path("$." + name, "QUERY", null));
        }
        return false;
    }

    private void flattenQueryParams(TypeSchema schema, String prefix, ApiEndpoint ep, int depth) {
        if (schema.fields == null || depth > 3) return;
        for (Map.Entry<String, TypeSchema> e : schema.fields.entrySet()) {
            TypeSchema f = e.getValue();
            String name = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            if (f == null) continue;
            if ("object".equals(f.type) && f.fields != null) {
                flattenQueryParams(f, name, ep, depth + 1);
            } else {
                ep.queryParams.add(new ParamSpec(name, simpleName(f.javaType), f.type, false, null,
                        f.enumValues, f.description, f.example));
            }
        }
    }

    private void registerInGraph(ApiEndpoint ep, TypeDeclaration<?> controller, MethodDeclaration m) {
        if (graph == null) return;
        String endpointId = ep.httpMethod + " " + ep.path;
        String typeId = controller.getFullyQualifiedName().orElse(controller.getNameAsString());
        String file = m.findCompilationUnit()
                .flatMap(cu -> cu.getStorage()).map(s -> s.getPath().toString()).orElse(null);
        graph.addNode(endpointId, CodeGraph.NodeKind.ENDPOINT, endpointId, file,
                m.getRange().map(r -> r.begin.line).orElse(null));
        graph.addEdge(endpointId, GraphBuilder.methodId(typeId, m), CodeGraph.EdgeKind.HANDLED_BY);
    }

    private static void mergeConditions(List<Condition> target, List<Condition> add) {
        Set<String> keys = new java.util.HashSet<>();
        for (Condition c : target) keys.add(c.key());
        for (Condition c : add) {
            if (keys.add(c.key())) target.add(c);
        }
    }

    private static String nameOf(AnnotationExpr a, Parameter p) {
        Object v = AstUtils.annotationMember(a, "value", "name");
        return v != null ? String.valueOf(v) : p.getNameAsString();
    }

    private static String mappingPath(AnnotationExpr mapping) {
        if (mapping == null) return "";
        Object v = AstUtils.annotationMember(mapping, "value", "path");
        if (v instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0));
        return v != null ? String.valueOf(v) : "";
    }

    private static String combine(String base, String sub) {
        String path = ("/" + base + "/" + sub).replaceAll("/+", "/");
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static String joined(Object v) {
        if (v == null) return null;
        if (v instanceof List<?> list) return String.join(",", list.stream().map(String::valueOf).toList());
        return String.valueOf(v);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String simpleName(String qname) {
        if (qname == null) return null;
        String s = qname.replaceAll("<.*>", "");
        return s.substring(s.lastIndexOf('.') + 1);
    }
}
