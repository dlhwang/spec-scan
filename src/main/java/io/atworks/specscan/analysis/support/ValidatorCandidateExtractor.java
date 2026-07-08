package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class ValidatorCandidateExtractor {

    private final Path workspaceRoot;
    private final List<Path> sourceRoots;

    public ValidatorCandidateExtractor(Path workspaceRoot, List<Path> sourceRoots) {
        this.workspaceRoot = workspaceRoot;
        this.sourceRoots = sourceRoots;
    }

    /**
     * DTO 필드에 선언된 커스텀 어노테이션에 대응하는 ConstraintValidator의 검증 로직을 추출합니다.
     */
    public List<ValidationCandidate> extractFromCustomAnnotation(String annotationName, String fieldName, Path file) {
        List<ValidationCandidate> list = new ArrayList<>();
        
        // 1. 커스텀 어노테이션 정의 파일 탐색
        Optional<TypeDeclaration<?>> annDeclOpt = findTypeInSourceRoots(annotationName);
        if (annDeclOpt.isEmpty()) {
            return list;
        }

        TypeDeclaration<?> annDecl = annDeclOpt.get();
        // 2. @Constraint(validatedBy = ...) 찾기
        annDecl.getAnnotationByName("Constraint").ifPresent(constraintAnn -> {
            String validatorClassName = resolveConstraintValidatorClass(constraintAnn);
            if (validatorClassName != null) {
                // 3. Validator 클래스 파일 탐색 및 isValid 메서드 분석
                findTypeInSourceRoots(validatorClassName).ifPresent(validatorDecl -> {
                    if (validatorDecl instanceof ClassOrInterfaceDeclaration classDecl) {
                        classDecl.getMethodsByName("isValid").forEach(method -> {
                            String candidateId = "cand-val-" + UUID.randomUUID().toString().substring(0, 8);
                            String evidenceSnippet = "Validator: " + validatorClassName + ".isValid()\n" + method.toString().trim();
                            SourceTrace trace = SourceTraceResolver.resolve(method, workspaceRoot, getFilePath(validatorDecl));
                            
                            list.add(new ValidationCandidate(
                                candidateId,
                                "VALIDATOR",
                                fieldName,
                                evidenceSnippet,
                                1.0,
                                trace
                            ));
                        });
                    }
                });
            }
        });

        return list;
    }

    /**
     * Spring Validator 구현체의 validate 메서드 내 errors.rejectValue(...)를 탐색합니다.
     */
    public List<ValidationCandidate> extractFromSpringValidator(String validatorClassName) {
        List<ValidationCandidate> list = new ArrayList<>();
        findTypeInSourceRoots(validatorClassName).ifPresent(validatorDecl -> {
            if (validatorDecl instanceof ClassOrInterfaceDeclaration classDecl) {
                classDecl.getMethodsByName("validate").forEach(method -> {
                    method.findAll(MethodCallExpr.class).forEach(call -> {
                        String name = call.getNameAsString();
                        if (name.equals("rejectValue") || name.equals("reject")) {
                            String fieldName = "root";
                            if (name.equals("rejectValue") && call.getArguments().size() > 0) {
                                fieldName = cleanStringLiteral(call.getArgument(0).toString());
                            }
                            String candidateId = "cand-val-" + UUID.randomUUID().toString().substring(0, 8);
                            String evidenceSnippet = call.toString();
                            SourceTrace trace = SourceTraceResolver.resolve(call, workspaceRoot, getFilePath(validatorDecl));

                            list.add(new ValidationCandidate(
                                candidateId,
                                "VALIDATOR",
                                fieldName,
                                evidenceSnippet,
                                1.0,
                                trace
                            ));
                        }
                    });
                });
            }
        });
        return list;
    }

    private String resolveConstraintValidatorClass(AnnotationExpr constraintAnn) {
        if (constraintAnn instanceof NormalAnnotationExpr normal) {
            for (MemberValuePair pair : normal.getPairs()) {
                if (pair.getNameAsString().equals("validatedBy")) {
                    String val = pair.getValue().toString();
                    // Class literal e.g. { EmailValidator.class } or EmailValidator.class
                    val = val.replace("{", "").replace("}", "").replace(".class", "").trim();
                    return val;
                }
            }
        }
        return null;
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
            } catch (IOException ignored) {}
        }
        return Optional.empty();
    }

    private Path getFilePath(TypeDeclaration<?> clazz) {
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
