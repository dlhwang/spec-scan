package io.specscan.graph;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import io.specscan.graph.CodeGraph.EdgeKind;
import io.specscan.graph.CodeGraph.NodeKind;
import io.specscan.parse.ProjectIndex;

/** Builds the code graph: type/member declarations plus best-effort call edges. */
public final class GraphBuilder {

    private GraphBuilder() {
    }

    public static CodeGraph build(ProjectIndex index) {
        CodeGraph g = new CodeGraph();

        for (CompilationUnit cu : index.units) {
            String file = cu.getStorage().map(s -> s.getPath().toString()).orElse(null);
            for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                String typeId = type.getFullyQualifiedName().orElse(type.getNameAsString());
                g.addNode(typeId, kindOf(type), type.getNameAsString(), file, line(type));

                type.getFields().forEach(f -> addField(g, typeId, f, file));
                type.getMethods().forEach(m -> addCallable(g, typeId, methodId(typeId, m), NodeKind.METHOD,
                        m.getNameAsString(), m, file));
                if (type instanceof ClassOrInterfaceDeclaration cid) {
                    for (ConstructorDeclaration c : cid.getConstructors()) {
                        addCallable(g, typeId, ctorId(typeId, c), NodeKind.CONSTRUCTOR, "<init>", c, file);
                    }
                }
            }
        }

        // Structural + call edges in a second pass so all targets exist.
        for (CompilationUnit cu : index.units) {
            for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                String typeId = type.getFullyQualifiedName().orElse(type.getNameAsString());
                if (type instanceof ClassOrInterfaceDeclaration cid) {
                    cid.getExtendedTypes().forEach(t ->
                            resolveTypeId(index, t.getName().getIdentifier())
                                    .ifPresent(target -> g.addEdge(typeId, target, EdgeKind.EXTENDS)));
                    cid.getImplementedTypes().forEach(t ->
                            resolveTypeId(index, t.getName().getIdentifier())
                                    .ifPresent(target -> g.addEdge(typeId, target, EdgeKind.IMPLEMENTS)));
                }
                type.getMethods().forEach(m -> addCallEdges(index, g, methodId(typeId, m), m));
                if (type instanceof ClassOrInterfaceDeclaration cid) {
                    cid.getConstructors().forEach(c -> addCallEdges(index, g, ctorId(typeId, c), c));
                }
            }
        }
        return g;
    }

    private static void addField(CodeGraph g, String typeId, FieldDeclaration f, String file) {
        f.getVariables().forEach(v -> {
            String id = typeId + "#" + v.getNameAsString();
            g.addNode(id, NodeKind.FIELD, v.getNameAsString(), file, line(f));
            g.addEdge(typeId, id, EdgeKind.DECLARES);
        });
    }

    private static void addCallable(CodeGraph g, String typeId, String id, NodeKind kind,
                                    String label, com.github.javaparser.ast.Node node, String file) {
        g.addNode(id, kind, label, file, line(node));
        g.addEdge(typeId, id, EdgeKind.DECLARES);
    }

    private static void addCallEdges(ProjectIndex index, CodeGraph g, String callerId,
                                     com.github.javaparser.ast.Node body) {
        body.findAll(MethodCallExpr.class).forEach(call -> {
            try {
                var resolved = call.resolve();
                String targetType = resolved.declaringType().getQualifiedName();
                if (index.byQualifiedName(targetType).isPresent()) {
                    String targetId = targetType + "." + resolved.getName() + "/" + resolved.getNumberOfParams();
                    g.addEdge(callerId, targetId, EdgeKind.CALLS);
                }
            } catch (RuntimeException ignored) {
                // unresolved (framework types, lombok accessors) — skip
            }
        });
        body.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String simple = creation.getType().getName().getIdentifier();
            resolveTypeId(index, simple).ifPresent(target -> g.addEdge(callerId, target, EdgeKind.CREATES));
        });
    }

    private static java.util.Optional<String> resolveTypeId(ProjectIndex index, String simpleName) {
        return index.bySimpleName(simpleName).flatMap(TypeDeclaration::getFullyQualifiedName);
    }

    public static String methodId(String typeId, MethodDeclaration m) {
        return typeId + "." + m.getNameAsString() + "/" + m.getParameters().size();
    }

    private static String ctorId(String typeId, ConstructorDeclaration c) {
        return typeId + ".<init>/" + c.getParameters().size();
    }

    private static NodeKind kindOf(TypeDeclaration<?> t) {
        if (t instanceof EnumDeclaration) return NodeKind.ENUM;
        if (t instanceof RecordDeclaration) return NodeKind.RECORD;
        if (t instanceof ClassOrInterfaceDeclaration c && c.isInterface()) return NodeKind.INTERFACE;
        return NodeKind.CLASS;
    }

    private static Integer line(com.github.javaparser.ast.Node n) {
        return n.getRange().map(r -> r.begin.line).orElse(null);
    }
}
