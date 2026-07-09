package io.atworks.specscan.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.analysis.support.OpenApiGenerator;
import io.atworks.specscan.analysis.support.StructuredSpecExporter;
import io.atworks.specscan.analysis.support.ValidationEvidenceGraphBuilder;
import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class OpenApiAssemblyService {

    private final NormalizationService normalizationService;
    private final OpenApiGenerator openApiGenerator;
    private final StructuredSpecExporter structuredSpecExporter;
    private final ObjectMapper objectMapper;

    public OpenApiAssemblyService() {
        this.normalizationService = new NormalizationService();
        this.openApiGenerator = new OpenApiGenerator();
        this.structuredSpecExporter = new StructuredSpecExporter();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void assemble(
        StaticScanResult scanResult,
        ValidationExtractionResult extractResult,
        RepositorySource source,
        Path outputPath
    ) throws IngestionException {
        List<IngestionWarning> warnings = new ArrayList<>(extractResult.warnings());

        NormalizedResult normalizedResult;
        try {
            normalizedResult = normalizationService.normalize(extractResult.candidates(), scanResult.endpoints());
            for (ValidationCandidate reject : normalizedResult.rejected()) {
                warnings.add(new IngestionWarning(
                    "NORMALIZATION_REJECTED",
                    "Candidate rejected during LLM normalization schema validation.",
                    reject.targetPath(),
                    "MEDIUM"
                ));
            }
            for (CandidateChunk invalid : normalizedResult.invalidChunks()) {
                warnings.add(new IngestionWarning(
                    "INVALID_CHUNK_DETECTED",
                    "Candidate chunk rejected due to missing ID or missing source evidence.",
                    invalid.endpointPath(),
                    "HIGH"
                ));
            }
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to run LLM normalization: " + e.getMessage()
            );
        }

        String yamlContent;
        try {
            yamlContent = openApiGenerator.generateYaml(
                scanResult,
                extractResult.directConditions(),
                normalizedResult.conditions()
            );
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate OpenAPI YAML document: " + e.getMessage()
            );
        }

        String structuredJson;
        try {
            structuredJson = structuredSpecExporter.export(
                scanResult,
                extractResult.directConditions(),
                normalizedResult.conditions()
            );
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate structured API analysis JSON document: " + e.getMessage()
            );
        }

        ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractResult, source);
        String graphJson;
        try {
            graphJson = objectMapper.writeValueAsString(graph);
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to serialize validation evidence graph to JSON: " + e.getMessage()
            );
        }

        try {
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.writeString(outputPath, yamlContent);
            Files.writeString(resolveStructuredOutputPath(outputPath), structuredJson);
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

    private Path resolveGraphOutputPath(Path outputPath) {
        Path parent = outputPath.getParent();
        if (parent == null) {
            return Path.of("validation-evidence-graph.json");
        }
        return parent.resolve("validation-evidence-graph.json");
    }
}
