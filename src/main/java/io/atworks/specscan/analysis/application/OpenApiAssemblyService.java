package io.atworks.specscan.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
import io.atworks.specscan.analysis.support.OpenApiGenerator;
import io.atworks.specscan.analysis.support.StructuredSpecExporter;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenApiAssemblyService {

    private final OpenApiGenerator openApiGenerator;
    private final StructuredSpecExporter structuredSpecExporter;
    private final ExecutionSpecExporter executionSpecExporter;
    private final ObjectMapper objectMapper;
    private final RuleOutputService ruleOutputService;

    public OpenApiAssemblyService() {
        this.openApiGenerator = new OpenApiGenerator();
        this.structuredSpecExporter = new StructuredSpecExporter();
        this.executionSpecExporter = new ExecutionSpecExporter();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.ruleOutputService = new RuleOutputService();
    }

    public void assemble(
        StaticScanResult scanResult,
        ValidationExtractionResult extractResult,
        RepositorySource source,
        Path outputPath
    ) throws IngestionException {
        List<IngestionWarning> warnings = new ArrayList<>(scanResult.warnings());
        warnings.addAll(extractResult.warnings());
        FactGraphBuildResult factGraphs = new DefaultFactCodeGraphBuilder().build(
            scanResult, source, FactGraphTraversalBudget.defaults());

        Map<String, EndpointRuleOutput> ruleOutputs = ruleOutputService.generate(
            scanResult, factGraphs, extractResult.directConditions());

        String structuredJson;
        try {
            structuredJson = structuredSpecExporter.export(
                scanResult,
                extractResult.directConditions(),
                ruleOutputs
            );
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate structured API analysis JSON document: " + e.getMessage()
            );
        }

        String executionJson;
        try {
            executionJson = executionSpecExporter.export(scanResult, ruleOutputs, warnings, source);
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate execution model JSON document: " + e.getMessage()
            );
        }

        String yamlContent;
        try {
            yamlContent = openApiGenerator.generateYaml(executionJson);
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate OpenAPI YAML document: " + e.getMessage()
            );
        }

        String graphJson;
        try {
            graphJson = objectMapper.writeValueAsString(factGraphs);
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to serialize fact code graph artifact to JSON: " + e.getMessage()
            );
        }

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.writeString(outputPath, yamlContent);
            Files.writeString(resolveStructuredOutputPath(outputPath), structuredJson);
            Files.writeString(resolveExecutionOutputPath(outputPath), executionJson);
            Files.writeString(resolveGraphOutputPath(outputPath), graphJson);
        } catch (IOException e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to write assembled output files near target path " + outputPath + ": " + e.getMessage()
            );
        }
    }

    private Path resolveStructuredOutputPath(Path outputPath) {
        Path parent = outputPath.getParent();
        if (parent == null) {
            return Path.of("api-spec-analysis.json");
        }
        return parent.resolve("api-spec-analysis.json");
    }

    private Path resolveExecutionOutputPath(Path outputPath) {
        Path parent = outputPath.getParent();
        if (parent == null) {
            return Path.of("api-execution-model.json");
        }
        return parent.resolve("api-execution-model.json");
    }

    private Path resolveGraphOutputPath(Path outputPath) {
        Path parent = outputPath.getParent();
        if (parent == null) {
            return Path.of("validation-evidence-graph.json");
        }
        return parent.resolve("validation-evidence-graph.json");
    }

}
