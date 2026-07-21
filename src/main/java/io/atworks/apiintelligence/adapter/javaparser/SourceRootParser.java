package io.atworks.apiintelligence.adapter.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import io.atworks.apiintelligence.domain.diagnostic.Diagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class SourceRootParser {

    record ParsedSource(Path sourceRoot, Path file, CompilationUnit unit) {

    }

    record Result(List<ParsedSource> sources, List<Diagnostic> diagnostics) {

    }

    Result parse(List<Path> sourceRoots) {
        JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        List<ParsedSource> sources = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Path root : sourceRoots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted().toList()) {
                    try {
                        var result = parser.parse(file);
                        if (result.isSuccessful() && result.getResult().isPresent()) {
                            sources.add(
                                new ParsedSource(root, file, result.getResult().orElseThrow()));
                        } else {
                            diagnostics.add(new Diagnostic("JAVA_PARSE_FAILED", "DISCOVERING",
                                "Unable to parse " + root.relativize(file).toString()
                                    .replace('\\', '/'), false));
                        }
                    } catch (Exception e) {
                        diagnostics.add(new Diagnostic("JAVA_PARSE_FAILED", "DISCOVERING",
                            "Unable to parse " + root.relativize(file).toString()
                                .replace('\\', '/'), false));
                    }
                }
            } catch (IOException e) {
                diagnostics.add(new Diagnostic("SOURCE_ROOT_READ_FAILED", "DISCOVERING",
                    "Unable to read a configured source root", false));
            }
        }
        return new Result(List.copyOf(sources), List.copyOf(diagnostics));
    }
}
