package io.atworks.specscan.analysis.domain.fact;

public sealed interface FactNodePayload permits FactNodePayload.MethodPayload, FactNodePayload.ConstructorPayload, FactNodePayload.ObjectCreationPayload, FactNodePayload.LambdaPayload, FactNodePayload.MethodReferencePayload, FactNodePayload.ParameterPayload, FactNodePayload.LocalVariablePayload, FactNodePayload.TypePayload, FactNodePayload.SchemaFieldPayload, FactNodePayload.AnnotationPayload, FactNodePayload.FieldAccessPayload, FactNodePayload.EnumConstantPayload, FactNodePayload.LiteralPayload, FactNodePayload.NullLiteralPayload, FactNodePayload.MethodCallPayload, FactNodePayload.ResponseFactoryPayload, FactNodePayload.ConditionPayload, FactNodePayload.OutcomePayload, FactNodePayload.ExceptionPayload, FactNodePayload.HttpStatusPayload {
    record MethodPayload(String owner, String declarationSignature, boolean apiRoot) implements FactNodePayload {}
    record ConstructorPayload(String owner, String declarationSignature) implements FactNodePayload {}
    record ObjectCreationPayload(String typeName, int argumentCount) implements FactNodePayload {}
    record LambdaPayload(int parameterCount, boolean expressionBody) implements FactNodePayload {}
    record MethodReferencePayload(String identifier) implements FactNodePayload {}
    record ParameterPayload(String name, int index, String declaredType, String bindingLocation,
                            String bindingName, boolean required) implements FactNodePayload {
        public ParameterPayload(String name, int index, String declaredType) {
            this(name, index, declaredType, "UNKNOWN", name, false);
        }
        public ParameterPayload(String name, int index, String declaredType, String bindingLocation,
                                boolean required) {
            this(name, index, declaredType, bindingLocation, name, required);
        }
    }
    record LocalVariablePayload(String name, String declaredType) implements FactNodePayload {}
    record TypePayload(String declaredType, String qualifiedType, boolean primitive,
                       boolean collection, String elementDeclaredType) implements FactNodePayload {}
    record SchemaFieldPayload(String javaName, String jsonName, String declaredType,
                              boolean primitive, String defaultValue) implements FactNodePayload {}
    record AnnotationPayload(String annotationType,
                             java.util.Map<String, Object> attributes) implements FactNodePayload {
        public AnnotationPayload {
            attributes = java.util.Map.copyOf(attributes == null ? java.util.Map.of() : attributes);
        }
    }
    record FieldAccessPayload(String fieldName, String rootExpressionKind) implements FactNodePayload {}
    record EnumConstantPayload(String declaringType, String constantName) implements FactNodePayload {}
    record LiteralPayload(String value, String literalKind) implements FactNodePayload {}
    record NullLiteralPayload() implements FactNodePayload {}
    record MethodCallPayload(String methodName, int argumentCount, boolean internalTraversal) implements FactNodePayload {}
    record ResponseFactoryPayload(String factoryMethod, Integer statusCode,
                                  String statusExpression) implements FactNodePayload {}
    record ConditionPayload(String astKind, String rootOperator) implements FactNodePayload {}
    record OutcomePayload(String outcomeKind, String expressionKind) implements FactNodePayload {}
    record ExceptionPayload(String exceptionType, boolean handler) implements FactNodePayload {}
    record HttpStatusPayload(int statusCode, String statusName) implements FactNodePayload {}
}
