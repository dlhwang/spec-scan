package io.atworks.specscan.analysis.fixture;

import io.atworks.specscan.analysis.domain.ApiEndpoint;
import io.atworks.specscan.analysis.domain.BindingLocation;
import io.atworks.specscan.analysis.domain.RequestBinding;
import io.atworks.specscan.analysis.domain.ResponseBinding;
import io.atworks.specscan.analysis.domain.StaticScanResult;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactGraphBuildResult;
import io.atworks.specscan.analysis.domain.fact.FactGraphTraversalBudget;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class ContractDetailValidationFixture {
    private ContractDetailValidationFixture() {}

    public static FactCodeGraph buildPostGraph(Path workspace) throws Exception {
        return buildPostScenario(workspace).build().graphs().get(0);
    }

    public static PostScenario buildPostScenario(Path workspace) throws Exception {
        writeSources(workspace);
        SourceTrace trace = new SourceTrace(
            "src/main/java/demo/api/adapter/PropertyController.java", 1, 1);
        RequestBinding body = new RequestBinding("save", BindingLocation.BODY,
            "demo.api.application.PropertyRequest.PropertySave", true,
            null, null, null, List.of(), trace);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/api/estate/properties",
            "demo.api.adapter.PropertyController", "post", List.of(body),
            new ResponseBinding("void", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 8, List.of(), null);
        FactGraphBuildResult build = new DefaultFactCodeGraphBuilder().build(scan, source(workspace),
            FactGraphTraversalBudget.defaults());
        return new PostScenario(scan, build);
    }

    public record PostScenario(StaticScanResult scan, FactGraphBuildResult build) {}

    private static void writeSources(Path workspace) throws Exception {
        write(workspace, "demo/api/adapter/PropertyController.java", """
            package demo.api.adapter;
            import demo.api.application.PropertyRequest;
            import demo.api.application.PropertyService;
            public class PropertyController {
                private PropertyService service;
                public void post(PropertyRequest.PropertySave save) { service.save(save); }
            }
            """);
        write(workspace, "demo/api/application/PropertyService.java", """
            package demo.api.application;
            import demo.api.domain.PropertySaveService;
            public class PropertyService {
                private PropertySaveService propertySaveService;
                public void save(PropertyRequest.PropertySave save) {
                    propertySaveService.save(save.to());
                }
            }
            """);
        write(workspace, "demo/api/application/PropertyRequest.java", """
            package demo.api.application;
            import demo.api.domain.ContractDetailVO;
            import java.util.Set;
            import java.util.stream.Collectors;
            public class PropertyRequest {
                public static class PropertySave {
                    private Set<ContractDetailDTO> contractDetails;
                    public demo.api.domain.PropertySave to() {
                        return new demo.api.domain.PropertySave(contractDetails.stream()
                            .map(ContractDetailDTO::to).collect(Collectors.toSet()));
                    }
                }
                public static class ContractDetailDTO {
                    private demo.api.domain.ContractType contractType;
                    private long deposit;
                    private long rent;
                    public ContractDetailVO to() {
                        return new ContractDetailVO(contractType, deposit, rent);
                    }
                }
            }
            """);
        write(workspace, "demo/api/domain/PropertySaveService.java", """
            package demo.api.domain;
            public class PropertySaveService {
                public void save(PropertySave save) { save.to(); }
            }
            """);
        write(workspace, "demo/api/domain/PropertySave.java", """
            package demo.api.domain;
            import java.util.Set;
            import java.util.stream.Collectors;
            public class PropertySave {
                private Set<ContractDetailVO> contractDetails;
                public PropertySave(Set<ContractDetailVO> contractDetails) {
                    this.contractDetails = contractDetails;
                }
                public Set<ContractDetail> to() {
                    return contractDetails.stream()
                        .map(detail -> ContractDetail.newInstance(
                            detail.contractType(), detail.deposit(), detail.rent()))
                        .collect(Collectors.toSet());
                }
            }
            """);
        write(workspace, "demo/api/domain/ContractDetailVO.java", """
            package demo.api.domain;
            public record ContractDetailVO(ContractType contractType, long deposit, long rent) {}
            """);
        write(workspace, "demo/api/domain/ContractType.java", """
            package demo.api.domain;
            public enum ContractType { JEONSE, MONTHLYRENT, SALE }
            """);
        write(workspace, "demo/api/domain/ContractDetail.java", """
            package demo.api.domain;
            public class ContractDetail {
                private ContractType contractType;
                private long deposit;
                private long rent;
                protected ContractDetail(ContractType contractType, long deposit, long rent) {
                    if (isNotValid(contractType, deposit, rent)) {
                        throw new IllegalArgumentException("invalid contract detail");
                    }
                    this.contractType = contractType;
                    this.deposit = deposit;
                    this.rent = rent;
                }
                public static ContractDetail newInstance(
                    ContractType contractType, long deposit, long rent) {
                    return new ContractDetail(contractType, deposit, rent);
                }
                private static boolean isNotValid(
                    ContractType contractType, long deposit, long rent) {
                    if (contractType == null) return true;
                    return switch (contractType) {
                        case JEONSE -> deposit <= 0;
                        case MONTHLYRENT -> rent <= 0 || deposit <= 0;
                        default -> false;
                    };
                }
            }
            """);
    }

    private static void write(Path workspace, String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static RepositorySource source(Path workspace) {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path",
            "main"), new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 8, 8, 1,
                "DETECTED")), "Gradle", new JavaInventorySummary(8, 1, 1, true, 0), List.of(),
            List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 8, "LOCAL", "main", 0));
    }
}
