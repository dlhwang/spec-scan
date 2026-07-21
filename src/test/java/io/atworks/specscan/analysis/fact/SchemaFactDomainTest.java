package io.atworks.specscan.analysis.fact;

import static org.assertj.core.api.Assertions.assertThat;

import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactEdge;
import io.atworks.specscan.analysis.domain.fact.FactEdgeType;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.FactNodePayload;
import io.atworks.specscan.analysis.domain.fact.FactNodeType;
import io.atworks.specscan.analysis.domain.fact.SourceRange;
import io.atworks.specscan.analysis.domain.fact.TypeResolution;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaFactDomainTest {

    @Test
    void representsBindingTypeFieldElementTypeAndStructuredAnnotation() {
        SourceRange range = new SourceRange("src/PropertyRequest.java", 1, 1, 1, 20);
        FactNode api = node("api", FactNodeType.API_METHOD,
            new FactNodePayload.MethodPayload("PropertyController", "save(PropertySave)", true),
            range);
        FactNode parameter = node("parameter", FactNodeType.PARAMETER,
            new FactNodePayload.ParameterPayload("propertyId", 0, "Long", "PATH", true), range);
        FactNode type = node("type", FactNodeType.TYPE,
            new FactNodePayload.TypePayload("Set<ContractDetailDTO>", "java.util.Set", false,
                true, "ContractDetailDTO"), range);
        FactNode field = node("field", FactNodeType.SCHEMA_FIELD,
            new FactNodePayload.SchemaFieldPayload("deposit", "deposit", "long", true, "0"),
            range);
        FactNode annotation = node("annotation", FactNodeType.ANNOTATION,
            new FactNodePayload.AnnotationPayload("Min", Map.of("value", 0L)), range);

        FactCodeGraph graph = new FactCodeGraph("graph", api.id(),
            List.of(api, parameter, type, field, annotation), List.of(
                edge("parameter-type", parameter, type, FactEdgeType.HAS_TYPE),
                edge("type-field", type, field, FactEdgeType.HAS_FIELD),
                edge("field-annotation", field, annotation, FactEdgeType.HAS_ANNOTATION)));

        assertThat(graph.nodes()).hasSize(5);
        assertThat(((FactNodePayload.ParameterPayload) parameter.payload()).bindingLocation())
            .isEqualTo("PATH");
        assertThat(((FactNodePayload.AnnotationPayload) annotation.payload()).attributes())
            .containsEntry("value", 0L);
    }

    private FactNode node(String id, FactNodeType type, FactNodePayload payload,
        SourceRange range) {
        return new FactNode(id, type, range, id, TypeResolution.notApplicable(), payload);
    }

    private FactEdge edge(String id, FactNode source, FactNode target, FactEdgeType type) {
        return new FactEdge(id, source.id(), target.id(), type, 0, "SCHEMA");
    }
}
