# Unit 01 Fact Code Graph 도메인 모델

## Aggregate

### FactCodeGraph

```java
public record FactCodeGraph(
    String graphId,
    String apiMethodNodeId,
    List<FactNode> nodes,
    List<FactEdge> edges
) {}
```

생성 시 collection을 방어적으로 복사하고 node ID uniqueness와 edge 참조 무결성을 검증한다.

## Node 모델

### FactNode

```java
public record FactNode(
    String id,
    FactNodeType type,
    SourceRange sourceRange,
    String snippet,
    TypeResolution typeResolution,
    FactNodePayload payload
) {}
```

`type`과 payload subtype의 조합은 생성 시 검증한다.

### FactNodeType

```java
public enum FactNodeType {
    API_METHOD,
    METHOD,
    PARAMETER,
    LOCAL_VARIABLE,
    FIELD_ACCESS,
    ENUM_CONSTANT,
    METHOD_CALL,
    CONDITION,
    THROW,
    RETURN
}
```

### FactNodePayload

```java
public sealed interface FactNodePayload permits
    MethodPayload,
    ParameterPayload,
    LocalVariablePayload,
    FieldAccessPayload,
    EnumConstantPayload,
    MethodCallPayload,
    ConditionPayload,
    OutcomePayload {
}
```

주요 payload 필드는 다음과 같다.

| Payload | 필수 정보 |
|:---|:---|
| MethodPayload | owner, declaration signature, API 여부 |
| ParameterPayload | name, index, declared type |
| LocalVariablePayload | name, declared type |
| FieldAccessPayload | field name, root expression kind |
| EnumConstantPayload | declaring type, constant name |
| MethodCallPayload | method name, argument count, internal traversal 여부 |
| ConditionPayload | AST kind, root operator |
| OutcomePayload | throw/return kind, 표현식 kind |

API method와 일반 method는 같은 `MethodPayload`를 사용하되 `FactNodeType`과 `apiRoot`로 구분한다.

## Source 및 Type 모델

### SourceRange

```java
public record SourceRange(
    String relativePath,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn
) {}
```

### TypeResolution

```java
public record TypeResolution(
    TypeResolutionStatus status,
    String qualifiedType,
    String resolvedSignature,
    String diagnosticCode
) {}
```

### TypeResolutionStatus

```java
public enum TypeResolutionStatus {
    RESOLVED,
    PARTIAL,
    UNRESOLVED,
    NOT_APPLICABLE
}
```

`RESOLVED`일 때는 qualified type 또는 resolved signature 중 해당 node에 필요한 값이 존재해야 한다.

## Edge 모델

### FactEdge

```java
public record FactEdge(
    String id,
    String sourceNodeId,
    String targetNodeId,
    FactEdgeType type,
    int ordinal,
    String role
) {}
```

### FactEdgeType

```java
public enum FactEdgeType {
    CALLS,
    HAS_ARGUMENT,
    RECEIVER_OF,
    READS,
    ASSIGNED_FROM,
    COMPARES_WITH,
    OPERAND_OF,
    CONTROLS,
    THEN_OUTCOME,
    ELSE_OUTCOME,
    ORIGINATES_FROM
}
```

`ordinal`이 적용되지 않는 edge는 `-1`을 사용한다. `role`은 `LEFT`, `RIGHT`, `RECEIVER`, `ARGUMENT` 등 구조적 역할만 담고 비즈니스 의미를 담지 않는다.

## Traversal 모델

### FactGraphTraversalBudget

```java
public record FactGraphTraversalBudget(
    int maxDepth,
    int maxVisitedMethodsPerApi,
    int maxEdgesPerApi
) {
    public static FactGraphTraversalBudget defaults() {
        return new FactGraphTraversalBudget(5, 100, 300);
    }
}
```

모든 값은 1 이상이어야 하며 API root는 depth 0이다.

### FactGraphTraversalStats

```java
public record FactGraphTraversalStats(
    int maxObservedDepth,
    int visitedMethods,
    int edges
) {}
```

### FactGraphDiagnostic

```java
public record FactGraphDiagnostic(
    String apiMethod,
    DiagnosticSeverity severity,
    String reason,
    boolean truncated,
    FactGraphTraversalBudget configuredBudget,
    FactGraphTraversalStats observed,
    SourceRange sourceRange,
    String details
) {}
```

### FactGraphBuildResult

```java
public record FactGraphBuildResult(
    List<FactCodeGraph> graphs,
    List<FactGraphDiagnostic> diagnostics
) {}
```

## 서비스 계약

### FactCodeGraphBuilder

```java
public interface FactCodeGraphBuilder {
    FactGraphBuildResult build(
        StaticScanResult scanResult,
        RepositorySource repositorySource,
        FactGraphTraversalBudget budget
    );
}
```

구현체는 legacy `ValidationEvidenceGraphBuilder`를 호출하거나 그 결과를 역변환하지 않는다. 공통 source/type resolver는 재사용할 수 있다.

### FactNodeIdGenerator

```java
public interface FactNodeIdGenerator {
    String generate(
        FactNodeType type,
        String ownerMethodIdentity,
        SourceRange sourceRange,
        String semanticRole
    );
}
```

동일 canonical input에 대해 항상 동일한 값을 반환해야 한다.

