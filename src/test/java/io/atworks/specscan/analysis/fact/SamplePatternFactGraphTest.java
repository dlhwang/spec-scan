package io.atworks.specscan.analysis.fact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SamplePatternFactGraphTest {

    @TempDir
    Path workspace;

    @Test
    void testVerifyAllFourGraphPatterns() throws Exception {
        // 1. 소스 파일 작성 (SampleOrderRequest, SampleUserService, SamplePatternController)
        String requestDto = """
            package demo.dto;
            import java.lang.annotation.*;
            import java.util.List;
            
            @Retention(RetentionPolicy.RUNTIME) @interface NotNull {}
            @Retention(RetentionPolicy.RUNTIME) @interface Min { long value(); }
            @Retention(RetentionPolicy.RUNTIME) @interface Size { int min(); int max(); }
            
            public class SampleOrderRequest {
                @NotNull
                @Size(min = 2, max = 100)
                private String orderTitle;

                @Min(1)
                private int quantity;

                private List<Item> items;

                public String getOrderTitle() { return orderTitle; }
                public int getQuantity() { return quantity; }
                public List<Item> getItems() { return items; }

                public static class Item {
                    @NotNull private String itemName;
                    @Min(1000) private long amount;
                    public String getItemName() { return itemName; }
                    public long getAmount() { return amount; }
                }
            }
            """;

        String userService = """
            package demo.service;
            public class SampleUserService {
                public boolean isUserActive(Long userId) {
                    return userId != null && userId > 0;
                }
            }
            """;

        String controller = """
            package demo.controller;
            import demo.dto.SampleOrderRequest;
            import demo.service.SampleUserService;
            import java.lang.annotation.*;
            import java.util.List;
            import java.util.stream.Collectors;

            @Retention(RetentionPolicy.RUNTIME) @interface RequestParam {}
            @Retention(RetentionPolicy.RUNTIME) @interface RequestBody {}
            @Retention(RetentionPolicy.RUNTIME) @interface NotNull {}
            @Retention(RetentionPolicy.RUNTIME) @interface Min { long value(); }

            public class SamplePatternController {
                private SampleUserService userService;

                public List<String> processOrder(
                        @RequestParam @NotNull @Min(1) Long userId,
                        @RequestBody SampleOrderRequest orderRequest
                ) {
                    // [패턴 1] BINARY_EXPR -> IF -> THROW
                    if (orderRequest.getQuantity() <= 0) {
                        throw new IllegalArgumentException("Quantity error");
                    }

                    // [패턴 2] BOOLEAN_CALL -> IF -> THROW
                    if (!userService.isUserActive(userId)) {
                        throw new IllegalStateException("User inactive");
                    }

                    // [패턴 4] LAMBDA -> STREAM_FILTER
                    return orderRequest.getItems().stream()
                            .filter(item -> item.getAmount() >= 1000)
                            .map(SampleOrderRequest.Item::getItemName)
                            .collect(Collectors.toList());
                }
            }
            """;

        Path dtoFile = workspace.resolve("src/main/java/demo/dto/SampleOrderRequest.java");
        Files.createDirectories(dtoFile.getParent());
        Files.writeString(dtoFile, requestDto);

        Path serviceFile = workspace.resolve("src/main/java/demo/service/SampleUserService.java");
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, userService);

        Path ctrlFile = workspace.resolve("src/main/java/demo/controller/SamplePatternController.java");
        Files.createDirectories(ctrlFile.getParent());
        Files.writeString(ctrlFile, controller);

        // 2. Scan 및 Ingestion 메타데이터 구축
        SourceTrace trace = new SourceTrace("src/main/java/demo/controller/SamplePatternController.java", 1, 1);
        RequestBinding b1 = new RequestBinding("userId", BindingLocation.QUERY, "Long", true, null, null, null, List.of(), trace);
        RequestBinding b2 = new RequestBinding("orderRequest", BindingLocation.BODY, "SampleOrderRequest", true, null, null, null, List.of(), trace);

        ApiEndpoint endpoint = new ApiEndpoint("POST", "/api/sample/orders", "demo.controller.SamplePatternController", "processOrder", List.of(b1, b2), new ResponseBinding("List<String>", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 3, List.of(), null);

        RepositorySource source = new RepositorySource(
            new RepositoryIdentity("test-repo", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec-id", workspace.toString(), Instant.now(), "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 3, 3, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(3, 1, 1, true, 0),
            List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(Instant.now(), Instant.now(), 3, "LOCAL", "main", 0)
        );

        // 3. FactCodeGraph 생성
        FactGraphBuildResult result = new DefaultFactCodeGraphBuilder().build(scan, source, FactGraphTraversalBudget.defaults());

        assertThat(result.graphs()).isNotEmpty();
        FactCodeGraph graph = result.graphs().get(0);

        // 1. BINARY_EXPR -> IF -> THROW 검증
        boolean hasBinaryIfThrow = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.CONDITION && node.snippet().contains("orderRequest.getQuantity() <= 0"));

        // 2. BOOLEAN_CALL -> IF -> THROW 검증
        boolean hasBooleanCallIfThrow = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.CONDITION && node.snippet().contains("!userService.isUserActive(userId)"));

        // 3. VALIDATION_ANNOTATION -> PARAMETER/FIELD 검증
        boolean hasValidationAnnotations = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.ANNOTATION);

        // 4. LAMBDA -> STREAM_FILTER 검증
        boolean hasLambdaStreamFilter = graph.nodes().stream()
                .anyMatch(node -> node.type() == FactNodeType.LAMBDA && node.snippet().contains("item -> item.getAmount() >= 1000"));

        System.out.println("\n====== [Pattern Verification Results] ======");
        System.out.println("1. BINARY_EXPR -> IF -> THROW : " + hasBinaryIfThrow);
        System.out.println("2. BOOLEAN_CALL -> IF -> THROW: " + hasBooleanCallIfThrow);
        System.out.println("3. VALIDATION_ANNOTATION      : " + hasValidationAnnotations);
        System.out.println("4. LAMBDA -> STREAM_FILTER    : " + hasLambdaStreamFilter);

        assertThat(hasBinaryIfThrow).isTrue();
        assertThat(hasBooleanCallIfThrow).isTrue();
        assertThat(hasValidationAnnotations).isTrue();
        assertThat(hasLambdaStreamFilter).isTrue();
    }
}
