package io.atworks.specscan.analysis.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.ConditionLocation;
import io.atworks.specscan.analysis.domain.ValidationCandidate;

import java.util.*;

public class LlmResponseValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * LLM 응답 JSON 문자열을 파싱하고 스키마 및 의미 정합성을 검증하여 ApiCondition 목록을 반환합니다.
     * 검증에 실패한 후보들은 rejectedList에 누적합니다.
     */
    public List<ApiCondition> validateAndParse(
        String responseJson, 
        CandidateChunk chunk, 
        List<ValidationCandidate> rejectedList
    ) {
        List<ApiCondition> list = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            if (!root.isArray()) {
                rejectedList.addAll(chunk.candidates());
                return list;
            }

            Map<String, ValidationCandidate> candMap = new HashMap<>();
            for (ValidationCandidate c : chunk.candidates()) {
                candMap.put(c.candidateId(), c);
            }

            for (JsonNode node : root) {
                if (!node.has("candidateId") || !node.has("operator") || !node.has("confidence")) {
                    continue;
                }

                String candId = node.get("candidateId").asText();
                String operator = node.get("operator").asText();
                double confidence = node.get("confidence").asDouble();
                String expected = node.has("expected") ? node.get("expected").asText() : "";
                String reason = node.has("llmReason") ? node.get("llmReason").asText() : "No reason provided";

                ValidationCandidate matched = candMap.get(candId);
                if (matched == null) {
                    continue;
                }

                list.add(new ApiCondition(
                    ConditionLocation.UNKNOWN,
                    matched.targetPath(),
                    operator,
                    expected,
                    matched.evidenceSnippet(),
                    confidence,
                    reason,
                    matched.sourceTrace()
                ));
                candMap.remove(candId);
            }

            rejectedList.addAll(candMap.values());

        } catch (Exception e) {
            rejectedList.addAll(chunk.candidates());
        }

        return list;
    }
}
