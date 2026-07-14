package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
        for (ApiEndpoint endpoint : scan.endpoints()) buildEndpoint(endpoint, workspace, resolver, budget, graphs, diagnostics);
        return new FactGraphBuildResult(graphs, diagnostics);
    }

    private void buildEndpoint(ApiEndpoint endpoint, Path workspace, TypeResolver resolver, FactGraphTraversalBudget budget, List<FactCodeGraph> graphs, List<FactGraphDiagnostic> diagnostics) {
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
        FactCodeGraph graph = acc.snapshot("FACT_GRAPH:" + rootNode.id(), rootNode.id()); validator.validate(graph, budget, stats); graphs.add(graph);
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

    private void traverse(MethodDeclaration method, FactNode methodNode, String owner, String appBase, int depth, Path workspace, TypeResolver resolver, FactGraphTraversalBudget budget, FactGraphAccumulator acc, List<FactGraphDiagnostic> diagnostics, TraversalState state, Set<String> path) {
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
            relate(methodNode, callNode, FactEdgeType.CALLS, -1, "CALL", acc);
            if (resolved.isEmpty()) continue;
            ResolvedMethodDeclaration declaration = resolved.get(); String targetOwner = declaration.declaringType().getQualifiedName() + "." + declaration.getSignature();
            Optional<MethodDeclaration> target = resolver.resolveMethodDeclaration(declaration);
            TraversalDecision decision = policy.decide(declaration.declaringType().getQualifiedName(), declaration.getName(), target.isPresent(), appBase);
            if (decision != TraversalDecision.VISIT_BODY || target.isEmpty()) continue;
            FactNode existingTarget = state.methodNodes.get(targetOwner);
            if (existingTarget != null) { relate(callNode, existingTarget, FactEdgeType.CALLS, -1, "TARGET", acc); continue; }
            SourceRange targetRange = FactExpressionVisitor.range(target.get(), workspace); FactNode targetNode = methodNode(target.get(), targetOwner, targetRange, false);
            state.methodNodes.put(targetOwner, targetNode);
            relate(callNode, targetNode, FactEdgeType.CALLS, -1, "TARGET", acc);
            traverse(target.get(), targetNode, targetOwner, appBase, depth + 1, workspace, resolver, budget, acc, diagnostics, state, new LinkedHashSet<>(path));
        }
        path.remove(owner);
    }

    private FactNode methodNode(MethodDeclaration method, String owner, SourceRange range, boolean api) { FactNodeType type = api ? FactNodeType.API_METHOD : FactNodeType.METHOD; return new FactNode(ids.generate(type, owner, range, api ? "api-root" : "method"), type, range, method.getDeclarationAsString(false, false, true), TypeResolution.resolvedSignature(owner), new FactNodePayload.MethodPayload(owner.substring(0, Math.max(0, owner.lastIndexOf('.'))), method.getSignature().asString(), api)); }
    private void relate(FactNode a, FactNode b, FactEdgeType type, int ordinal, String role, FactGraphAccumulator acc) { acc.addRelation(a, b, new FactEdge(ids.edgeId(a.id(), b.id(), type.name(), ordinal, role), a.id(), b.id(), type, ordinal, role)); }
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
    private FactGraphDiagnostic diagnostic(String api, String reason, boolean truncated, FactGraphTraversalBudget budget, FactGraphTraversalStats stats, SourceRange range, String details) { return new FactGraphDiagnostic(api, truncated ? DiagnosticSeverity.WARNING : DiagnosticSeverity.ERROR, reason, truncated, budget, stats, range, details); }
    private static final class TraversalState { private int maxDepth; private final Set<String> visited = new LinkedHashSet<>(); private final Map<String, FactNode> methodNodes = new LinkedHashMap<>(); }
}
