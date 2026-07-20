package io.specscan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.specscan.extract.EndpointExtractor;
import io.specscan.graph.CodeGraph;
import io.specscan.graph.GraphBuilder;
import io.specscan.model.ApiEndpoint;
import io.specscan.model.ScanResult;
import io.specscan.parse.ProjectIndex;
import io.specscan.parse.ProjectParser;
import io.specscan.source.ProjectResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "spec-scan", mixinStandardHelpOptions = true, version = "0.1.0",
        description = "Scans a Spring project (git URL or local path), builds a code graph and "
                + "extracts executable API specs with preconditions, response assertions and domain rules.")
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = "Git URL (https://... , git@...) or local project path")
    String source;

    @Option(names = {"-o", "--output"}, description = "Output directory (default: ${DEFAULT-VALUE})")
    Path output = Path.of("spec-scan-output");

    @Option(names = "--no-graph", description = "Skip writing code-graph.json")
    boolean noGraph;

    @Override
    public Integer call() throws Exception {
        Path project = ProjectResolver.resolve(source);
        System.err.println("[spec-scan] parsing " + project);
        ProjectIndex index = ProjectParser.parse(project);
        System.err.println("[spec-scan] parsed " + index.units.size() + " files, "
                + index.typesByQName.size() + " types");

        CodeGraph graph = GraphBuilder.build(index);
        EndpointExtractor extractor = new EndpointExtractor(index, graph);
        var endpoints = extractor.extract();

        ScanResult result = new ScanResult();
        result.source = source;
        result.scannedAt = Instant.now().toString();
        result.apis = endpoints;
        result.stats.put("files", index.units.size());
        result.stats.put("types", index.typesByQName.size());
        result.stats.put("graphNodes", graph.nodes.size());
        result.stats.put("graphEdges", graph.graph.edgeSet().size());
        result.stats.put("endpoints", endpoints.size());

        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path specFile = output.resolve("api-spec.json");
        mapper.writeValue(specFile.toFile(), result);
        if (!noGraph) {
            mapper.writeValue(output.resolve("code-graph.json").toFile(), graph.export());
        }

        System.out.println();
        System.out.printf("%-7s %-45s %5s %5s %5s%n", "METHOD", "PATH", "PRE", "RESP", "OTHER");
        for (ApiEndpoint ep : endpoints) {
            System.out.printf("%-7s %-45s %5d %5d %5d%n", ep.httpMethod, ep.path,
                    ep.preConditions.size(), ep.responseAssertions.size(), ep.others.size());
        }
        System.out.println("\n[spec-scan] wrote " + specFile.toAbsolutePath()
                + (noGraph ? "" : " and code-graph.json"));
        return 0;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }
}
