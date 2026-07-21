package io.atworks.specscan.analysis.output;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.output.EndpointRuleOutput;
import io.atworks.specscan.analysis.support.output.ResponseInvariantAdapter;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseInvariantAdapterTest {
    @Test void promotesReachableResponseFactoryConstantsToAssertions() {
        FactNode api = node("api", FactNodeType.API_METHOD,
            new FactNodePayload.MethodPayload("Controller", "login()", true));
        FactNode returned = node("return", FactNodeType.RETURN,
            new FactNodePayload.OutcomePayload("RETURN", "ReturnStmt"));
        FactNode factoryCall = node("factory-call", FactNodeType.METHOD_CALL,
            new FactNodePayload.MethodCallPayload("success", 1, true));
        FactNode factoryMethod = node("factory-method", FactNodeType.METHOD,
            new FactNodePayload.MethodPayload("ApiResponse", "success(Object)", false));
        FactNode factoryReturn = node("factory-return", FactNodeType.RETURN,
            new FactNodePayload.OutcomePayload("RETURN", "ReturnStmt"));
        FactNode creation = node("creation", FactNodeType.OBJECT_CREATION,
            new FactNodePayload.ObjectCreationPayload("ApiResponse", 4));
        FactNode literal = node("true", FactNodeType.LITERAL,
            new FactNodePayload.LiteralPayload("true", "BooleanLiteralExpr"));
        FactNode field = node("success-field", FactNodeType.VALUE_FIELD,
            new FactNodePayload.FieldAccessPayload("success", "CONSTRUCTOR_PARAMETER:ApiResponse"));
        FactCodeGraph graph = new FactCodeGraph("graph", api.id(),
            List.of(api, returned, factoryCall, factoryMethod, factoryReturn, creation, literal, field),
            List.of(
                edge("api-return", api, returned, FactEdgeType.RETURNS, "RETURN"),
                edge("return-call", returned, factoryCall, FactEdgeType.OPERAND_OF, "RETURN_BODY"),
                edge("call-target", factoryCall, factoryMethod, FactEdgeType.CALLS, "TARGET"),
                edge("factory-return", factoryMethod, factoryReturn, FactEdgeType.RETURNS, "RETURN"),
                edge("return-creation", factoryReturn, creation, FactEdgeType.OPERAND_OF, "RETURN_BODY"),
                edge("creation-literal", creation, literal, FactEdgeType.OPERAND_OF, "ARGUMENT"),
                edge("literal-field", literal, field, FactEdgeType.VALUE_FLOWS_TO,
                    "CONSTRUCTOR_ARGUMENT")));
        SourceTrace trace = new SourceTrace("Controller.java", 1, 1);
        ApiEndpoint endpoint = new ApiEndpoint("POST", "/login", "Controller", "login", List.of(),
            new ResponseBinding("ApiResponse<LoginResponse>", trace), trace);

        EndpointRuleOutput output = new ResponseInvariantAdapter().augment(endpoint,
            EndpointRuleOutput.empty(endpoint.path()), graph, List.of());

        assertThat(output.responseAssertions()).singleElement().satisfies(assertion -> {
            assertThat(assertion.targetPath()).isEqualTo("$.success");
            assertThat(assertion.operator()).isEqualTo("EQ");
            assertThat(assertion.expectedValues()).containsExactly("true");
            assertThat(assertion.ruleId()).isEqualTo("RESPONSE_FACTORY_CONSTANT");
        });
    }

    private FactNode node(String id, FactNodeType type, FactNodePayload payload) {
        SourceRange range = new SourceRange("Controller.java", 1, 1, 1, 20);
        return new FactNode(id, type, range, id, TypeResolution.notApplicable(), payload);
    }

    private FactEdge edge(String id, FactNode source, FactNode target, FactEdgeType type, String role) {
        return new FactEdge(id, source.id(), target.id(), type, 0, role);
    }
}
