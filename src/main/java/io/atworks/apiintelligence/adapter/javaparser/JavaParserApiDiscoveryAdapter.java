package io.atworks.apiintelligence.adapter.javaparser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import io.atworks.apiintelligence.domain.api.ApiIdGenerator;
import io.atworks.apiintelligence.domain.api.DiscoveredApi;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import io.atworks.apiintelligence.port.out.ApiDiscoveryPort;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class JavaParserApiDiscoveryAdapter implements ApiDiscoveryPort {

    @Override
    public List<DiscoveredApi> discover(SourceWorkspace workspace) {
        List<DiscoveredApi> result = new ArrayList<>();
        for (Path root : workspace.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var files = Files.walk(root)) {
                files.filter(p -> p.toString().endsWith(".java")).sorted()
                    .forEach(file -> parse(file, workspace.root(), result));
            } catch (IOException e) {
                throw new IllegalStateException("Unable to scan Java sources", e);
            }
        }
        return result.stream().sorted(
            Comparator.comparing(DiscoveredApi::httpMethod).thenComparing(DiscoveredApi::path)
                .thenComparing(DiscoveredApi::controllerType)
                .thenComparing(DiscoveredApi::handlerSignature)).toList();
    }

    private void parse(Path file, Path projectRoot, List<DiscoveredApi> output) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(file);
            for (ClassOrInterfaceDeclaration type : unit.findAll(
                ClassOrInterfaceDeclaration.class)) {
                if (!isController(type)) {
                    continue;
                }
                String classPath = MappingAnnotationReader.path(type.getAnnotations());
                for (MethodDeclaration method : type.getMethods()) {
                    List<String> mappings = MappingAnnotationReader.paths(method.getAnnotations());
                    if (mappings.isEmpty()) {
                        continue;
                    }
                    for (MappingAnnotationReader.Mapping mapping : MappingAnnotationReader.expand(
                        method.getAnnotations(), mappings)) {
                        String path = join(classPath, mapping.path());
                        String controller =
                            unit.getPackageDeclaration().map(p -> p.getNameAsString() + ".")
                                .orElse("") + type.getNameAsString();
                        String signature = method.getDeclarationAsString(false, false, false);
                        SourceLocation location = location(file, projectRoot, method);
                        String id = ApiIdGenerator.generate(mapping.method(), path, controller,
                            signature);
                        List<String> bindings = method.getParameters().stream()
                            .map(p -> p.getNameAsString() + ":" + p.getType()).toList();
                        output.add(
                            new DiscoveredApi(id, mapping.method(), path, controller, signature,
                                bindings,
                                method.getType().asString(), location));
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }

    private static boolean isController(ClassOrInterfaceDeclaration type) {
        return type.getAnnotations().stream().anyMatch(
            a -> Set.of("Controller", "RestController").stream().anyMatch(
                n -> a.getNameAsString().equals(n) || a.getNameAsString().endsWith("." + n)));
    }

    private static String join(String a, String b) {
        return ApiIdGenerator.normalizePath((a == null ? "" : a) + "/" + (b == null ? "" : b));
    }

    private static SourceLocation location(Path file, Path root, MethodDeclaration method) {
        var begin = method.getBegin().orElseThrow();
        var end = method.getEnd().orElse(begin);
        String relative = root.relativize(file.toAbsolutePath().normalize()).toString();
        return new SourceLocation(relative, begin.line, begin.column, end.line, end.column);
    }
}
