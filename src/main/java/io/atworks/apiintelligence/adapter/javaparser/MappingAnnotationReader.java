package io.atworks.apiintelligence.adapter.javaparser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MappingAnnotationReader {

    record Mapping(String method, String path) {

    }

    static String path(NodeList<AnnotationExpr> annotations) {
        return annotations.stream().filter(
                a -> a.getNameAsString().equals("RequestMapping") || a.getNameAsString()
                    .endsWith(".RequestMapping"))
            .findFirst().map(MappingAnnotationReader::pathsFor).orElse(List.of("")).stream()
            .findFirst().orElse("");
    }

    static List<String> paths(NodeList<AnnotationExpr> annotations) {
        return annotations.stream().filter(MappingAnnotationReader::isMapping)
            .flatMap(a -> pathsFor(a).stream()).toList();
    }

    static List<Mapping> expand(NodeList<AnnotationExpr> annotations, List<String> paths) {
        List<Mapping> result = new ArrayList<>();
        for (AnnotationExpr a : annotations) {
            String qualifiedName = a.getNameAsString();
            String name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
            String method = switch (name) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "PatchMapping" -> "PATCH";
                case "DeleteMapping" -> "DELETE";
                default -> null;
            };
            if (method != null) {
                for (String path : pathsFor(a)) {
                    result.add(new Mapping(method, path));
                }
            }
            if (name.equals("RequestMapping")) {
                List<String> methods = values(a, "method");
                for (String m : methods.isEmpty() ? List.of("GET") : methods) {
                    for (String path : pathsFor(a)) {
                        result.add(new Mapping(stripEnum(m), path));
                    }
                }
            }
        }
        return result;
    }

    private static boolean isMapping(AnnotationExpr a) {
        return Set.of("RequestMapping", "GetMapping", "PostMapping", "PutMapping", "PatchMapping",
                "DeleteMapping").stream()
            .anyMatch(n -> a.getNameAsString().equals(n) || a.getNameAsString().endsWith("." + n));
    }

    private static List<String> pathsFor(AnnotationExpr a) {
        if (a instanceof MarkerAnnotationExpr) {
            return List.of("");
        }
        if (a instanceof SingleMemberAnnotationExpr s) {
            return strings(s.getMemberValue());
        }
        if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair p : n.getPairs()) {
                if (p.getNameAsString().equals("value") || p.getNameAsString().equals("path")) {
                    return strings(p.getValue());
                }
            }
        }
        return List.of("");
    }

    private static List<String> values(AnnotationExpr a, String key) {
        if (!(a instanceof NormalAnnotationExpr n)) {
            return List.of();
        }
        for (MemberValuePair p : n.getPairs()) {
            if (p.getNameAsString().equals(key)) {
                return strings(p.getValue());
            }
        }
        return List.of();
    }

    private static List<String> strings(Expression e) {
        if (e instanceof ArrayInitializerExpr a) {
            return a.getValues().stream().map(MappingAnnotationReader::string).toList();
        }
        return List.of(string(e));
    }

    private static String string(Expression e) {
        return e.isStringLiteralExpr() ? e.asStringLiteralExpr().asString() : e.toString();
    }

    private static String stripEnum(String value) {
        int dot = value.lastIndexOf('.');
        return value.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
}
