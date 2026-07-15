package io.atworks.specscan.analysis.support.fact;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import io.atworks.specscan.analysis.domain.fact.SourceRange;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

final class ExceptionHandlerFactScanner {
    Map<String, HandlerFact> scan(List<Path> roots, Path workspace) {
        Map<String, HandlerFact> result = new LinkedHashMap<>();
        for (Path root : roots) try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                .sorted().forEach(path -> scanFile(path, workspace, result));
        } catch (Exception ignored) { }
        return result;
    }

    private void scanFile(Path file, Path workspace, Map<String, HandlerFact> result) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(file);
            for (MethodDeclaration method : unit.findAll(MethodDeclaration.class)) {
                method.getAnnotationByName("ExceptionHandler").ifPresent(annotation -> {
                    String type = handledType(annotation, method);
                    if (type != null) result.putIfAbsent(type,
                        new HandlerFact(type, status(method), FactExpressionVisitor.range(method, workspace),
                            method.toString()));
                });
            }
        } catch (Exception ignored) { }
    }

    private String handledType(AnnotationExpr annotation, MethodDeclaration method) {
        String text = annotation.toString();
        int classSuffix = text.indexOf(".class");
        if (classSuffix >= 0) {
            int start = Math.max(text.lastIndexOf('{', classSuffix), text.lastIndexOf('(', classSuffix)) + 1;
            String value = text.substring(start, classSuffix).trim();
            int dot = value.lastIndexOf('.');
            return dot < 0 ? value : value.substring(dot + 1);
        }
        return method.getParameters().isEmpty() ? null : method.getParameter(0).getType().getElementType().asString();
    }

    private StatusFact status(MethodDeclaration method) {
        String text = method.toString();
        for (Map.Entry<String, Integer> status : statuses().entrySet()) {
            if (text.contains("HttpStatus." + status.getKey()) || text.contains("ErrorCode." + status.getKey()))
                return new StatusFact(status.getValue(), status.getKey());
        }
        return null;
    }

    private Map<String, Integer> statuses() {
        return Map.of("BAD_REQUEST", 400, "UNAUTHORIZED", 401, "FORBIDDEN", 403,
            "ACCESS_DENIED", 403, "NOT_FOUND", 404, "DATA_NOT_FOUND", 404,
            "CONFLICT", 409, "INTERNAL_SERVER_ERROR", 500);
    }

    record HandlerFact(String exceptionType, StatusFact status, SourceRange range, String snippet) {}
    record StatusFact(int code, String name) {}
}
