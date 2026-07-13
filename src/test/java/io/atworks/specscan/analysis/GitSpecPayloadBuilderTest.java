package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.support.GitSpecPayloadBuilder;
import io.atworks.specscan.ingestion.domain.IngestionMetadata;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.JavaInventorySummary;
import io.atworks.specscan.ingestion.domain.RepositoryIdentity;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.SafetyPolicyHint;
import io.atworks.specscan.ingestion.domain.SourceRootCandidate;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitSpecPayloadBuilderTest {

    @Test
    void payloadAnalysisCountsLateServiceHintWarnings() {
        GitSpecPayloadBuilder builder = new GitSpecPayloadBuilder();
        IngestionWarning rejectedWarning = new IngestionWarning(
            "SERVICE_HINT_REJECTED",
            "Skipped service hint without graph evidence for POST /orders/order: orderService.submit(orderRequest)",
            "/orders/order",
            "MEDIUM",
            Map.of(
                "endpoint", "POST /orders/order",
                "candidateId", "cand-1",
                "targetPath", "shippingInfo.address.zipCode",
                "reasonCategory", "UNREACHABLE_GRAPH_EVIDENCE"
            )
        );
        IngestionWarning ambiguousWarning = new IngestionWarning(
            "SERVICE_HINT_AMBIGUOUS",
            "Skipped ambiguous service hint for POST /orders/order: orderService.submit(orderRequest)",
            "/orders/order",
            "MEDIUM",
            Map.of(
                "endpoint", "POST /orders/order",
                "candidateId", "cand-2",
                "targetPath", "orderProducts[*].productId",
                "reasonCategory", "AMBIGUOUS_GRAPH_EVIDENCE"
            )
        );
        StaticScanResult scanResult = new StaticScanResult(
            List.of(),
            0,
            List.of(),
            new IngestionMetadata(Instant.now(), Instant.now(), 0, "BRANCH", "main", 0)
        );

        Map<String, Object> payload = builder.build(
            "spec-scan",
            "http://localhost:8080",
            buildRepositorySource(scanResult),
            scanResult,
            List.of(),
            List.of(),
            List.of(rejectedWarning, ambiguousWarning)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) payload.get("analysis");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warnings = (List<Map<String, Object>>) payload.get("warnings");

        assertThat(analysis.get("warningCount")).isEqualTo(2);
        assertThat(analysis.get("warningSummary").toString())
            .contains("Skipped service hint without graph evidence")
            .contains("Skipped ambiguous service hint");
        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0).get("code")).isEqualTo("SERVICE_HINT_REJECTED");
        assertThat(warnings.get(0).get("severity")).isEqualTo("warning");
        assertThat(warnings.get(0).get("location")).isEqualTo("/orders/order");
        assertThat(warnings.get(0).get("message").toString()).contains("Skipped service hint without graph evidence");
        assertThat(warnings.get(0).get("details").toString())
            .contains("UNREACHABLE_GRAPH_EVIDENCE")
            .contains("cand-1")
            .contains("POST /orders/order")
            .contains("shippingInfo.address.zipCode");
        assertThat(warnings.get(1).get("code")).isEqualTo("SERVICE_HINT_AMBIGUOUS");
        assertThat(warnings.get(1).get("message").toString()).contains("Skipped ambiguous service hint");
        assertThat(warnings.get(1).get("details").toString())
            .contains("AMBIGUOUS_GRAPH_EVIDENCE")
            .contains("POST /orders/order")
            .contains("cand-2")
            .contains("orderProducts[*].productId");
    }

    private RepositorySource buildRepositorySource(StaticScanResult scanResult) {
        RepositoryIdentity identity = new RepositoryIdentity("github.com", "owner", "repo",
            "https://github.com/owner/repo.git", "main");
        WorkspaceContext workspace = new WorkspaceContext("exec-123", "D:/workspace/auto-oas",
            Instant.now(), "cache-key", false);
        List<SourceRootCandidate> sourceRoots = List.of(
            new SourceRootCandidate("root", "src/main/java", "Gradle", true, 1, 1, 1, "DETECTED")
        );
        JavaInventorySummary javaSummary = new JavaInventorySummary(1, 1, 1, true, 0);
        return new RepositorySource(
            identity,
            workspace,
            sourceRoots,
            "Gradle",
            javaSummary,
            List.of(),
            List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            scanResult.metadata()
        );
    }
}
