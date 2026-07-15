package io.atworks.specscan.analysis.domain.fact;

public sealed interface FactNodePayload permits FactNodePayload.MethodPayload, FactNodePayload.ConstructorPayload, FactNodePayload.ObjectCreationPayload, FactNodePayload.LambdaPayload, FactNodePayload.MethodReferencePayload, FactNodePayload.ParameterPayload, FactNodePayload.LocalVariablePayload, FactNodePayload.FieldAccessPayload, FactNodePayload.EnumConstantPayload, FactNodePayload.LiteralPayload, FactNodePayload.NullLiteralPayload, FactNodePayload.MethodCallPayload, FactNodePayload.ConditionPayload, FactNodePayload.OutcomePayload, FactNodePayload.ExceptionPayload, FactNodePayload.HttpStatusPayload {
    record MethodPayload(String owner, String declarationSignature, boolean apiRoot) implements FactNodePayload {}
    record ConstructorPayload(String owner, String declarationSignature) implements FactNodePayload {}
    record ObjectCreationPayload(String typeName, int argumentCount) implements FactNodePayload {}
    record LambdaPayload(int parameterCount, boolean expressionBody) implements FactNodePayload {}
    record MethodReferencePayload(String identifier) implements FactNodePayload {}
    record ParameterPayload(String name, int index, String declaredType) implements FactNodePayload {}
    record LocalVariablePayload(String name, String declaredType) implements FactNodePayload {}
    record FieldAccessPayload(String fieldName, String rootExpressionKind) implements FactNodePayload {}
    record EnumConstantPayload(String declaringType, String constantName) implements FactNodePayload {}
    record LiteralPayload(String value, String literalKind) implements FactNodePayload {}
    record NullLiteralPayload() implements FactNodePayload {}
    record MethodCallPayload(String methodName, int argumentCount, boolean internalTraversal) implements FactNodePayload {}
    record ConditionPayload(String astKind, String rootOperator) implements FactNodePayload {}
    record OutcomePayload(String outcomeKind, String expressionKind) implements FactNodePayload {}
    record ExceptionPayload(String exceptionType, boolean handler) implements FactNodePayload {}
    record HttpStatusPayload(int statusCode, String statusName) implements FactNodePayload {}
}
