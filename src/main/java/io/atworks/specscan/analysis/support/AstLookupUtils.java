package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class AstLookupUtils {

    private AstLookupUtils() {
    }

    public static String findFieldType(ClassOrInterfaceDeclaration clazz, String fieldName) {
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(fieldName))) {
                return field.getElementType().asString();
            }
        }
        return null;
    }

    public static Path getFilePath(TypeDeclaration<?> typeDecl, Path fallbackPath) {
        if (typeDecl.findCompilationUnit().isPresent() && typeDecl.findCompilationUnit().get().getStorage().isPresent()) {
            return typeDecl.findCompilationUnit().get().getStorage().get().getPath();
        }
        return fallbackPath;
    }

    public static Optional<TypeDeclaration<?>> findTypeInSourceRoots(String typeName, List<Path> sourceRoots) {
        String finalSimpleName = toSimpleTypeName(typeName);

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
            } catch (IOException ignored) {
            }
        }
        return Optional.empty();
    }

    public static Map<String, String> collectAccessibleTypes(
        ClassOrInterfaceDeclaration ownerDecl,
        MethodDeclaration method
    ) {
        Map<String, String> types = new HashMap<>();
        for (FieldDeclaration field : ownerDecl.getFields()) {
            field.getVariables().forEach(variable -> types.put(variable.getNameAsString(), variable.getType().asString()));
        }
        method.getParameters().forEach(parameter -> types.put(parameter.getNameAsString(), parameter.getType().asString()));
        method.findAll(VariableDeclarator.class).forEach(variable -> types.put(variable.getNameAsString(), variable.getType().asString()));
        return types;
    }

    public static Optional<String> resolveCalledType(
        MethodCallExpr call,
        ClassOrInterfaceDeclaration ownerDecl,
        String ownerType,
        Map<String, String> accessibleTypes
    ) {
        if (call.getScope().isEmpty()) {
            return ownerDecl.getMethodsByName(call.getNameAsString()).isEmpty()
                ? Optional.empty()
                : Optional.of(ownerType);
        }

        Expression scope = call.getScope().get();
        if (scope instanceof ThisExpr) {
            return Optional.of(ownerType);
        }
        if (scope instanceof NameExpr nameExpr) {
            return Optional.ofNullable(accessibleTypes.get(nameExpr.getNameAsString()));
        }
        if (scope instanceof FieldAccessExpr fieldAccessExpr && fieldAccessExpr.getScope() instanceof ThisExpr) {
            return Optional.ofNullable(accessibleTypes.get(fieldAccessExpr.getNameAsString()));
        }
        return Optional.empty();
    }

    public static String resolveThrownExceptionName(ThrowStmt throwStmt) {
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

    private static String toSimpleTypeName(String typeName) {
        int angleIdx = typeName.indexOf('<');
        if (angleIdx != -1) {
            typeName = typeName.substring(0, angleIdx);
        }
        int dotIdx = typeName.lastIndexOf('.');
        if (dotIdx != -1) {
            typeName = typeName.substring(dotIdx + 1);
        }
        return typeName.trim();
    }
}
