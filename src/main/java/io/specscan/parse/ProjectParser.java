package io.specscan.parse;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Parses all main-source Java files of a project with symbol resolution attached. */
public final class ProjectParser {

    private ProjectParser() {
    }

    public static ProjectIndex parse(Path projectRoot) throws IOException {
        List<Path> sourceRoots = findSourceRoots(projectRoot);
        if (sourceRoots.isEmpty()) {
            throw new IOException("No java source roots found under " + projectRoot);
        }

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        for (Path root : sourceRoots) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(config);

        ProjectIndex index = new ProjectIndex(projectRoot);
        for (Path root : sourceRoots) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    parser.parse(file).getResult().ifPresent(cu -> {
                        index.units.add(cu);
                        cu.findAll(TypeDeclaration.class).forEach(t -> index.register((TypeDeclaration<?>) t));
                    });
                }
            }
        }
        return index;
    }

    /** Every src/main/java directory; falls back to the root itself if none exist. */
    private static List<Path> findSourceRoots(Path projectRoot) throws IOException {
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> p.endsWith(Path.of("src", "main", "java")))
                    .forEach(roots::add);
        }
        if (roots.isEmpty()) {
            try (Stream<Path> walk = Files.walk(projectRoot)) {
                boolean hasJava = walk.anyMatch(p -> p.toString().endsWith(".java")
                        && !p.toString().contains("/test/"));
                if (hasJava) roots.add(projectRoot);
            }
        }
        return roots;
    }
}
