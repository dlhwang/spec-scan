package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.ingestion.domain.SourceTrace;

public final class ResponseMetadataAdapter {
    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output) {
        List<CandidateOutputDiagnostic> diagnostics = new ArrayList<>(output.diagnostics());
        List<ExecutableCondition> assertions = new ArrayList<>(output.responseAssertions());
        if (endpoint.responseBinding().explicitStatus() != null) {
            assertions.add(statusAssertion(endpoint));
        } else if (assertions.isEmpty()) {
            diagnostics.add(new CandidateOutputDiagnostic("RESPONSE_METADATA_UNRESOLVED",
                "No explicit normal response status, body condition, or header assertion was observed",
                "response:" + endpoint.httpMethod() + ":" + endpoint.path(), null));
        }
        return new EndpointRuleOutput(output.endpointPath(), output.requestPreconditions(),
            assertions, output.excludedBusinessRules(), diagnostics);
    }

    private ExecutableCondition statusAssertion(ApiEndpoint endpoint) {
        SourceTrace trace = endpoint.responseBinding().sourceTrace();
        int start = trace == null ? 1 : Math.max(1, trace.startLine());
        int end = trace == null ? start : Math.max(start, trace.endLine());
        String file = trace == null || trace.fileRelativePath() == null || trace.fileRelativePath().isBlank()
            ? "unknown" : trace.fileRelativePath();
        EvidenceRef evidence = new EvidenceRef("response-status:" + start, file, start, 1, end, 1,
            EvidenceRole.INPUT_ORIGIN, endpoint.responseBinding().statusSource());
        return new ExecutableCondition("STATUS", "$status", "EQUALS",
            List.of(String.valueOf(endpoint.responseBinding().explicitStatus())),
            endpoint.responseBinding().statusSource(), "RESPONSE_STATUS_METADATA", 1.0, List.of(evidence));
    }
}
