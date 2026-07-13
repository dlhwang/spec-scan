package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ServiceHintExtractor {
    private static final Pattern INDEX_PATTERN = Pattern.compile("\\[\\d+\\]");
    private static final Pattern GETTER_PATTERN = Pattern.compile("\\.get([A-Z][A-Za-z0-9_]*)\\(\\)");

    private final Path workspaceRoot;
    private final List<Path> sourceRoots;

    public ServiceHintExtractor(Path workspaceRoot, List<Path> sourceRoots) {
        this.workspaceRoot = workspaceRoot;
        this.sourceRoots = sourceRoots;
    }

    /**
     * 특정 서비스 클래스의 메서드 내부에 정의된 비즈니스 예외 던지기(if-throw) 제약과
     * 서비스가 호출하는 검증 보조 메서드/검증기 내부의 명시적 validation signal을 추출합니다.
     */
    public List<ValidationCandidate> extractFromServiceMethod(String serviceClassName, String methodName) {
        List<ValidationCandidate> list = new ArrayList<>();
        findTypeInSourceRoots(serviceClassName).ifPresent(typeDecl -> {
            if (typeDecl instanceof ClassOrInterfaceDeclaration classDecl) {
                Set<String> visitedMethods = new LinkedHashSet<>();
                classDecl.getMethodsByName(methodName)
                    .forEach(method -> collectCandidates(method, classDecl, visitedMethods, list));
            }
        });
        return list;
    }

    private void collectCandidates(
        MethodDeclaration method,
        ClassOrInterfaceDeclaration ownerClass,
        Set<String> visitedMethods,
        List<ValidationCandidate> list
    ) {
        String visitKey = ownerClass.getFullyQualifiedName().orElse(ownerClass.getNameAsString()) + "#" + method.getSignature().asString();
        if (!visitedMethods.add(visitKey)) {
            return;
        }

        method.findAll(IfStmt.class).forEach(ifStmt -> {
            boolean hasThrow = ifStmt.findFirst(ThrowStmt.class).isPresent();
            if (!hasThrow) {
                return;
            }
            String candidateId = "cand-svc-" + UUID.randomUUID().toString().substring(0, 8);
            String evidenceSnippet = ifStmt.toString().trim();
            String targetPath = inferTargetPathFromCondition(ifStmt.getCondition().toString());
            SourceTrace trace = SourceTraceResolver.resolve(ifStmt, workspaceRoot, getFilePath(ownerClass));
            list.add(new ValidationCandidate(
                candidateId,
                "SERVICE_HINT",
                targetPath,
                evidenceSnippet,
                0.5,
                trace
            ));
        });

        method.findAll(MethodCallExpr.class).forEach(call -> {
            extractValidatorSignal(call).ifPresent(list::add);
            extractOptionalLookupSignal(method, ownerClass, call).ifPresent(list::add);

            String calledMethod = call.getNameAsString();
            if (call.getScope().isEmpty()) {
                ownerClass.getMethodsByName(calledMethod)
                    .forEach(nested -> collectCandidates(nested, ownerClass, visitedMethods, list));
                return;
            }

            Expression scope = call.getScope().orElseThrow();
            String scopeText = scope.toString().trim();
            if (!scopeText.startsWith("new ")) {
                return;
            }

            String nestedClassName = extractConstructedTypeName(scopeText);
            if (nestedClassName == null || nestedClassName.isBlank()) {
                return;
            }

            findTypeInSourceRoots(nestedClassName).ifPresent(typeDecl -> {
                if (typeDecl instanceof ClassOrInterfaceDeclaration nestedClassDecl) {
                    nestedClassDecl.getMethodsByName(calledMethod)
                        .forEach(nested -> collectCandidates(nested, nestedClassDecl, visitedMethods, list));
                }
            });
        });
    }

    private Optional<ValidationCandidate> extractValidatorSignal(MethodCallExpr call) {
        String callName = call.getNameAsString();
        String fieldName = null;
        String code = null;

        if (callName.equals("of") && call.getScope().map(Expression::toString).orElse("").endsWith("ValidationError")) {
            if (call.getArguments().size() >= 2) {
                fieldName = cleanStringLiteral(call.getArgument(0).toString());
                code = cleanStringLiteral(call.getArgument(1).toString());
            }
        } else if (callName.equals("rejectValue") && call.getArguments().size() >= 2) {
            fieldName = cleanStringLiteral(call.getArgument(0).toString());
            code = cleanStringLiteral(call.getArgument(1).toString());
        } else if (callName.equals("rejectIfEmptyOrWhitespace") && call.getArguments().size() >= 3) {
            fieldName = cleanStringLiteral(call.getArgument(1).toString());
            code = cleanStringLiteral(call.getArgument(2).toString());
        }

        if (fieldName == null || fieldName.isBlank()) {
            return Optional.empty();
        }

        String candidateId = "cand-val-" + UUID.randomUUID().toString().substring(0, 8);
        String evidenceSnippet = call.toString();
        SourceTrace trace = SourceTraceResolver.resolve(call, workspaceRoot, getFilePath(call.findAncestor(TypeDeclaration.class).orElse(null)));
        String normalizedTarget = normalizeValidatorTarget(fieldName);
        double confidence = "nonPositive".equals(code) ? 0.9 : 1.0;

        return Optional.of(new ValidationCandidate(
            candidateId,
            "VALIDATOR",
            normalizedTarget,
            evidenceSnippet,
            confidence,
            trace
        ));
    }

    private Optional<ValidationCandidate> extractOptionalLookupSignal(
        MethodDeclaration method,
        ClassOrInterfaceDeclaration ownerClass,
        MethodCallExpr call
    ) {
        if (!call.getNameAsString().equals("orElseThrow")) {
            return Optional.empty();
        }

        String targetPath = resolveOptionalLookupTarget(method, call);
        if (targetPath == null || targetPath.isBlank() || targetPath.equals("unknown")) {
            return Optional.empty();
        }

        String candidateId = "cand-svc-" + UUID.randomUUID().toString().substring(0, 8);
        String evidenceSnippet = resolveOptionalLookupSource(method, call)
            .map(source -> source.toString() + " -> " + call)
            .orElse(call.toString());
        SourceTrace trace = SourceTraceResolver.resolve(call, workspaceRoot, getFilePath(ownerClass));
        return Optional.of(new ValidationCandidate(
            candidateId,
            "SERVICE_HINT",
            targetPath,
            evidenceSnippet,
            0.6,
            trace
        ));
    }

    private String resolveOptionalLookupTarget(MethodDeclaration method, MethodCallExpr call) {
        return resolveOptionalLookupSource(method, call)
            .map(this::inferTargetFromLookupCall)
            .orElse("");
    }

    private Optional<MethodCallExpr> resolveOptionalLookupSource(MethodDeclaration method, MethodCallExpr call) {
        if (call.getScope().isEmpty()) {
            return Optional.empty();
        }

        Expression scope = call.getScope().orElseThrow();
        if (scope.isMethodCallExpr()) {
            return Optional.of(scope.asMethodCallExpr());
        }
        if (!scope.isNameExpr()) {
            return Optional.empty();
        }

        String variableName = scope.asNameExpr().getNameAsString();
        int callLine = call.getBegin().map(pos -> pos.line).orElse(Integer.MAX_VALUE);
        return method.findAll(VariableDeclarator.class).stream()
            .filter(declarator -> declarator.getNameAsString().equals(variableName))
            .filter(declarator -> declarator.getBegin().map(pos -> pos.line <= callLine).orElse(true))
            .map(VariableDeclarator::getInitializer)
            .flatMap(Optional::stream)
            .filter(Expression::isMethodCallExpr)
            .map(Expression::asMethodCallExpr)
            .findFirst();
    }

    private String inferTargetFromLookupCall(MethodCallExpr lookupCall) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (Expression argument : lookupCall.getArguments()) {
            collectTargetTokens(argument, tokens);
        }
        if (tokens.isEmpty()) {
            lookupCall.getScope().ifPresent(scope -> collectTargetTokens(scope, tokens));
        }
        return tokens.stream()
            .filter(token -> token != null && !token.isBlank() && !token.equals("unknown"))
            .findFirst()
            .orElse("");
    }

    private void collectTargetTokens(Expression expression, Set<String> tokens) {
        if (expression == null) {
            return;
        }

        String getterPath = toGetterPath(expression.toString());
        if (!getterPath.isBlank()) {
            tokens.add(getterPath);
        }
        if (expression.isNameExpr()) {
            tokens.add(expression.asNameExpr().getNameAsString());
        }

        expression.findAll(MethodCallExpr.class).forEach(methodCall -> {
            String nestedGetterPath = toGetterPath(methodCall.toString());
            if (!nestedGetterPath.isBlank()) {
                tokens.add(nestedGetterPath);
                return;
            }
            String methodName = methodCall.getNameAsString();
            if (methodName.startsWith("get") && methodName.length() > 3) {
                tokens.add(Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4));
            }
        });
    }

    private String normalizeValidatorTarget(String fieldName) {
        return INDEX_PATTERN.matcher(fieldName).replaceAll("[*]");
    }
    private String inferTargetPathFromCondition(String conditionStr) {
        int getIdx = conditionStr.indexOf(".get");
        if (getIdx != -1) {
            int braceIdx = conditionStr.indexOf("()", getIdx);
            if (braceIdx != -1 && braceIdx > getIdx + 4) {
                String field = conditionStr.substring(getIdx + 4, braceIdx);
                if (!field.isEmpty()) {
                    return Character.toLowerCase(field.charAt(0)) + field.substring(1);
                }
            }
        }

        int dotIdx = conditionStr.indexOf('.');
        if (dotIdx != -1) {
            StringBuilder sb = new StringBuilder();
            for (int i = dotIdx + 1; i < conditionStr.length(); i++) {
                char c = conditionStr.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    sb.append(c);
                } else {
                    break;
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return "unknown";
    }

    private Optional<TypeDeclaration<?>> findTypeInSourceRoots(String typeName) {
        int angleIdx = typeName.indexOf('<');
        if (angleIdx != -1) {
            typeName = typeName.substring(0, angleIdx);
        }
        int dotIdx = typeName.lastIndexOf('.');
        if (dotIdx != -1) {
            typeName = typeName.substring(dotIdx + 1);
        }
        String finalSimpleName = typeName.trim();

        for (Path root : sourceRoots) {
            try (Stream<Path> walk = Files.walk(root)) {
                Path found = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(finalSimpleName + ".java"))
                    .findFirst()
                    .orElse(null);

                if (found != null) {
                    CompilationUnit cu = StaticJavaParser.parse(found);
                    if (cu.getTypes().isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(cu.getType(0));
                }
            } catch (IOException ignored) {
            }
        }
        return Optional.empty();
    }

    private String extractConstructedTypeName(String scopeText) {
        String normalized = scopeText.substring(4).trim();
        int paren = normalized.indexOf('(');
        if (paren == -1) {
            return null;
        }
        return normalized.substring(0, paren).trim();
    }

    private String toGetterPath(String expression) {
        java.util.regex.Matcher matcher = GETTER_PATTERN.matcher(expression);
        List<String> segments = new ArrayList<>();
        while (matcher.find()) {
            segments.add(Character.toLowerCase(matcher.group(1).charAt(0)) + matcher.group(1).substring(1));
        }
        return String.join(".", segments);
    }

    private Path getFilePath(TypeDeclaration<?> clazz) {
        if (clazz == null) {
            return workspaceRoot;
        }
        if (clazz.findCompilationUnit().isPresent() && clazz.findCompilationUnit().get().getStorage().isPresent()) {
            return clazz.findCompilationUnit().get().getStorage().get().getPath();
        }
        return workspaceRoot;
    }

    private String cleanStringLiteral(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
