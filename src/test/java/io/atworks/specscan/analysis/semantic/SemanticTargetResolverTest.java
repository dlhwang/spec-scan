package io.atworks.specscan.analysis.semantic;

import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import io.atworks.specscan.analysis.support.semantic.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SemanticTargetResolverTest {
    @Test void usesExternalHeaderNameInsteadOfJavaParameterName() {
        FactNode api = node("api", FactNodeType.API_METHOD, "get()",
            new FactNodePayload.MethodPayload("sample.Api", "get()", true));
        FactNode header = node("header", FactNodeType.PARAMETER, "@RequestHeader String id",
            new FactNodePayload.ParameterPayload("id", 0, "String", "HEADER",
                "Authorization", true));
        FactCodeGraph graph = new FactCodeGraph("graph", api.id(), List.of(api, header), List.of());

        assertThat(new SemanticTargetResolver().resolve(new SemanticContext(graph), header))
            .extracting(SemanticTargetResolution::role, SemanticTargetResolution::path)
            .containsExactly(SemanticTargetRole.REQUEST, "$.Authorization");
    }

    @Test void distinguishesApiRequestParameterFromResponseConstructorField() {
        FactNode api = node("api", FactNodeType.API_METHOD, "get()",
            new FactNodePayload.MethodPayload("sample.Api", "get()", true));
        FactNode requestParameter = node("request", FactNodeType.PARAMETER, "request",
            new FactNodePayload.ParameterPayload("request", 0, "String"));
        FactNode requestValue = node("request-value", FactNodeType.FIELD_ACCESS, "request",
            new FactNodePayload.FieldAccessPayload("request", "NameExpr"));
        FactNode constructorParameter = node("constructor-parameter", FactNodeType.PARAMETER, "propertyType",
            new FactNodePayload.ParameterPayload("propertyType", 0, "String"));
        FactNode responseArgument = node("response-argument", FactNodeType.FIELD_ACCESS, "propertyType",
            new FactNodePayload.FieldAccessPayload("propertyType", "NameExpr"));
        FactNode guardedValue = node("guarded", FactNodeType.FIELD_ACCESS, "propertyType",
            new FactNodePayload.FieldAccessPayload("propertyType", "NameExpr"));
        FactNode responseField = node("response-field", FactNodeType.VALUE_FIELD, "propertyType",
            new FactNodePayload.FieldAccessPayload("propertyType",
                "CONSTRUCTOR_PARAMETER:sample.PropertyResponse"));
        FactNode responseType = new FactNode("response-type", FactNodeType.TYPE, range(), "PropertyResponse",
            TypeResolution.resolvedType("sample.PropertyResponse"),
            new FactNodePayload.TypePayload("PropertyResponse", "sample.PropertyResponse", false,
                false, null));
        FactCodeGraph graph = new FactCodeGraph("graph", api.id(), List.of(api, requestParameter,
            requestValue, constructorParameter, responseArgument, guardedValue, responseField, responseType),
            List.of(
                edge("request-read", requestValue, requestParameter, FactEdgeType.READS, "DECLARATION"),
                edge("guard-read", guardedValue, constructorParameter, FactEdgeType.READS, "DECLARATION"),
                edge("constructor-origin", constructorParameter, responseArgument,
                    FactEdgeType.ORIGINATES_FROM, "CALL_ARGUMENT"),
                edge("response-flow", responseArgument, responseField,
                    FactEdgeType.VALUE_FLOWS_TO, "CONSTRUCTOR_ARGUMENT"),
                edge("response-type-edge", api, responseType, FactEdgeType.RETURNS_TYPE,
                    "RESPONSE_BODY_TYPE")));
        SemanticContext context = new SemanticContext(graph);
        SemanticTargetResolver resolver = new SemanticTargetResolver();

        assertThat(resolver.resolve(context, requestValue))
            .extracting(SemanticTargetResolution::role, SemanticTargetResolution::path)
            .containsExactly(SemanticTargetRole.REQUEST, "$");
        assertThat(resolver.resolve(context, guardedValue))
            .extracting(SemanticTargetResolution::role, SemanticTargetResolution::path)
            .containsExactly(SemanticTargetRole.RESPONSE, "$.propertyType");
    }

    private FactNode node(String id, FactNodeType type, String snippet, FactNodePayload payload) {
        return new FactNode(id, type, range(), snippet, TypeResolution.notApplicable(), payload);
    }

    private FactEdge edge(String id, FactNode source, FactNode target, FactEdgeType type, String role) {
        return new FactEdge(id, source.id(), target.id(), type, -1, role);
    }

    private SourceRange range() { return new SourceRange("src/Test.java", 1, 1, 1, 20); }
}
