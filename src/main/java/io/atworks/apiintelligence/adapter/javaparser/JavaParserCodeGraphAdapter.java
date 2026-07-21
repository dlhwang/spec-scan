package io.atworks.apiintelligence.adapter.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.atworks.apiintelligence.config.ApiIntelligenceConfiguration.GraphSettings;
import io.atworks.apiintelligence.domain.api.DiscoveredApi;
import io.atworks.apiintelligence.domain.diagnostic.Diagnostic;
import io.atworks.apiintelligence.domain.graph.CodeEdge;
import io.atworks.apiintelligence.domain.graph.CodeEdgeKind;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.graph.CodeNode;
import io.atworks.apiintelligence.domain.graph.CodeNodeKind;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import io.atworks.apiintelligence.port.out.CodeGraphPort;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JavaParserCodeGraphAdapter implements CodeGraphPort {

    @Override
    public CodeGraph build(DiscoveredApi api, SourceWorkspace workspace, GraphSettings limits) {
        List<CodeNode> nodes = new ArrayList<>();
        List<CodeEdge> edges = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        String apiNode = "api:" + api.apiId();
        String methodNode = "method:" + api.apiId();
        nodes.add(new CodeNode(apiNode, CodeNodeKind.API, api.path(), api.sourceLocation(),
            Map.of("method", api.httpMethod())));
        nodes.add(new CodeNode(methodNode, CodeNodeKind.METHOD, api.handlerSignature(),
            api.sourceLocation(), Map.of()));
        edges.add(
            new CodeEdge("declares:" + api.apiId(), CodeEdgeKind.DECLARES, apiNode, methodNode,
                api.sourceLocation()));
        if (nodes.size() > limits.maxMethods()) {
            diagnostics.add(
                new Diagnostic("GRAPH_TRUNCATED", "GRAPHING", "method limit reached", false));
            return new CodeGraph(api.apiId(), nodes, edges, diagnostics);
        }
        try {
            Path file = workspace.root().resolve(api.sourceLocation().relativePath());
            MethodDeclaration method = StaticJavaParser.parse(file).findAll(MethodDeclaration.class)
                .stream()
                .filter(m -> m.getDeclarationAsString(false, false, false)
                    .equals(api.handlerSignature())).findFirst().orElse(null);
            if (method == null || method.getBody().isEmpty()) {
                return new CodeGraph(api.apiId(), nodes, edges, diagnostics);
            }
            for (IfStmt statement : method.findAll(IfStmt.class)) {
                addCondition(nodes, edges, statement.getCondition().toString(), methodNode,
                    statement, CodeEdgeKind.CHECKS, limits);
            }
            for (SwitchStmt statement : method.findAll(SwitchStmt.class)) {
                addCondition(nodes, edges, statement.getSelector().toString(), methodNode,
                    statement, CodeEdgeKind.CHECKS, limits);
            }
            for (ThrowStmt statement : method.findAll(ThrowStmt.class)) {
                addNode(nodes, edges, CodeNodeKind.EXCEPTION, statement.getExpression().toString(),
                    methodNode, statement, CodeEdgeKind.THROWS, limits);
            }
            for (ReturnStmt statement : method.findAll(ReturnStmt.class)) {
                addNode(nodes, edges, CodeNodeKind.FIELD,
                    statement.getExpression().map(Object::toString).orElse("return"), methodNode,
                    statement, CodeEdgeKind.RETURNS, limits);
            }
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                addNode(nodes, edges, CodeNodeKind.METHOD, call.getNameAsString(), methodNode, call,
                    CodeEdgeKind.CALLS, limits);
            }
        } catch (IOException | RuntimeException e) {
            diagnostics.add(
                new Diagnostic("GRAPH_BUILD_FAILED", "GRAPHING", "Unable to parse handler source",
                    false));
        }
        return new CodeGraph(api.apiId(), nodes, edges, diagnostics);
    }

    private static void addCondition(List<CodeNode> n, List<CodeEdge> e, String name, String parent,
        com.github.javaparser.ast.Node source, CodeEdgeKind kind, GraphSettings l) {
        addNode(n, e, CodeNodeKind.CONDITION, name, parent, source, kind, l);
    }

    private static void addNode(List<CodeNode> n, List<CodeEdge> e, CodeNodeKind k, String name,
        String parent, com.github.javaparser.ast.Node source, CodeEdgeKind edge, GraphSettings l) {
        if (n.size() >= l.maxEdges()) {
            return;
        }
        String id =
            k.name().toLowerCase() + ":" + name + ":" + source.getRange().map(r -> r.begin.line)
                .orElse(0);
        SourceLocation loc = source.getRange().map(
            r -> new SourceLocation("unknown", r.begin.line, r.begin.column, r.end.line,
                r.end.column)).orElse(null);
        if (n.stream().noneMatch(x -> x.id().equals(id))) {
            n.add(new CodeNode(id, k, name, loc, Map.of()));
            e.add(new CodeEdge("edge:" + id, edge, parent, id, loc));
        }
    }
}
