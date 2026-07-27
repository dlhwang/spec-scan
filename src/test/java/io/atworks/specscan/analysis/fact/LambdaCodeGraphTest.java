package io.atworks.specscan.analysis.fact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.apiintelligence.adapter.javaparser.JavaParserCodeGraphAdapter;
import io.atworks.apiintelligence.config.ApiIntelligenceConfiguration.GraphSettings;
import io.atworks.apiintelligence.domain.api.ApiIdGenerator;
import io.atworks.apiintelligence.domain.api.DiscoveredApi;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
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

public class LambdaCodeGraphTest {

    @TempDir
    Path workspace;

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void testLambdaAndStreamCodeGraphGeneration() throws Exception {
        // 1. 샘플 자바 코드 파일 작성
        String controllerContent = """
            package demo.controller;
            
            import demo.model.Employee;
            import java.util.List;
            import java.util.stream.Collectors;
            
            public class DeveloperController {
                public List<String> getHighEarningDevelopers(List<Employee> employees) {
                    return employees.stream()
                        .filter(emp -> emp.getDepartment().equals("개발팀"))
                        .filter(emp -> emp.getSalary() >= 5000)
                        .map(Employee::getName)
                        .collect(Collectors.toList());
                }
            }
            """;

        String modelContent = """
            package demo.model;
            
            public class Employee {
                private String name;
                private String department;
                private double salary;
                
                public String getName() { return name; }
                public String getDepartment() { return department; }
                public double getSalary() { return salary; }
            }
            """;

        Path controllerFile = workspace.resolve("src/main/java/demo/controller/DeveloperController.java");
        Files.createDirectories(controllerFile.getParent());
        Files.writeString(controllerFile, controllerContent);

        Path modelFile = workspace.resolve("src/main/java/demo/model/Employee.java");
        Files.createDirectories(modelFile.getParent());
        Files.writeString(modelFile, modelContent);

        // 2-A. JavaParserCodeGraphAdapter (API Intelligence CodeGraph) 빌드
        String handlerSig = "List<String> getHighEarningDevelopers(List<Employee>)";
        var loc = new SourceLocation("src/main/java/demo/controller/DeveloperController.java", 1, 1, 18, 1);
        String apiId = ApiIdGenerator.generate("GET", "/developers/high-earning", "demo.controller.DeveloperController", handlerSig);
        var api = new DiscoveredApi(apiId, "GET", "/developers/high-earning", "demo.controller.DeveloperController", handlerSig, List.of(), "List<String>", loc);
        var sourceWs = new SourceWorkspace(workspace, List.of(workspace.resolve("src/main/java")), null, false);
        
        CodeGraph codeGraph = new JavaParserCodeGraphAdapter().build(api, sourceWs, new GraphSettings(100, 1000, 2000));
        String codeGraphJson = objectMapper.writeValueAsString(codeGraph);

        System.out.println("====== [1] API Intelligence CodeGraph JSON ======");
        System.out.println(codeGraphJson);

        // 2-B. DefaultFactCodeGraphBuilder (SpecScan FactCodeGraph) 빌드
        SourceTrace trace = new SourceTrace("src/main/java/demo/controller/DeveloperController.java", 1, 1);
        RequestBinding binding = new RequestBinding("employees", BindingLocation.QUERY, "List<Employee>", true, null, null, null, List.of(), trace);
        ApiEndpoint endpoint = new ApiEndpoint("GET", "/developers/high-earning", "demo.controller.DeveloperController", "getHighEarningDevelopers", List.of(binding), new ResponseBinding("List<String>", trace), trace);
        StaticScanResult scan = new StaticScanResult(List.of(endpoint), 2, List.of(), null);
        
        RepositorySource source = new RepositorySource(
            new RepositoryIdentity("test", "owner", "repo", "path", "main"),
            new WorkspaceContext("exec", workspace.toString(), Instant.now(), "cache", false),
            List.of(new SourceRootCandidate("root", "src/main/java", "Gradle", true, 2, 2, 1, "DETECTED")),
            "Gradle", new JavaInventorySummary(2, 1, 1, true, 0),
            List.of(), List.of(), new SafetyPolicyHint(List.of(), List.of(), "1.0"),
            new IngestionMetadata(Instant.now(), Instant.now(), 2, "LOCAL", "main", 0)
        );

        FactGraphBuildResult factResult = new DefaultFactCodeGraphBuilder().build(scan, source, FactGraphTraversalBudget.defaults());
        assertThat(factResult.graphs()).isNotEmpty();
        FactCodeGraph factCodeGraph = factResult.graphs().get(0);
        String factGraphJson = objectMapper.writeValueAsString(factCodeGraph);

        System.out.println("\n====== [2] SpecScan FactCodeGraph JSON ======");
        System.out.println(factGraphJson);

        // Assertions: 람다 및 메서드 참조가 제대로 감지되었는지 검증
        assertThat(codeGraph.nodes()).anyMatch(node -> node.name().equals("filter") || node.name().equals("map"));
        assertThat(factCodeGraph.nodes()).anyMatch(node -> node.type() == FactNodeType.LAMBDA);
        assertThat(factCodeGraph.nodes()).anyMatch(node -> node.type() == FactNodeType.METHOD_REFERENCE);
    }
}
