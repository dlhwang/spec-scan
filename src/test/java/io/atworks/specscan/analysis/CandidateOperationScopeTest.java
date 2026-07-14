package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.support.CandidateChunkGenerator;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateOperationScopeTest {
    @Test void keepsCandidatesScopedToMethodsThatShareAPath() {
        SourceTrace trace = new SourceTrace("Controller.java", 1, 1);
        List<ApiEndpoint> endpoints = List.of(endpoint("GET", "get", trace), endpoint("DELETE", "remove", trace));
        List<ValidationCandidate> candidates = List.of(
            candidate("get-rule", "GET /items/{id}", trace),
            candidate("delete-rule", "DELETE /items/{id}", trace));

        List<CandidateChunk> chunks = new CandidateChunkGenerator().generateChunks(candidates, endpoints);

        assertThat(chunks).extracting(CandidateChunk::operationKey)
            .containsExactly("GET /items/{id}", "DELETE /items/{id}");
        assertThat(chunks.get(0).candidates()).extracting(ValidationCandidate::candidateId).containsExactly("get-rule");
        assertThat(chunks.get(1).candidates()).extracting(ValidationCandidate::candidateId).containsExactly("delete-rule");
    }

    private ValidationCandidate candidate(String id, String operationKey, SourceTrace trace) {
        return new ValidationCandidate(id, "SERVICE_HINT", "id", "findById(id).orElseThrow()", .9, trace,
            operationKey);
    }

    private ApiEndpoint endpoint(String method, String operation, SourceTrace trace) {
        return new ApiEndpoint(method, "/items/{id}", "example.Controller", operation, List.of(),
            new ResponseBinding("void", trace), trace);
    }
}
