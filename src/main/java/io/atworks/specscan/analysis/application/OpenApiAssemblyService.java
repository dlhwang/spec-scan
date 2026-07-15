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
import io.atworks.specscan.analysis.support.NormalizationRejectionClassifier;
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
    private final NormalizationRejectionClassifier rejectionClassifier = new NormalizationRejectionClassifier();

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
        long stepStartedAt = System.nanoTime();
        List<IngestionWarning> warnings = new ArrayList<>(scanResult.warnings());
        warnings.addAll(extractResult.warnings());
        System.out.printf("  [Step 4.1] Building validation evidence graph (endpoints=%d, candidates=%d)...%n",
            scanResult.endpoints().size(), extractResult.candidates().size());
        long stageStartedAt = System.nanoTime();
        ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractResult, source);
        System.out.printf("  [Step 4.1] DONE graph nodes=%d, edges=%d (%d ms)%n",
            graph.nodes().size(), graph.edges().size(), elapsedMillis(stageStartedAt));

        NormalizedResult normalizedResult;
        try {
            System.out.printf("  [Step 4.2] Normalizing %d candidates across %d endpoints...%n",
                extractResult.candidates().size(), scanResult.endpoints().size());
            stageStartedAt = System.nanoTime();
            normalizedResult = normalizationService.normalize(extractResult.candidates(), scanResult.endpoints(), graph);
            System.out.printf("  [Step 4.2] DONE conditions=%d, rejected=%d, invalidChunks=%d, warnings=%d (%d ms)%n",
                normalizedResult.conditions().size(), normalizedResult.rejected().size(),
                normalizedResult.invalidChunks().size(), normalizedResult.warnings().size(),
                elapsedMillis(stageStartedAt));
            warnings.addAll(normalizedResult.warnings());
            for (ValidationCandidate reject : normalizedResult.rejected()) {
                if ("SERVICE_HINT".equals(reject.sourceType())) {
                    continue;
                }
                warnings.add(new IngestionWarning(
                    rejectionClassifier.classify(reject),
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
            System.out.println("  [Step 4.3] Exporting structured API analysis JSON...");
            stageStartedAt = System.nanoTime();
            structuredJson = structuredSpecExporter.export(
                scanResult,
                extractResult.directConditions(),
                normalizedResult.conditions()
            );
            System.out.printf("  [Step 4.3] DONE (%d ms)%n", elapsedMillis(stageStartedAt));
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate structured API analysis JSON document: " + e.getMessage()
            );
        }

        RuleOutputMigrationResult migration = null;
        if (migrationMode != OutputMigrationMode.LEGACY_ONLY) {
            System.out.printf("  [Step 4.4] Running output migration (mode=%s)...%n", migrationMode);
            stageStartedAt = System.nanoTime();
            migration = ruleOutputMigrationService.migrate(scanResult, source, normalizedResult.conditions());
            System.out.printf("  [Step 4.4] DONE outputs=%d (%d ms)%n",
                migration.outputs().size(), elapsedMillis(stageStartedAt));
        } else {
            System.out.println("  [Step 4.4] Skipping output migration (mode=LEGACY_ONLY)");
        }

        String executionJson;
        try {
            System.out.println("  [Step 4.5] Exporting API execution model JSON...");
            stageStartedAt = System.nanoTime();
            executionJson = migrationMode == OutputMigrationMode.LEGACY_ONLY
                ? executionSpecExporter.export(scanResult, extractResult.directConditions(),
                    normalizedResult.conditions(), warnings, source)
                : executionSpecExporter.export(scanResult, migration.outputs(), warnings, source);
            System.out.printf("  [Step 4.5] DONE (%d ms)%n", elapsedMillis(stageStartedAt));
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate execution model JSON document: " + e.getMessage()
            );
        }

        String yamlContent;
        try {
            System.out.println("  [Step 4.6] Generating OpenAPI YAML...");
            stageStartedAt = System.nanoTime();
            yamlContent = openApiGenerator.generateYaml(executionJson);
            System.out.printf("  [Step 4.6] DONE (%d ms)%n", elapsedMillis(stageStartedAt));
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to generate OpenAPI YAML document: " + e.getMessage()
            );
        }

        String graphJson;
        try {
            System.out.println("  [Step 4.7] Serializing validation evidence graph...");
            stageStartedAt = System.nanoTime();
            graphJson = objectMapper.writeValueAsString(graph);
            System.out.printf("  [Step 4.7] DONE (%d ms)%n", elapsedMillis(stageStartedAt));
        } catch (Exception e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to serialize validation evidence graph to JSON: " + e.getMessage()
            );
        }

        try {
            System.out.printf("  [Step 4.8] Writing output files near %s...%n", outputPath.toAbsolutePath());
            stageStartedAt = System.nanoTime();
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            Files.writeString(outputPath, yamlContent);
            Files.writeString(resolveStructuredOutputPath(outputPath), structuredJson);
            Files.writeString(resolveExecutionOutputPath(outputPath), executionJson);
            Files.writeString(resolveGraphOutputPath(outputPath), graphJson);
            if (migration != null) Files.writeString(resolveMigrationReportPath(outputPath),
                objectMapper.writeValueAsString(migration.comparisonDocument()));
            System.out.printf("  [Step 4.8] DONE files=%d (%d ms)%n",
                migration == null ? 4 : 5, elapsedMillis(stageStartedAt));
            System.out.printf("  [Step 4] COMPLETED in %d ms%n", elapsedMillis(stepStartedAt));
        } catch (IOException e) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Failed to write assembled output files near target path " + outputPath + ": " + e.getMessage()
            );
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
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
        return OutputMigrationMode.configured(System.getProperty("specscan.output.migration-mode"));
    }
}
