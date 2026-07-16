package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.support.fact.DefaultFactCodeGraphBuilder;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PropertyPostPreconditionTest {
    @TempDir Path workspace;

    @Test void promotesReachableConstructorNullAndEmptyGuards() throws Exception {
        write("sample/controller/PropertyController.java", """
            package sample.controller;
            import sample.service.PropertyService;
            import sample.dto.PropertyRequest;
            public class PropertyController { private PropertyService service;
                public void post(PropertyRequest request) { service.save(request); }
            }
            """);
        write("sample/service/PropertyService.java", """
            package sample.service;
            import sample.dto.PropertyRequest;
            import sample.model.Property;
            import sample.model.ContractDetail;
            public class PropertyService {
                public void save(PropertyRequest request) {
                    request.getContractDetails().stream()
                        .map(detail -> new ContractDetail(detail.getContractType())).toList();
                    new Property(request.getPropertyType(), request.getContractDetails());
                }
            }
            """);
        write("sample/dto/PropertyRequest.java", """
            package sample.dto;
            import java.util.Set;
            public class PropertyRequest {
                private String propertyType; private Set<ContractDetailRequest> contractDetails;
                public String getPropertyType() { return propertyType; }
                public Set<ContractDetailRequest> getContractDetails() { return contractDetails; }
            }
            """);
        write("sample/dto/ContractDetailRequest.java", """
            package sample.dto;
            public class ContractDetailRequest {
                private String contractType;
                public String getContractType() { return contractType; }
            }
            """);
        write("sample/model/Property.java", """
            package sample.model;
            import java.util.Set;
            import org.springframework.util.ObjectUtils;
            public class Property {
                public Property(String propertyType, Set<?> contractDetails) {
                    if (propertyType == null) throw new IllegalArgumentException();
                    if (ObjectUtils.isEmpty(contractDetails)) throw new IllegalArgumentException();
                }
            }
            """);
        write("sample/model/ContractDetail.java", """
            package sample.model;
            public class ContractDetail {
                public ContractDetail(String contractType) {
                    if (contractType == null) throw new IllegalArgumentException();
                }
            }
            """);
        write("org/springframework/util/ObjectUtils.java", """
            package org.springframework.util;
            public final class ObjectUtils { public static boolean isEmpty(Object value) { return value == null; } }
            """);

        SourceTrace trace = new SourceTrace("src/main/java/sample/controller/PropertyController.java", 5, 5);
        RequestBinding body = new RequestBinding("request", BindingLocation.BODY, "sample.dto.PropertyRequest",
            true, null, null, null, List.of(), trace);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/properties", "sample.controller.PropertyController", "post",
            List.of(body), new ResponseBinding("void", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 5, List.of(), null);

        FactCodeGraph graph = new DefaultFactCodeGraphBuilder().build(scan, source(),
            FactGraphTraversalBudget.defaults()).graphs().get(0);
        assertThat(graph.nodes()).withFailMessage("graph=%s", graph)
            .extracting(FactNode::type).contains(FactNodeType.CONSTRUCTOR, FactNodeType.CONDITION);

        EndpointRuleOutput output = TestRuleOutputs.generate(scan, source()).get("POST /properties");

        assertThat(output.requestPreconditions()).withFailMessage("output=%s", output)
            .extracting(ExecutableCondition::targetPath,
                ExecutableCondition::operator)
            .contains(tuple("$", "NOT_NULL"), tuple("$.propertyType", "NOT_NULL"),
                tuple("$.contractDetails", "NOT_EMPTY"),
                tuple("$.contractDetails[*].contractType", "NOT_NULL"));
        assertThat(output.requestPreconditions()).allSatisfy(condition ->
            assertThat(condition.evidence()).isNotEmpty());
    }

    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private RepositorySource source() {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 5, 5, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(5, 1, 1, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 5, "LOCAL", "main", 0));
    }
}
