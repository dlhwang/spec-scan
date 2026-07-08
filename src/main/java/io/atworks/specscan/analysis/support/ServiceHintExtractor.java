package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class ServiceHintExtractor {

    private final Path workspaceRoot;
    private final List<Path> sourceRoots;

    public ServiceHintExtractor(Path workspaceRoot, List<Path> sourceRoots) {
        this.workspaceRoot = workspaceRoot;
        this.sourceRoots = sourceRoots;
    }

    /**
     * 특정 서비스 클래스의 메서드 내부에 정의된 비즈니스 예외 던지기(if-throw) 제약을 스캔합니다.
     */
    public List<ValidationCandidate> extractFromServiceMethod(String serviceClassName, String methodName) {
        List<ValidationCandidate> list = new ArrayList<>();
        
        findTypeInSourceRoots(serviceClassName).ifPresent(typeDecl -> {
            if (typeDecl instanceof ClassOrInterfaceDeclaration classDecl) {
                classDecl.getMethodsByName(methodName).forEach(method -> {
                    // Method 내의 if 문들을 스캔
                    method.findAll(IfStmt.class).forEach(ifStmt -> {
                        // if 문 블록 내부에 throw 구문이 존재하는지 확인
                        boolean hasThrow = ifStmt.findFirst(ThrowStmt.class).isPresent();
                        if (hasThrow) {
                            String candidateId = "cand-svc-" + UUID.randomUUID().toString().substring(0, 8);
                            String evidenceSnippet = ifStmt.toString().trim();
                            
                            // 조건식(e.g. user.getAge() < 19)에서 필드명 유추
                            String targetPath = inferTargetPathFromCondition(ifStmt.getCondition().toString());
                            SourceTrace trace = SourceTraceResolver.resolve(ifStmt, workspaceRoot, getFilePath(typeDecl));

                            list.add(new ValidationCandidate(
                                candidateId,
                                "SERVICE_HINT",
                                targetPath,
                                evidenceSnippet,
                                0.5, // 서비스/도메인 레이어 간접 힌트는 confidence 0.5
                                trace
                            ));
                        }
                    });
                });
            }
        });

        return list;
    }

    private String inferTargetPathFromCondition(String conditionStr) {
        // e.g. "user.getAge() < 19" -> "age" 유추
        // getXXX() 패턴 매칭
        int getIdx = conditionStr.indexOf(".get");
        if (getIdx != -1) {
            int braceIdx = conditionStr.indexOf("()", getIdx);
            if (braceIdx != -1 && braceIdx > getIdx + 4) {
                String field = conditionStr.substring(getIdx + 4, braceIdx);
                // First letter lowercase
                if (field.length() > 0) {
                    return Character.toLowerCase(field.charAt(0)) + field.substring(1);
                }
            }
        }
        
        // e.g. "user.age < 19" -> "age" 유추
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
}
