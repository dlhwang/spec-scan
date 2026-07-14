package io.atworks.specscan.analysis.support.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.evaluation.EvaluationReport;

public final class EvaluationReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public String toJson(EvaluationReport report) {
        try { return mapper.writeValueAsString(report); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("evaluation report serialization failed", exception);
        }
    }
}
