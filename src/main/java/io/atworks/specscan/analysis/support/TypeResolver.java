package io.atworks.specscan.analysis.support;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TypeResolver {

    private final List<Path> sourceRoots;

    public TypeResolver(List<Path> sourceRoots) {
        this.sourceRoots = sourceRoots;
    }

    /**
     * DTO 클래스명을 기반으로 클래스 소스 파일을 찾아서 ClassOrInterfaceDeclaration 노드를 반환합니다.
     */
    public Optional<ClassOrInterfaceDeclaration> resolveClassDeclaration(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }

        // 단순 클래스명만 넘어왔거나 generic인 경우 단순화
        String cleanClassName = getCleanClassName(className);

        // source roots 아래에서 패키지 경로를 환산하거나 전체 스캔을 통해 클래스 파일 찾기
        for (Path root : sourceRoots) {
            Path file = findClassFile(root, cleanClassName);
            if (file != null && Files.exists(file)) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(file);
                    return cu.getClassByName(cleanClassName)
                             .or(() -> cu.getInterfaceByName(cleanClassName));
                } catch (IOException e) {
                    System.err.println("Warning: Failed to parse class file " + file + ": " + e.getMessage());
                }
            }
        }
        return Optional.empty();
    }

    private String getCleanClassName(String className) {
        // generic 해제 (e.g. List<UserDto> -> List, UserDto)
        int angleIdx = className.indexOf('<');
        if (angleIdx != -1) {
            className = className.substring(0, angleIdx);
        }
        // FQCN인 경우 마지막 이름 추출
        int dotIdx = className.lastIndexOf('.');
        if (dotIdx != -1) {
            className = className.substring(dotIdx + 1);
        }
        return className.trim();
    }

    private Path findClassFile(Path sourceRoot, String simpleClassName) {
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(simpleClassName + ".java"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
