package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.support.CandidateChunkGenerator;
import io.atworks.specscan.analysis.support.CandidateChunkValidator;
import io.atworks.specscan.analysis.support.RuleBasedConditionNormalizer;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.IngestionWarning;

import java.util.ArrayList;
import java.util.List;

public class NormalizationService {

    private final CandidateChunkGenerator chunkGenerator;
    private final CandidateChunkValidator chunkValidator;
    private final RuleBasedConditionNormalizer ruleBasedConditionNormalizer;

    public NormalizationService() {
        this.chunkGenerator = new CandidateChunkGenerator();
        this.chunkValidator = new CandidateChunkValidator();
        this.ruleBasedConditionNormalizer = new RuleBasedConditionNormalizer();
    }

    /**
     * 유효성 추출 후보군을 입력받아 청크 분할, 무결성 필터링 및 규칙 기반 정규화 연산을 수행하여 NormalizedResult를 반환합니다.
     */
    public NormalizedResult normalize(List<ValidationCandidate> candidates, List<ApiEndpoint> endpoints) throws IngestionException {
        return normalize(candidates, endpoints, new ValidationEvidenceGraph(List.of(), List.of()));
    }

    public NormalizedResult normalize(
        List<ValidationCandidate> candidates,
        List<ApiEndpoint> endpoints,
        ValidationEvidenceGraph graph
    ) throws IngestionException {
        List<ApiCondition> conditions = new ArrayList<>();
        List<ValidationCandidate> rejected = new ArrayList<>();
        List<CandidateChunk> invalidChunks = new ArrayList<>();
        List<IngestionWarning> warnings = new ArrayList<>();
        List<String> conditionKeys = new ArrayList<>();

        // 1. Chunk Generation
        List<CandidateChunk> chunks = chunkGenerator.generateChunks(candidates, endpoints);

        for (CandidateChunk chunk : chunks) {
            // 2. Chunks Validation (S-06)
            if (!chunkValidator.isValid(chunk)) {
                invalidChunks.add(chunk);
                rejected.addAll(chunk.candidates());
                continue;
            }

            // 3. Deterministic rule-based normalization.
            ApiEndpoint endpoint = endpoints.stream()
                .filter(item -> item.path().equals(chunk.endpointPath()))
                .findFirst()
                .orElse(null);
            appendUniqueConditions(conditions, conditionKeys, normalizeChunkRuleBased(chunk, endpoint, graph, rejected, warnings));
            if ("SERVICE_HINT".equals(chunk.sourceType())) {
                appendUniqueConditions(conditions, conditionKeys, ruleBasedConditionNormalizer.deriveGraphConditions(chunk, endpoint, graph));
            }
        }

        return new NormalizedResult(conditions, rejected, invalidChunks, warnings);
    }

    private List<ApiCondition> normalizeChunkRuleBased(
        CandidateChunk chunk,
        ApiEndpoint endpoint,
        ValidationEvidenceGraph graph,
        List<ValidationCandidate> rejected,
        List<IngestionWarning> warnings
    ) {
        List<ApiCondition> conditions = new ArrayList<>();
        for (ValidationCandidate cand : chunk.candidates()) {
            ruleBasedConditionNormalizer.normalize(cand, chunk, endpoint, graph)
                .ifPresentOrElse(
                    conditions::add,
                    () -> {
                        rejected.add(cand);
                        if ("SERVICE_HINT".equals(cand.sourceType())) {
                            ruleBasedConditionNormalizer.buildServiceHintWarning(cand, chunk, endpoint, graph)
                                .ifPresent(warnings::add);
                        }
                    }
                );
        }
        return conditions;
    }

    private void appendUniqueConditions(List<ApiCondition> conditions, List<String> conditionKeys, List<ApiCondition> additions) {
        for (ApiCondition condition : additions) {
            String key = String.join("|",
                String.valueOf(condition.targetLocation()),
                String.valueOf(condition.targetPath()),
                String.valueOf(condition.operator()),
                String.valueOf(condition.expected()),
                String.valueOf(condition.endpointPath())
            );
            if (conditionKeys.contains(key)) {
                continue;
            }
            conditionKeys.add(key);
            conditions.add(condition);
        }
    }

}
