package io.atworks.specscan.analysis;

import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.GraphEdge;
import io.atworks.specscan.analysis.domain.GraphEdgeType;
import io.atworks.specscan.analysis.domain.GraphNode;
import io.atworks.specscan.analysis.domain.GraphNodeType;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.analysis.domain.ValidationEvidenceGraph;
import io.atworks.specscan.analysis.support.GraphContextSelector;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphContextSelectorTest {

    @Test
    void selectsEndpointScopedSubgraphForPrompt() {
        SourceTrace trace = new SourceTrace("src/main/java/io/atworks/controller/UserController.java", 10, 12);
        CandidateChunk chunk = new CandidateChunk(
            "chunk-1",
            "/users",
            "SERVICE_HINT",
            List.of(new ValidationCandidate("cand-1", "SERVICE_HINT", "age", "user.getAge() < 19", 0.5, trace))
        );

        ValidationEvidenceGraph graph = new ValidationEvidenceGraph(
            List.of(
                new GraphNode("ENDPOINT:POST:/users", GraphNodeType.ENDPOINT, "POST /users", "UserController.java", 10,
                    "UserController.createUser"),
                new GraphNode("DTO_FIELD:UserDto.age", GraphNodeType.DTO_FIELD, "UserDto.age", "UserDto.java", 20,
                    "private int age;"),
                new GraphNode("SERVICE_METHOD:UserService.register", GraphNodeType.SERVICE_METHOD, "UserService.register",
                    "UserService.java", 30, "public void register(UserDto user)"),
                new GraphNode("BUSINESS_RULE:UserService.register:age", GraphNodeType.BUSINESS_RULE, "user.getAge() < 19",
                    "UserService.java", 32, "if (user.getAge() < 19) throw new IllegalArgumentException();"),
                new GraphNode("ENDPOINT:GET:/other", GraphNodeType.ENDPOINT, "GET /other", "OtherController.java", 5,
                    "OtherController.read")
            ),
            List.of(
                new GraphEdge("ENDPOINT:POST:/users", "SERVICE_METHOD:UserService.register", GraphEdgeType.CALLS,
                    "userService.register(user)"),
                new GraphEdge("SERVICE_METHOD:UserService.register", "BUSINESS_RULE:UserService.register:age", GraphEdgeType.EVALUATES,
                    "if statement"),
                new GraphEdge("ENDPOINT:POST:/users", "DTO_FIELD:UserDto.age", GraphEdgeType.ACCEPTS, "user"),
                new GraphEdge("ENDPOINT:GET:/other", "SERVICE_METHOD:UserService.register", GraphEdgeType.CALLS,
                    "unrelated")
            )
        );

        GraphContextSelector selector = new GraphContextSelector();
        GraphContextSelector.GraphContext context = selector.selectContext(chunk, graph, 4, 4);

        assertThat(context.nodeSummaries()).isNotEmpty();
        assertThat(context.nodeSummaries().toString()).contains("POST /users");
        assertThat(context.nodeSummaries().toString()).contains("UserDto.age");
        assertThat(context.edgeSummaries().toString()).contains("CALLS");
        assertThat(context.edgeSummaries().toString()).contains("ACCEPTS");
    }
}
