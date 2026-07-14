package io.atworks.specscan.analysis.domain.fact;

import java.util.Objects;

public record FactNode(String id, FactNodeType type, SourceRange sourceRange, String snippet, TypeResolution typeResolution, FactNodePayload payload) {
    public FactNode { Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(sourceRange); Objects.requireNonNull(typeResolution); Objects.requireNonNull(payload); if (id.isBlank() || !matches(type, payload)) throw new IllegalArgumentException("invalid fact node"); }
    private static boolean matches(FactNodeType type, FactNodePayload payload) { return switch (type) {
        case API_METHOD, METHOD -> payload instanceof FactNodePayload.MethodPayload;
        case PARAMETER -> payload instanceof FactNodePayload.ParameterPayload;
        case LOCAL_VARIABLE -> payload instanceof FactNodePayload.LocalVariablePayload;
        case FIELD_ACCESS -> payload instanceof FactNodePayload.FieldAccessPayload;
        case ENUM_CONSTANT -> payload instanceof FactNodePayload.EnumConstantPayload;
        case LITERAL -> payload instanceof FactNodePayload.LiteralPayload;
        case NULL_LITERAL -> payload instanceof FactNodePayload.NullLiteralPayload;
        case METHOD_CALL -> payload instanceof FactNodePayload.MethodCallPayload;
        case CONDITION -> payload instanceof FactNodePayload.ConditionPayload;
        case THROW, RETURN -> payload instanceof FactNodePayload.OutcomePayload;
    }; }
}
