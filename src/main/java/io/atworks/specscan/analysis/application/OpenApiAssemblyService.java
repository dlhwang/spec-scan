package io.atworks.specscan.analysis.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.analysis.domain.output.OutputMigrationMode;
import io.atworks.specscan.analysis.domain.output.RuleOutputMigrationResult;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
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
    private final ExecutionSpecExporter executionSpecExporter;
    private final ObjectMapper objectMapper;
    private final RuleOutputMigrationService ruleOutputMigrationService;
    private final OutputMigrationMode migrationMode;

    public OpenApiAssemblyService() {
        this(resolveMigrationMode());
    }

    public OpenApiAssemblyService(OutputMigrationMode migrationMode) {
        this.normalizationService = new NormalizationService();
        this.openApiGenerator = new OpenApiGenerator();
        this.structuredSpecExporter = new StructuredSpecExporter();
        this.executionSpecExporter = new ExecutionSpecExporter();
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.ruleOutputMigrationService = new RuleOutputMigrationService();
        this.migrationMode = migrationMode;
    }

    public void assemble(
        StaticScanResult scanResult,
        ValidationExtractionResult extractResult,
        RepositorySource source,
        Path outputPath
    ) throws IngestionException {
        List<IngestionWarning> warnings = new ArrayList<>(scanResult.warnings());
        warnings.addAll(extractResult.warnings());
        ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractResult, source);

        NormalizedResult normalizedResult;
        try {
            normalizedResult = normalizationService.normalize(extractResult.candidates(), scanResult.endpoints(), graph);
            warnings.addAll(normalizedResult.warnings());
            for (ValidationCandidate reject : normalizedResult.rejected()) {
                if ("SERVICE_HINT".equals(reject.sourceType())) {
                    continue;
                }
                warnings.add(new IngestionWarning(
                    "NORMALIZATION_REJECTED",
                    "Candidate rejected during rule-based normalization.",
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
                "Failed to run rule-based normalization: " + e.getMessage()
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

        RuleOutputMigrationResult migration = null;
        if (migrationMode != OutputMigrationMode.LEGACY_ONLY) {
            migration = ruleOutputMigrationService.migrate(scanResult, source, normalizedResult.conditions());
        }

        String executionJson;
        try {
            executionJson = migrationMode == OutputMigrationMode.NEW_ONLY
                ? executionSpecExporter.export(scanResult, migration.outputs(), warnings, source)
                : executionSpecExporter.export(scanResult, extractResult.directConditions(),
                    normalizedResult.conditions(), warnings, source);
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
            Files.writeString(resolveExecutionOutputPath(outputPath), executionJson);
            Files.writeString(resolveGraphOutputPath(outputPath), graphJson);
            if (migration != null) Files.writeString(resolveMigrationReportPath(outputPath),
                objectMapper.writeValueAsString(migration.comparisonReports()));
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

    private Path resolveMigrationReportPath(Path outputPath) {
        Path parent = outputPath.getParent();
        return parent == null ? Path.of("api-condition-migration-report.json")
            : parent.resolve("api-condition-migration-report.json");
    }

    private static OutputMigrationMode resolveMigrationMode() {
        String configured = System.getProperty("specscan.output.migration-mode", "COMPARE");
        try { return OutputMigrationMode.valueOf(configured.trim().toUpperCase()); }
        catch (RuntimeException ignored) { return OutputMigrationMode.COMPARE; }
    }
}
