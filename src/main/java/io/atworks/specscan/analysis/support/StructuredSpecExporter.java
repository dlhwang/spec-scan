package io.atworks.specscan.analysis.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.ApiCondition;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.ApiSpecAnalysisExport;
import io.atworks.specscan.analysis.domain.ApiValueValidationRecord;
import io.atworks.specscan.analysis.domain.ApiVersionRecord;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.ParameterRecord;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.domain.output.ExecutableCondition;
import io.atworks.specscan.analysis.domain.output.OperationKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StructuredSpecExporter {

    private final ObjectMapper objectMapper;

    public StructuredSpecExporter() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String export(StaticScanResult scanResult, List<ApiConditionDraft> drafts,
                         Map<String, EndpointRuleOutput> ruleOutputs) {
        ApiSpecAnalysisExport exportModel = buildExport(scanResult, drafts, ruleOutputs);
        try {
            return objectMapper.writeValueAsString(exportModel);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize structured spec export.", e);
        }
    }

    private ApiSpecAnalysisExport buildExport(
        StaticScanResult scanResult,
        List<ApiConditionDraft> drafts,
        Map<String, EndpointRuleOutput> ruleOutputs
    ) {
        List<ApiVersionRecord> apiVersions = new ArrayList<>();
        List<ParameterRecord> parameters = new ArrayList<>();
        List<ApiValueValidationRecord> valueValidations = new ArrayList<>();

        long apiVersionId = 1L;
        long parameterId = 1L;
        long validationId = 1L;
        long apiId = 1L;

for (ApiEndpoint endpoint : scanResult.endpoints()) {
    EndpointRuleOutput output = ruleOutputs.getOrDefault(OperationKey.of(endpoint).externalKey(),
        EndpointRuleOutput.empty(endpoint.path()));
    String jsonRequestBody = resolveJsonRequestBody(endpoint, drafts, output);
    String requestExample = jsonRequestBody;
    String responseExample = resolveResponseExample(endpoint);

    apiVersions.add(new ApiVersionRecord(
        apiVersionId,
        apiId,
        1,
        endpoint.httpMethod(),
        endpoint.path(),
        resolveContentType(endpoint),
        jsonRequestBody,
        null,
        requestExample,
        responseExample,
        null,
        null,
        false
    ));

    for (RequestBinding binding : endpoint.requestBindings()) {
        if (binding.targetLocation() == BindingLocation.BODY) {
            continue;
        }
        parameters.add(new ParameterRecord(
            parameterId++,
            apiVersionId,
            binding.targetLocation().name(),
            binding.parameterName(),
            null,
            "STATIC",
            binding.parameterName()
        ));
    }

    int orderNo = 1;
    for (ApiConditionDraft draft : drafts) {
        valueValidations.add(new ApiValueValidationRecord(
            validationId++,
            apiId,
            1,
            orderNo++,
            toJsonPath(draft.targetPath()),
            draft.operator(),
            draft.expected(),
            true,
            null,
            null
        ));
    }
    for (ExecutableCondition condition : output.requestPreconditions()) {
        valueValidations.add(new ApiValueValidationRecord(
            validationId++,
            apiId,
            1,
            orderNo++,
            condition.targetPath(),
            condition.operator(),
            expected(condition),
            true,
            null,
            null
        ));
    }

    apiVersionId++;
    apiId++;
}

        return new ApiSpecAnalysisExport(apiVersions, parameters, valueValidations);
    }

    private String resolveContentType(ApiEndpoint endpoint) {
        boolean hasBody = endpoint.requestBindings().stream()
            .anyMatch(binding -> binding.targetLocation() == BindingLocation.BODY);
        return hasBody ? "JSON" : "NONE";
    }

    private String resolveJsonRequestBody(
        ApiEndpoint endpoint,
        List<ApiConditionDraft> drafts,
        EndpointRuleOutput output
    ) {
        Optional<RequestBinding> bodyBinding = endpoint.requestBindings().stream()
            .filter(binding -> binding.targetLocation() == BindingLocation.BODY)
            .findFirst();
        if (bodyBinding.isEmpty()) {
            return null;
        }

Map<String, Object> template = new LinkedHashMap<>();
for (ApiConditionDraft draft : drafts) {
    template.putIfAbsent(draft.targetPath(), resolvePlaceholderValue(draft.targetPath()));
}
for (ExecutableCondition condition : output.requestPreconditions()) {
    template.putIfAbsent(condition.targetPath(), resolvePlaceholderValue(condition.targetPath()));
}
if (template.isEmpty()) {
    template.put("schemaRef", bodyBinding.get().type());
}

try {
    return objectMapper.writeValueAsString(template);
} catch (JsonProcessingException e) {
    throw new IllegalStateException("Failed to serialize request body template.", e);
}
    }

    private String resolveResponseExample(ApiEndpoint endpoint) {
        String responseType = endpoint.responseBinding().type();
        if (responseType == null || "void".equals(responseType)) {
            return null;
        }
        if (isPrimitiveType(responseType)) {
            return switch (responseType) {
                case "boolean", "Boolean" -> "true";
                case "int", "Integer", "long", "Long" -> "0";
                default -> "\"string\"";
            };
        }

        try {
            return objectMapper.writeValueAsString(Map.of("type", responseType));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize response example.", e);
        }
    }

    private Object resolvePlaceholderValue(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.endsWith("id") || lower.contains("count") || lower.contains("age")) {
            return 0;
        }
        if (lower.startsWith("is") || lower.startsWith("has") || lower.contains("enabled") || lower.contains("active")) {
            return false;
        }
        return "";
    }

    private String toJsonPath(String targetPath) {
        return targetPath.startsWith("$.") ? targetPath : "$." + targetPath;
    }

    private String expected(ExecutableCondition condition) {
        if (condition.expectedValues().isEmpty()) return null;
        return condition.expectedValues().size() == 1
            ? condition.expectedValues().get(0)
            : String.join(",", condition.expectedValues());
    }

    private boolean isPrimitiveType(String typeName) {
        return typeName.equals("String") || typeName.equals("int") || typeName.equals("Integer")
            || typeName.equals("long") || typeName.equals("Long") || typeName.equals("boolean")
            || typeName.equals("Boolean");
    }
}
