package io.atworks.apiintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LlmScanResponseAdapterTest {

    @Test
    @DisplayName("LLM 결과를 기존 operations 응답 계약으로 변환한다")
    void mapsIntelligenceToScanContract() {
        var input = Map.<String, Object>of(
            "apis", List.of(Map.of("apiId", "api-1", "httpMethod", "GET", "path", "/orders",
                "controllerType", "OrderController", "handlerSignature", "list()")),
            "intelligence",
            Map.of("api-1", Map.of("preConditions", List.of(Map.of("description", "auth")))),
            "graphs", Map.of(), "evidence", Map.of(), "runDirectory", "build/run");

        var output = new LlmScanResponseAdapter().toScanResponse(input);

        assertThat(output).containsEntry("analysisMode", "LLM");
        var operations = (List<?>) output.get("operations");
        assertThat(operations).hasSize(1);
        @SuppressWarnings("unchecked")
        var operation = (Map<String, Object>) operations.get(0);
        assertThat(operation).containsKeys("operationId", "method", "path", "controllerClass",
            "request", "response200", "requestPreconditions", "responseAssertions",
            "excludedBusinessRules");
        assertThat(operation).doesNotContainKey("businessRules");
        assertThat(operation.toString()).contains("/orders");
    }
}
