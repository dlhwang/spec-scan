package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.TypeResolver;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import java.nio.file.*;
import java.util.*;

public final class DefaultFactCodeGraphBuilder {
    private final DeterministicFactNodeIdGenerator ids = new DeterministicFactNodeIdGenerator();
    private final DefaultMethodTraversalPolicy policy = new DefaultMethodTraversalPolicy();
    private final FactGraphIntegrityValidator validator = new FactGraphIntegrityValidator();

    public FactGraphBuildResult build(StaticScanResult scan, RepositorySource source, FactGraphTraversalBudget budget) {
        if (source.workspaceContext() == null || source.workspaceContext().workspacePath() == null) return new FactGraphBuildResult(List.of(), List.of());
        Path workspace = Paths.get(source.workspaceContext().workspacePath());
        List<Path> roots = source.sourceRoots().stream().map(r -> workspace.resolve(r.rootPath())).filter(Files::exists).toList();
        TypeResolver resolver = new TypeResolver(roots);
        List<FactCodeGraph> graphs = new ArrayList<>(); List<FactGraphDiagnostic> diagnostics = new ArrayList<>();
        for (ApiEndpoint endpoint : scan.endpoints()) buildEndpoint(endpoint, workspace, roots, resolver, budget, graphs, diagnostics);
        return new FactGraphBuildResult(graphs, diagnostics);
    }

    private void buildEndpoint(ApiEndpoint endpoint, Path workspace, List<Path> roots, TypeResolver resolver, FactGraphTraversalBudget budget, List<FactCodeGraph> graphs, List<FactGraphDiagnostic> diagnostics) {
        Optional<MethodDeclaration> root = resolver.resolveClassDeclaration(endpoint.controllerClass())
            .flatMap(c -> selectApiMethod(c.getMethodsByName(endpoint.controllerMethod()), endpoint));
        if (root.isEmpty()) { diagnostics.add(diagnostic(endpoint.controllerClass() + "." + endpoint.controllerMethod(), "API_ROOT_UNRESOLVED", false, budget, new FactGraphTraversalStats(0,0,0), null, "controller method source not found")); return; }
        MethodDeclaration method = root.get(); String owner = endpoint.controllerClass() + "." + method.getSignature().asString();
        FactGraphAccumulator acc = new FactGraphAccumulator(budget); SourceRange range = FactExpressionVisitor.range(method, workspace);
        FactNode rootNode = methodNode(method, owner, range, true); acc.addNode(rootNode);
        TraversalState state = new TraversalState(); state.methodNodes.put(owner, rootNode);
        traverse(method, rootNode, owner, appBase(endpoint.controllerClass()), 0, workspace, resolver, budget, acc, diagnostics, state, new LinkedHashSet<>());
        FactGraphTraversalStats stats = new FactGraphTraversalStats(state.maxDepth, state.visited.size(), acc.edgeCount());
        if (acc.edgeLimitReached()) diagnostics.add(diagnostic(owner, "MAX_EDGES_EXCEEDED", true, budget, stats, range, "edge addition stopped at configured limit"));
        FactCodeGraph graph = acc.snapshot("FACT_GRAPH:" + rootNode.id(), rootNode.id());
        graph = augmentFailureMappings(graph, roots, workspace);
        stats = new FactGraphTraversalStats(state.maxDepth, state.visited.size(), graph.edges().size());
        validator.validate(graph, budget, stats); graphs.add(graph);
    }

    private Optional<MethodDeclaration> selectApiMethod(List<MethodDeclaration> methods, ApiEndpoint endpoint) {
        if (methods.isEmpty()) return Optional.empty();
        int sourceLine = endpoint.sourceTrace() == null ? -1 : endpoint.sourceTrace().startLine();
        if (sourceLine > 0) {
            Optional<MethodDeclaration> byLine = methods.stream()
                .filter(method -> method.getRange().map(range -> sourceLine >= range.begin.line
                    && sourceLine <= range.end.line).orElse(false)).findFirst();
            if (byLine.isPresent()) return byLine;
        }
        int bindingCount = endpoint.requestBindings().size();
        List<MethodDeclaration> byArity = methods.stream()
            .filter(method -> method.getParameters().size() == bindingCount).toList();
        return Optional.of((byArity.size() == 1 ? byArity : methods).get(0));
    }

    private void traverse(CallableDeclaration<?> method, FactNode methodNode, String owner, String appBase, int depth, Path workspace, TypeResolver resolver, FactGraphTraversalBudget budget, FactGraphAccumulator acc, List<FactGraphDiagnostic> diagnostics, TraversalState state, Set<String> path) {
        if (depth > budget.maxDepth()) { diagnostics.add(diagnostic(owner, "MAX_DEPTH_EXCEEDED", true, budget, stats(state, acc), FactExpressionVisitor.range(method, workspace), "method body not visited")); return; }
        if (state.visited.size() >= budget.maxVisitedMethodsPerApi() && !state.visited.contains(owner)) { diagnostics.add(diagnostic(owner, "MAX_VISITED_METHODS_EXCEEDED", true, budget, stats(state, acc), FactExpressionVisitor.range(method, workspace), "method body not visited")); return; }
        if (!path.add(owner)) return;
        if (!state.visited.add(owner)) { path.remove(owner); return; }
        state.maxDepth = Math.max(state.maxDepth, depth);
        new FactMethodVisitor(ids).visit(method, methodNode, owner, workspace, acc, resolver);
        for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
            Optional<ResolvedMethodDeclaration> resolved = resolver.resolveMethodCall(call);
            TypeResolution resolution = resolved.map(r -> TypeResolution.resolvedSignature(r.getQualifiedSignature())).orElseGet(() -> TypeResolution.unresolved("TYPE_RESOLUTION_FAILED"));
            FactNode callNode = new FactExpressionVisitor(ids).callNode(call, owner, workspace, resolution);
            new FactExpressionVisitor(ids).visitCallArguments(call, callNode, owner, workspace, acc,
                nested -> resolver.resolveMethodCall(nested)
                    .map(value -> TypeResolution.resolvedSignature(value.getQualifiedSignature()))
                    .orElseGet(() -> TypeResolution.unresolved("TYPE_RESOLUTION_FAILED")));
            FactNode callOwner = call.findAncestor(LambdaExpr.class)
                .map(lambda -> new FactExpressionVisitor(ids).lambdaNode(lambda, owner, workspace))
                .orElse(methodNode);
            relate(callOwner, callNode, FactEdgeType.CALLS, -1, "CALL", acc);
            if (resolved.isEmpty()) {
                Optional<MethodDeclaration> fallback = resolver.resolveStaticSourceMethodCall(call);
                if (fallback.isEmpty()) continue;
                MethodDeclaration target = fallback.get();
                String targetOwner = resolver.qualifiedOwner(target) + "." + target.getSignature();
                FactNode targetNode = state.methodNodes.get(targetOwner);
                if (targetNode == null) {
                    targetNode = methodNode(target, targetOwner,
                        FactExpressionVisitor.range(target, workspace), false);
                    state.methodNodes.put(targetOwner, targetNode);
                }
                relate(callNode, targetNode, FactEdgeType.CALLS, -1, "SOURCE_FALLBACK", acc);
                traverse(target, targetNode, targetOwner, appBase, depth + 1, workspace, resolver, budget,
                    acc, diagnostics, state, new LinkedHashSet<>(path));
                bindArguments(callNode, targetNode, acc);
                continue;
            }
            ResolvedMethodDeclaration declaration = resolved.get(); String targetOwner = declaration.declaringType().getQualifiedName() + "." + declaration.getSignature();
            Optional<MethodDeclaration> target = resolver.resolveMethodDeclaration(declaration);
            TraversalDecision decision = policy.decide(declaration.declaringType().getQualifiedName(), declaration.getName(), target.isPresent(), appBase);
            if (decision != TraversalDecision.VISIT_BODY || target.isEmpty()) continue;
            FactNode existingTarget = state.methodNodes.get(targetOwner);
            if (existingTarget != null) {
                relate(callNode, existingTarget, FactEdgeType.CALLS, -1, "TARGET", acc);
                bindArguments(callNode, existingTarget, acc);
                continue;
            }
            SourceRange targetRange = FactExpressionVisitor.range(target.get(), workspace); FactNode targetNode = methodNode(target.get(), targetOwner, targetRange, false);
            state.methodNodes.put(targetOwner, targetNode);
            relate(callNode, targetNode, FactEdgeType.CALLS, -1, "TARGET", acc);
            traverse(target.get(), targetNode, targetOwner, appBase, depth + 1, workspace, resolver, budget, acc, diagnostics, state, new LinkedHashSet<>(path));
            bindArguments(callNode, targetNode, acc);
        }
        for (MethodReferenceExpr reference : method.findAll(MethodReferenceExpr.class)) {
            Optional<ResolvedMethodDeclaration> resolved = resolver.resolveMethodReference(reference);
            TypeResolution resolution = resolved.map(value ->
                TypeResolution.resolvedSignature(value.getQualifiedSignature()))
                .orElseGet(() -> TypeResolution.unresolved("TYPE_RESOLUTION_FAILED"));
            FactNode referenceNode = new FactExpressionVisitor(ids)
                .methodReferenceNode(reference, owner, workspace, resolution);
            relate(methodNode, referenceNode, FactEdgeType.REFERENCES, -1, "METHOD_REFERENCE", acc);
            if (resolved.isEmpty()) continue;
            ResolvedMethodDeclaration declaration = resolved.get();
            Optional<MethodDeclaration> target = resolver.resolveMethodDeclaration(declaration);
            if (target.isEmpty()) continue;
            String targetOwner = declaration.declaringType().getQualifiedName() + "." + declaration.getSignature();
            FactNode targetNode = state.methodNodes.get(targetOwner);
            if (targetNode == null) {
                targetNode = methodNode(target.get(), targetOwner,
                    FactExpressionVisitor.range(target.get(), workspace), false);
                state.methodNodes.put(targetOwner, targetNode);
            }
            relate(referenceNode, targetNode, FactEdgeType.REFERENCES, -1, "TARGET", acc);
            traverse(target.get(), targetNode, targetOwner, appBase, depth + 1, workspace, resolver, budget,
                acc, diagnostics, state, new LinkedHashSet<>(path));
        }
        for (ObjectCreationExpr creation : method.findAll(ObjectCreationExpr.class)) {
            SourceRange creationRange = FactExpressionVisitor.range(creation, workspace);
            FactNode creationNode = new FactNode(ids.generate(FactNodeType.OBJECT_CREATION, owner, creationRange, "new"),
                FactNodeType.OBJECT_CREATION, creationRange, creation.toString(), TypeResolution.notApplicable(),
                new FactNodePayload.ObjectCreationPayload(creation.getTypeAsString(), creation.getArguments().size()));
            relate(methodNode, creationNode, FactEdgeType.CREATES, -1, "OBJECT_CREATION", acc);
            new FactExpressionVisitor(ids).visitObjectCreationArguments(creation, creationNode, owner,
                workspace, acc, call -> resolver.resolveMethodCall(call)
                    .map(value -> TypeResolution.resolvedSignature(value.getQualifiedSignature()))
                    .orElseGet(() -> TypeResolution.unresolved("TYPE_RESOLUTION_FAILED")));
            Optional<com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration> resolvedCreation =
                resolver.resolveObjectCreation(creation);
            resolvedCreation.ifPresent(resolved ->
                resolver.resolveConstructorDeclaration(resolved).ifPresent(target -> {
                    String targetOwner = resolved.declaringType().getQualifiedName() + "." + resolved.getSignature();
                    FactNode targetNode = state.methodNodes.get(targetOwner);
                    if (targetNode == null) {
                        SourceRange targetRange = FactExpressionVisitor.range(target, workspace);
                        targetNode = constructorNode(target, targetOwner, targetRange);
                        state.methodNodes.put(targetOwner, targetNode);
                    }
                    relate(creationNode, targetNode, FactEdgeType.CALLS, -1, "CONSTRUCTOR", acc);
                    traverse(target, targetNode, targetOwner, appBase, depth + 1, workspace, resolver, budget,
                        acc, diagnostics, state, new LinkedHashSet<>(path));
                    bindArguments(creationNode, targetNode, acc);
                }));
            if (resolvedCreation.flatMap(resolver::resolveConstructorDeclaration).isEmpty())
                synthesizeLombokConstructor(creation, creationNode, owner, workspace, resolver, acc);
        }
        for (ExplicitConstructorInvocationStmt invocation :
                method.findAll(ExplicitConstructorInvocationStmt.class)) {
            resolver.resolveConstructorInvocation(invocation).ifPresent(resolved ->
                resolver.resolveConstructorDeclaration(resolved).ifPresent(target -> {
                    String targetOwner = resolved.declaringType().getQualifiedName() + "." + resolved.getSignature();
                    FactNode targetNode = state.methodNodes.get(targetOwner);
                    if (targetNode == null) {
                        targetNode = constructorNode(target, targetOwner,
                            FactExpressionVisitor.range(target, workspace));
                        state.methodNodes.put(targetOwner, targetNode);
                    }
                    relate(methodNode, targetNode, FactEdgeType.CALLS, -1,
                        invocation.isThis() ? "THIS_CONSTRUCTOR" : "SUPER_CONSTRUCTOR", acc);
                    traverse(target, targetNode, targetOwner, appBase, depth + 1, workspace, resolver, budget,
                        acc, diagnostics, state, new LinkedHashSet<>(path));
                }));
        }
        path.remove(owner);
    }

    private FactNode methodNode(MethodDeclaration method, String owner, SourceRange range, boolean api) { FactNodeType type = api ? FactNodeType.API_METHOD : FactNodeType.METHOD; return new FactNode(ids.generate(type, owner, range, api ? "api-root" : "method"), type, range, method.getDeclarationAsString(false, false, true), TypeResolution.resolvedSignature(owner), new FactNodePayload.MethodPayload(declaringOwner(owner, method.getNameAsString()), method.getSignature().asString(), api)); }
    private FactNode constructorNode(ConstructorDeclaration constructor, String owner, SourceRange range) {
        return new FactNode(ids.generate(FactNodeType.CONSTRUCTOR, owner, range, "constructor"),
            FactNodeType.CONSTRUCTOR, range, constructor.getDeclarationAsString(false, false, true),
            TypeResolution.resolvedSignature(owner),
            new FactNodePayload.ConstructorPayload(declaringOwner(owner, constructor.getNameAsString()),
                constructor.getSignature().asString()));
    }
    private void synthesizeLombokConstructor(ObjectCreationExpr creation, FactNode creationNode, String owner,
                                             Path workspace, TypeResolver resolver, FactGraphAccumulator acc) {
        resolver.resolveClassDeclaration(creation.getTypeAsString()).filter(type ->
            type.isAnnotationPresent("Value") || type.isAnnotationPresent("AllArgsConstructor")).ifPresent(type -> {
                List<com.github.javaparser.ast.body.VariableDeclarator> fields = type.getFields().stream()
                    .filter(field -> !field.isStatic()).flatMap(field -> field.getVariables().stream()).toList();
                if (fields.size() != creation.getArguments().size()) return;
                String typeOwner = resolver.qualifiedOwner(type);
                SourceRange range = FactExpressionVisitor.range(type, workspace);
                String signature = type.getNameAsString() + "(" + fields.stream()
                    .map(field -> field.getType().asString()).reduce((a, b) -> a + ", " + b).orElse("") + ")";
                FactNode constructor = new FactNode(ids.generate(FactNodeType.CONSTRUCTOR, typeOwner, range,
                    "lombok-constructor"), FactNodeType.CONSTRUCTOR, range, signature,
                    TypeResolution.unresolved("LOMBOK_GENERATED_CONSTRUCTOR"),
                    new FactNodePayload.ConstructorPayload(typeOwner, signature));
                relate(creationNode, constructor, FactEdgeType.CALLS, -1, "LOMBOK_CONSTRUCTOR", acc);
                for (int i = 0; i < fields.size(); i++) {
                    var field = fields.get(i); SourceRange fieldRange = FactExpressionVisitor.range(field, workspace);
                    FactNode valueField = new FactNode(ids.generate(FactNodeType.VALUE_FIELD, typeOwner, fieldRange,
                        "lombok-field:" + field.getNameAsString()), FactNodeType.VALUE_FIELD, fieldRange,
                        field.getNameAsString(), TypeResolution.unresolved("DECLARED_FIELD"),
                        new FactNodePayload.FieldAccessPayload(field.getNameAsString(), "LOMBOK_FIELD:" + typeOwner));
                    FactNode argument = operand(acc, creationNode.id(), i);
                    if (argument != null) relate(argument, valueField, FactEdgeType.VALUE_FLOWS_TO, i,
                        "LOMBOK_CONSTRUCTOR_ARGUMENT", acc);
                }
            });
    }
    private FactNode operand(FactGraphAccumulator acc, String sourceId, int ordinal) {
        String targetId = acc.edges().stream().filter(edge -> edge.sourceNodeId().equals(sourceId)
                && edge.type() == FactEdgeType.OPERAND_OF && edge.ordinal() == ordinal
                && "ARGUMENT".equals(edge.role())).map(FactEdge::targetNodeId).findFirst().orElse(null);
        return targetId == null ? null : acc.nodes().stream().filter(node -> node.id().equals(targetId)).findFirst().orElse(null);
    }
    private String declaringOwner(String qualifiedSignature, String callableName) {
        int boundary = qualifiedSignature.indexOf("." + callableName + "(");
        return boundary > 0 ? qualifiedSignature.substring(0, boundary) : qualifiedSignature;
    }
    private void relate(FactNode a, FactNode b, FactEdgeType type, int ordinal, String role, FactGraphAccumulator acc) { acc.addRelation(a, b, new FactEdge(ids.edgeId(a.id(), b.id(), type.name(), ordinal, role), a.id(), b.id(), type, ordinal, role)); }
    private void bindArguments(FactNode invocation, FactNode target, FactGraphAccumulator acc) {
        Map<Integer, FactNode> parameters = new HashMap<>();
        for (FactEdge edge : acc.edges()) if (edge.sourceNodeId().equals(target.id())
                && edge.type() == FactEdgeType.ORIGINATES_FROM && edge.ordinal() >= 0) {
            acc.nodes().stream().filter(node -> node.id().equals(edge.targetNodeId())
                && node.type() == FactNodeType.PARAMETER).findFirst()
                .ifPresent(node -> parameters.put(edge.ordinal(), node));
        }
        for (FactEdge edge : acc.edges()) if (edge.sourceNodeId().equals(invocation.id())
                && edge.type() == FactEdgeType.OPERAND_OF && "ARGUMENT".equals(edge.role())) {
            FactNode parameter = parameters.get(edge.ordinal());
            if (parameter == null) continue;
            acc.nodes().stream().filter(node -> node.id().equals(edge.targetNodeId())).findFirst()
                .ifPresent(argument -> {
                    relate(parameter, argument, FactEdgeType.ORIGINATES_FROM,
                        edge.ordinal(), "CALL_ARGUMENT", acc);
                    if (target.type() == FactNodeType.CONSTRUCTOR
                            && parameter.payload() instanceof FactNodePayload.ParameterPayload payload
                            && target.payload() instanceof FactNodePayload.ConstructorPayload constructor) {
                        FactNode field = new FactNode(ids.generate(FactNodeType.VALUE_FIELD, target.id(),
                            parameter.sourceRange(), "constructor-field:" + payload.name()),
                            FactNodeType.VALUE_FIELD, parameter.sourceRange(), payload.name(),
                            TypeResolution.notApplicable(),
                            new FactNodePayload.FieldAccessPayload(payload.name(),
                                "CONSTRUCTOR_PARAMETER:" + constructor.owner()));
                        relate(argument, field, FactEdgeType.VALUE_FLOWS_TO, edge.ordinal(),
                            "CONSTRUCTOR_ARGUMENT", acc);
                    }
                });
        }
        FactNode receiver = acc.edges().stream().filter(edge -> edge.sourceNodeId().equals(invocation.id())
                && edge.type() == FactEdgeType.OPERAND_OF && "RECEIVER".equals(edge.role()))
            .map(FactEdge::targetNodeId).map(id -> acc.nodes().stream()
                .filter(node -> node.id().equals(id)).findFirst().orElse(null))
            .filter(Objects::nonNull).findFirst().orElse(null);
        if (receiver != null) acc.nodes().stream().filter(node -> node.type() == FactNodeType.VALUE_FIELD
                && node.sourceRange().relativePath().equals(target.sourceRange().relativePath())
                && node.payload() instanceof FactNodePayload.FieldAccessPayload field
                && field.rootExpressionKind().startsWith("INSTANCE_FIELD:"))
            .forEach(field -> relate(receiver, field, FactEdgeType.VALUE_FLOWS_TO, -1,
                "RECEIVER_FIELD", acc));
    }
    private String appBase(String controller) {
        for (String marker : List.of(".controller.", ".api.", ".web.")) {
            int i = controller.indexOf(marker);
            if (i > 0) return controller.substring(0, i);
        }
        String[] segments = controller.split("\\.");
        if (segments.length >= 2) return segments[0] + "." + segments[1];
        int i = controller.lastIndexOf('.');
        return i > 0 ? controller.substring(0, i) : "";
    }
    private FactGraphTraversalStats stats(TraversalState state, FactGraphAccumulator acc) { return new FactGraphTraversalStats(state.maxDepth, state.visited.size(), acc.edgeCount()); }
    private FactCodeGraph augmentFailureMappings(FactCodeGraph graph, List<Path> roots, Path workspace) {
        Map<String, ExceptionHandlerFactScanner.HandlerFact> handlers =
            new ExceptionHandlerFactScanner().scan(roots, workspace);
        Map<String, FactNode> nodes = new LinkedHashMap<>();
        graph.nodes().forEach(node -> nodes.put(node.id(), node));
        Map<String, FactEdge> edges = new LinkedHashMap<>();
        graph.edges().forEach(edge -> edges.put(edge.id(), edge));
        for (FactNode thrown : graph.nodes()) {
            if (thrown.type() != FactNodeType.THROW) continue;
            String exceptionType = thrownExceptionType(thrown.snippet());
            if (exceptionType == null) continue;
            FactNode exception = new FactNode(ids.generate(FactNodeType.EXCEPTION, exceptionType,
                thrown.sourceRange(), "exception"), FactNodeType.EXCEPTION, thrown.sourceRange(), exceptionType,
                TypeResolution.notApplicable(), new FactNodePayload.ExceptionPayload(exceptionType, false));
            nodes.putIfAbsent(exception.id(), exception);
            addEdge(edges, thrown, exception, FactEdgeType.THROWS, "EXCEPTION");
            ExceptionHandlerFactScanner.HandlerFact handlerFact = handlers.get(exceptionType);
            if (handlerFact == null) continue;
            FactNode handler = new FactNode(ids.generate(FactNodeType.EXCEPTION_HANDLER, exceptionType,
                handlerFact.range(), "handler"), FactNodeType.EXCEPTION_HANDLER, handlerFact.range(),
                handlerFact.snippet(), TypeResolution.notApplicable(),
                new FactNodePayload.ExceptionPayload(exceptionType, true));
            nodes.putIfAbsent(handler.id(), handler);
            addEdge(edges, exception, handler, FactEdgeType.HANDLED_BY, "HANDLER");
            if (handlerFact.status() == null) continue;
            FactNode status = new FactNode(ids.generate(FactNodeType.HTTP_STATUS,
                String.valueOf(handlerFact.status().code()), handlerFact.range(), "http-status"),
                FactNodeType.HTTP_STATUS, handlerFact.range(), String.valueOf(handlerFact.status().code()),
                TypeResolution.notApplicable(), new FactNodePayload.HttpStatusPayload(
                    handlerFact.status().code(), handlerFact.status().name()));
            nodes.putIfAbsent(status.id(), status);
            addEdge(edges, handler, status, FactEdgeType.MAPS_TO, "HTTP_STATUS");
        }
        return new FactCodeGraph(graph.graphId(), graph.apiMethodNodeId(),
            List.copyOf(nodes.values()), List.copyOf(edges.values()));
    }
    private String thrownExceptionType(String snippet) {
        int start = snippet.indexOf("new ");
        if (start < 0) return null;
        start += 4;
        int end = snippet.indexOf('(', start);
        if (end < 0) end = snippet.length();
        String value = snippet.substring(start, end).trim();
        int dot = value.lastIndexOf('.');
        return dot < 0 ? value : value.substring(dot + 1);
    }
    private void addEdge(Map<String, FactEdge> edges, FactNode source, FactNode target,
                         FactEdgeType type, String role) {
        FactEdge edge = new FactEdge(ids.edgeId(source.id(), target.id(), type.name(), -1, role),
            source.id(), target.id(), type, -1, role);
        edges.putIfAbsent(edge.id(), edge);
    }
    private FactGraphDiagnostic diagnostic(String api, String reason, boolean truncated, FactGraphTraversalBudget budget, FactGraphTraversalStats stats, SourceRange range, String details) { return new FactGraphDiagnostic(api, truncated ? DiagnosticSeverity.WARNING : DiagnosticSeverity.ERROR, reason, truncated, budget, stats, range, details); }
    private static final class TraversalState { private int maxDepth; private final Set<String> visited = new LinkedHashSet<>(); private final Map<String, FactNode> methodNodes = new LinkedHashMap<>(); }
}
