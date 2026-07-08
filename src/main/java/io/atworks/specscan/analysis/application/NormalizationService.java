package io.atworks.specscan.analysis.application;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.support.*;
import io.atworks.specscan.ingestion.domain.IngestionException;

import java.util.ArrayList;
import java.util.List;

public class NormalizationService {

    private final CandidateChunkGenerator chunkGenerator;
    private final CandidateChunkValidator chunkValidator;
    private final PromptBuilder promptBuilder;
    private final LlmResponseValidator responseValidator;

    public NormalizationService() {
        this.chunkGenerator = new CandidateChunkGenerator();
        this.chunkValidator = new CandidateChunkValidator();
        this.promptBuilder = new PromptBuilder();
        this.responseValidator = new LlmResponseValidator();
    }

    /**
     * 유효성 추출 후보군을 입력받아 청크 분할, 무결성 필터링 및 LLM 정규화 연산을 수행하여 NormalizedResult를 반환합니다.
     */
    public NormalizedResult normalize(List<ValidationCandidate> candidates, List<ApiEndpoint> endpoints) throws IngestionException {
        List<ApiCondition> conditions = new ArrayList<>();
        List<ValidationCandidate> rejected = new ArrayList<>();
        List<CandidateChunk> invalidChunks = new ArrayList<>();

        // 1. Chunk Generation
        List<CandidateChunk> chunks = chunkGenerator.generateChunks(candidates, endpoints);

        for (CandidateChunk chunk : chunks) {
            // 2. Chunks Validation (S-06)
            if (!chunkValidator.isValid(chunk)) {
                invalidChunks.add(chunk);
                rejected.addAll(chunk.candidates());
                continue;
            }

            // 3. Prompt Building & LLM Invocation Simulation (S-07)
            String mockResponseJson = resolveMockLlmResponse(chunk);

            // 4. Response Parsing & Schema Validation
            List<ApiCondition> parsedConditions = responseValidator.validateAndParse(mockResponseJson, chunk, rejected);
            conditions.addAll(parsedConditions);
        }

        return new NormalizedResult(conditions, rejected, invalidChunks);
    }

    private String resolveMockLlmResponse(CandidateChunk chunk) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        List<String> items = new ArrayList<>();
        for (ValidationCandidate cand : chunk.candidates()) {
            String operator = "CUSTOM_RULE";
            String expected = "validated";
            String snippet = cand.evidenceSnippet();

            if (cand.sourceType().equals("CUSTOM_ANNOTATION")) {
                if (snippet.contains("Email")) {
                    operator = "EMAIL";
                    expected = "email pattern";
                }
            } else if (cand.sourceType().equals("VALIDATOR")) {
                if (snippet.contains("Email") || snippet.contains("isValid")) {
                    operator = "EMAIL";
                    expected = "email pattern";
                } else {
                    operator = "VALIDATION_LOGIC";
                }
            } else if (cand.sourceType().equals("SERVICE_HINT")) {
                if (snippet.contains("< 19") || snippet.contains("Underage")) {
                    operator = "MIN_AGE";
                    expected = "19";
                } else {
                    operator = "BUSINESS_CONSTRAINT";
                }
            }

            items.add(String.format("""
                {
                  "candidateId": "%s",
                  "targetPath": "%s",
                  "operator": "%s",
                  "expected": "%s",
                  "confidence": %s,
                  "llmReason": "Successfully normalized %s candidate in chunk"
                }""",
                cand.candidateId(),
                cand.targetPath(),
                operator,
                expected,
                cand.confidence(),
                cand.sourceType()
            ));
        }
        sb.append(String.join(",\n", items));
        sb.append("\n]");
        return sb.toString();
    }
}
