package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.application.NormalizationService;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationServiceTest {

    private NormalizationService normalizationService;

    @BeforeEach
    void setUp() {
        normalizationService = new NormalizationService();
    }

    @Test
    void testCandidateChunkingAndNormalization() throws IngestionException {
        // Given
        SourceTrace trace1 = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 12);
        SourceTrace trace2 = new SourceTrace("src/main/java/io/atworks/service/UserService.java", 20, 25);
        SourceTrace invalidTrace = new SourceTrace(null, 0, 0); // null path to trigger invalid chunk

        ValidationCandidate cand1 = new ValidationCandidate("cand-1", "CUSTOM_ANNOTATION", "email", "@ValidEmail", 1.0, trace1);
        ValidationCandidate cand2 = new ValidationCandidate("cand-2", "SERVICE_HINT", "age", "user.getAge() < 19", 0.5, trace2);
        
        // candidate with missing trace to trigger validation failure
        ValidationCandidate candInvalid = new ValidationCandidate("cand-3", "VALIDATOR", "name", "name error", 1.0, invalidTrace);

        List<ValidationCandidate> candidates = List.of(cand1, cand2, candInvalid);

        // Mock Endpoint
        RequestBinding bodyBinding = new RequestBinding("user", BindingLocation.BODY, "UserDto", true, null, null, null, List.of(), trace1);
        ResponseBinding responseBinding = new ResponseBinding("void", trace1);
        ApiEndpoint endpoint = new ApiEndpoint(
            "POST",
            "/users",
            "io.atworks.controller.UserController",
            "createUser",
            List.of(bodyBinding),
            responseBinding,
            trace1
        );
        List<ApiEndpoint> endpoints = List.of(endpoint);

        // When
        NormalizedResult result = normalizationService.normalize(candidates, endpoints);

        // Then
        // 1. Valid conditions count (cand-1, cand-2)
        assertThat(result.conditions()).hasSize(2);
        
        ApiCondition emailCond = result.conditions().stream()
                .filter(c -> c.targetPath().equals("email")).findFirst().orElseThrow();
        assertThat(emailCond.operator()).isEqualTo("EMAIL");
        assertThat(emailCond.expected()).isEqualTo("email pattern");

        ApiCondition ageCond = result.conditions().stream()
                .filter(c -> c.targetPath().equals("age")).findFirst().orElseThrow();
        assertThat(ageCond.operator()).isEqualTo("MIN_AGE");
        assertThat(ageCond.expected()).isEqualTo("19");
        assertThat(ageCond.confidence()).isEqualTo(0.5);

        // 2. Invalid chunk detection (cand-3 is grouped to global because of null trace, which triggers invalid validation)
        assertThat(result.invalidChunks()).hasSize(1);
        CandidateChunk invalidChunk = result.invalidChunks().get(0);
        assertThat(invalidChunk.sourceType()).isEqualTo("VALIDATOR");
        
        // 3. Rejected verification
        assertThat(result.rejected()).hasSize(1);
        assertThat(result.rejected().get(0).candidateId()).isEqualTo("cand-3");
    }
}
