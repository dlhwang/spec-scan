package io.atworks.specscan.analysis.migration;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.ingestion.domain.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OtherPropertyPreconditionTest {
    @TempDir Path workspace;

    @Test void keepsPutDomainGuardsAndDoesNotWeakenRepositoryExistence() throws Exception {
        write("sample/controller/PropertyController.java", """
            package sample.controller;
            import sample.service.PropertyService;
            import sample.dto.PropertyRequest;
            public class PropertyController {
                private PropertyService service;
                public void put(Long propertyId, PropertyRequest request) { service.put(propertyId, request); }
                public void get(Long propertyId) { service.get(propertyId); }
                public void delete(Long propertyId) { service.delete(propertyId); }
            }
            """);
        write("sample/service/PropertyService.java", """
            package sample.service;
            import sample.dto.PropertyRequest;
            import sample.model.Property;
            import sample.repository.PropertyRepository;
            public class PropertyService {
                private PropertyRepository repository;
                public void put(Long id, PropertyRequest request) {
                    repository.findById(id).orElseThrow();
                    new Property(request.getPropertyType());
                }
                public void get(Long id) { repository.findById(id).orElseThrow(); }
                public void delete(Long id) { repository.findById(id).orElseThrow(); }
            }
            """);
        write("sample/dto/PropertyRequest.java", """
            package sample.dto;
            public class PropertyRequest {
                private String propertyType;
                public String getPropertyType() { return propertyType; }
            }
            """);
        write("sample/model/Property.java", """
            package sample.model;
            public class Property {
                public Property(String propertyType) {
                    if (propertyType == null) throw new IllegalArgumentException();
                }
            }
            """);
        write("sample/repository/PropertyRepository.java", """
            package sample.repository;
            import sample.model.Property;
            import org.springframework.data.repository.CrudRepository;
            public interface PropertyRepository extends CrudRepository<Property, Long> {}
            """);
        write("org/springframework/data/repository/CrudRepository.java", """
            package org.springframework.data.repository;
            import java.util.Optional;
            public interface CrudRepository<T, ID> { Optional<T> findById(ID id); }
            """);

        SourceTrace trace = new SourceTrace("src/main/java/sample/controller/PropertyController.java", 1, 20);
        RequestBinding id = new RequestBinding("propertyId", BindingLocation.PATH, "java.lang.Long",
            true, null, null, null, List.of(), trace);
        RequestBinding body = new RequestBinding("request", BindingLocation.BODY, "sample.dto.PropertyRequest",
            true, null, null, null, List.of(), trace);
        List<ApiEndpoint> endpoints = List.of(
            endpoint("PUT", "put", List.of(id, body), trace), endpoint("GET", "get", List.of(id), trace),
            endpoint("DELETE", "delete", List.of(id), trace));
        StaticScanResult scan = new StaticScanResult(endpoints, 6, List.of(), null);

        var outputs = TestRuleOutputs.generate(scan, source());
        EndpointRuleOutput put = outputs.get("PUT /properties/{propertyId}");
        assertThat(put.requestPreconditions()).extracting(ExecutableCondition::targetPath,
            ExecutableCondition::operator).contains(tuple("$.propertyId", "NOT_NULL"),
                tuple("$", "NOT_NULL"), tuple("$.propertyType", "NOT_NULL"));

        for (String method : List.of("GET", "DELETE")) {
            EndpointRuleOutput output = outputs.get(method + " /properties/{propertyId}");
            assertThat(output.requestPreconditions()).extracting(ExecutableCondition::targetPath,
                ExecutableCondition::operator).containsExactly(tuple("$.propertyId", "NOT_NULL"));
            assertThat(output.requestPreconditions()).extracting(ExecutableCondition::operator)
                .doesNotContain("NOT_EMPTY");
            assertThat(output.excludedBusinessRules()).anySatisfy(rule -> {
                assertThat(rule.category().name()).isEqualTo("EXISTENCE");
                assertThat(rule.evidence()).isNotEmpty();
            });
        }
    }

    private ApiEndpoint endpoint(String method, String controllerMethod, List<RequestBinding> bindings,
                                 SourceTrace trace) {
        return new ApiEndpoint(method, "/properties/{propertyId}", "sample.controller.PropertyController",
            controllerMethod, bindings, new ResponseBinding("void", trace), trace);
    }
    private void write(String relative, String content) throws Exception {
        Path file = workspace.resolve("src/main/java").resolve(relative);
        Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private RepositorySource source() {
        Instant now = Instant.now();
        return new RepositorySource(new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), now, "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 6, 6, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(6, 1, 1, true, 0), List.of(), List.of(),
            new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(now, now, 6, "LOCAL", "main", 0));
    }
}
