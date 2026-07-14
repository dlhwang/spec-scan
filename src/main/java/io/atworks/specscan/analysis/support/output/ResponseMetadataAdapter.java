package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

public final class ResponseMetadataAdapter {
    public EndpointRuleOutput augment(ApiEndpoint endpoint, EndpointRuleOutput output) {
        List<CandidateOutputDiagnostic> diagnostics = new ArrayList<>(output.diagnostics());
        diagnostics.add(new CandidateOutputDiagnostic("RESPONSE_METADATA_UNRESOLVED",
            "No explicit normal response status, body condition, or header assertion was observed",
            "response:" + endpoint.path(), null));
        return new EndpointRuleOutput(output.endpointPath(), output.requestPreconditions(),
            output.responseAssertions(), output.excludedBusinessRules(), diagnostics);
    }
}
