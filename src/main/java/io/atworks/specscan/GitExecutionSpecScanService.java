package io.atworks.specscan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.application.OpenApiAssemblyService;
import io.atworks.specscan.analysis.application.RuleOutputService;
import io.atworks.specscan.analysis.application.SpringStaticScanService;
import io.atworks.specscan.analysis.application.ValidationExtractionService;
import io.atworks.specscan.analysis.domain.NormalizedResult;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.ValidationExtractionResult;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.ExecutionSpecExporter;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.analysis.support.ValidationEvidenceGraphBuilder;
import io.atworks.specscan.ingestion.adapter.RepositorySourceFetcherAdapter;
import io.atworks.specscan.ingestion.adapter.TempWorkspacePreparerAdapter;
import io.atworks.specscan.ingestion.application.RepositoryIngestionService;
import io.atworks.specscan.ingestion.domain.RepositoryRequest;
import io.atworks.specscan.ingestion.domain.RepositorySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class GitExecutionSpecScanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Git 저장소를 수집하고 정적 분석하여 실행 명세 JSON을 생성함
    public String scan(
        String repositoryUrl,
        String revisionType,
        String revision
    ) throws Exception {
        RepositoryIngestionService ingestionService = new RepositoryIngestionService(
            new RepositorySourceFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        RepositorySource repositorySource = null;
        try {
            // 대상 revision을 가져와 임시 분석 작업공간을 준비함
            repositorySource = ingestionService.ingest(toRepositoryRequest(repositoryUrl, revisionType, revision));

            // Spring 엔드포인트와 기본 소스 정보를 정적 분석함
            SpringStaticScanService scanService = new SpringStaticScanService();
            StaticScanResult scanResult = scanService.scan(repositorySource);
            FactGraphBuildResult factGraphs = new DefaultFactCodeGraphBuilder().build(
                scanResult, repositorySource, FactGraphTraversalBudget.defaults());

            // 소스에서 검증 조건 후보를 추출하고 조건 간 근거 그래프를 생성함
            ValidationExtractionService extractionService = new ValidationExtractionService();
            ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);
            ValidationEvidenceGraph graph = new ValidationEvidenceGraphBuilder().build(scanResult, extractionResult, repositorySource);

            // 서로 다른 형태의 검증 조건을 공통 조건 모델로 정규화함
            NormalizationService normalizationService = new NormalizationService();
            NormalizedResult normalizedResult = normalizationService.normalize(
                extractionResult.candidates(),
                scanResult.endpoints(),
                graph
            );

            // 분석 단계별 경고를 최종 결과에 포함할 단일 목록으로 병합함
            ArrayList<io.atworks.specscan.ingestion.domain.IngestionWarning> warnings = new ArrayList<>(scanResult.warnings());
            warnings.addAll(extractionResult.warnings());
            warnings.addAll(normalizedResult.warnings());

            // 조건을 엔드포인트별 규칙으로 조립한 뒤 실행 명세 JSON으로 출력함
            Map<String, EndpointRuleOutput> ruleOutputs = new RuleOutputService().generate(
                scanResult, factGraphs, extractionResult.directConditions(), normalizedResult.conditions());
            return new ExecutionSpecExporter().export(scanResult, ruleOutputs, warnings, repositorySource);
        } finally {
            // 성공 여부와 관계없이 임시 작업공간을 정리함
            if (repositorySource != null) {
                new TempWorkspacePreparerAdapter().clean(repositorySource.workspaceContext());
            }
        }
    }

    // OpenAPI 조립 과정에서 생성된 실행 모델과 근거 그래프를 함께 반환함
    public String scanWithArtifacts(
        String repositoryUrl,
        String revisionType,
        String revision
    ) throws Exception {
        RepositoryIngestionService ingestionService = new RepositoryIngestionService(
            new RepositorySourceFetcherAdapter(),
            new TempWorkspacePreparerAdapter()
        );

        RepositorySource repositorySource = null;
        try {
            repositorySource = ingestionService.ingest(toRepositoryRequest(repositoryUrl, revisionType, revision));

            SpringStaticScanService scanService = new SpringStaticScanService();
            StaticScanResult scanResult = scanService.scan(repositorySource);

            ValidationExtractionService extractionService = new ValidationExtractionService();
            ValidationExtractionResult extractionResult = extractionService.extract(scanResult, repositorySource);

            // OpenAPI와 API 실행 모델, 검증 근거 그래프 산출물을 생성함
            Path outputPath = Path.of(repositorySource.workspaceContext().workspacePath(), "openapi.yaml");
            new OpenApiAssemblyService().assemble(scanResult, extractionResult, repositorySource, outputPath);

            JsonNode executionModel = OBJECT_MAPPER.readTree(
                Files.readString(outputPath.getParent().resolve("api-execution-model.json"))
            );
            JsonNode evidenceGraph = OBJECT_MAPPER.readTree(
                Files.readString(outputPath.getParent().resolve("validation-evidence-graph.json"))
            );

            // 실행 모델 최상위에 근거 그래프를 추가하여 단일 응답으로 구성함
            Map<String, Object> response = new LinkedHashMap<>();
            if (executionModel.isObject()) {
                executionModel.fields().forEachRemaining(entry -> response.put(entry.getKey(), entry.getValue()));
            }
            response.put("validationEvidenceGraph", evidenceGraph);
            return OBJECT_MAPPER.writeValueAsString(response);
        } finally {
            // 성공 여부와 관계없이 임시 작업공간을 정리함
            if (repositorySource != null) {
                new TempWorkspacePreparerAdapter().clean(repositorySource.workspaceContext());
            }
        }
    }

    // revision 종류에 따라 RepositoryRequest의 해당 필드만 설정함
    private RepositoryRequest toRepositoryRequest(String repositoryUrl, String revisionType, String revision) {
        String branch = null;
        String tag = null;
        String commit = null;

        if ("branch".equalsIgnoreCase(revisionType)) {
            branch = revision;
        } else if ("tag".equalsIgnoreCase(revisionType)) {
            tag = revision;
        } else if ("commit".equalsIgnoreCase(revisionType)) {
            commit = revision;
        }
        return new RepositoryRequest(repositoryUrl, branch, tag, commit);
    }
}
