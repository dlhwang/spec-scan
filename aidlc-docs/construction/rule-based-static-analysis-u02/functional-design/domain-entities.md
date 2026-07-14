# Unit 02 후보와 해석 모델 도메인 모델

## 구조 후보

### PredicateCandidate

```java
public record PredicateCandidate(
    String candidateId,
    String graphId,
    String conditionNodeId,
    PredicateType predicateType,
    ExtractionStatus extractionStatus,
    List<EvidenceRef> evidence,
    List<CandidateDiagnostic> diagnostics
) {}
```

각 Fact Graph `CONDITION` node당 하나를 생성한다. 비즈니스 `ruleId`, category, target은 포함하지 않는다.

### PredicateType

```java
public enum PredicateType {
    COMPARISON,
    BOOLEAN_CALL,
    NULL_CHECK,
    COLLECTION_MEMBERSHIP,
    LOOKUP_CHAIN,
    ENUM_COMPARISON,
    COMPOSITE,
    UNKNOWN
}
```

## 의미 후보

### BusinessRuleCandidate

```java
public record BusinessRuleCandidate(
    String candidateId,
    String predicateCandidateId,
    String ruleId,
    BusinessRuleCategory category,
    ExtractionStatus extractionStatus,
    SemanticStatus semanticStatus,
    TargetResolutionStatus targetStatus,
    NormalizedConstraint constraint,
    double confidence,
    List<EvidenceRef> evidence,
    List<CandidateDiagnostic> diagnostics
) {}
```

`ruleId`와 `constraint`는 상태에 따라 null일 수 있다. collection은 null을 허용하지 않는다.

### 상태 enum

```java
public enum ExtractionStatus {
    EXTRACTED,
    PARTIAL,
    UNSUPPORTED
}

public enum SemanticStatus {
    RESOLVED,
    UNRESOLVED,
    NOT_BUSINESS_RULE
}

public enum TargetResolutionStatus {
    RESOLVED,
    NOT_APPLICABLE,
    UNRESOLVED
}
```

### BusinessRuleCategory

```java
public enum BusinessRuleCategory {
    UNKNOWN,
    EXISTENCE,
    AUTHENTICATION,
    AUTHORIZATION,
    STATE_PRECONDITION,
    VERSION_CONSISTENCY,
    UNIQUENESS,
    RANGE,
    TEMPORAL,
    CAPACITY,
    INVARIANT,
    OTHER
}
```

`UNKNOWN`은 미해석 상태이고 `OTHER`는 해석된 Rule의 상위 taxonomy fallback이다.

## Constraint 모델

### ConstraintKind

```java
public enum ConstraintKind {
    INPUT_LITERAL,
    INPUT_TO_DOMAIN,
    RUNTIME_DEPENDENT,
    CONTROL_FLOW_ONLY
}
```

### NormalizedConstraint

```java
public record NormalizedConstraint(
    ConstraintKind kind,
    String targetPath,
    String operator,
    List<String> expectedValues,
    String expectedSource
) {}
```

- `targetPath`는 target `RESOLVED`일 때만 non-null이다.
- `operator`는 실제 operator를 정규화할 수 있을 때만 non-null이다.
- `expectedValues`는 항상 non-null이며 관찰된 값이 없으면 빈 목록이다.
- `expectedSource`는 source 표현 또는 runtime origin을 확인할 수 있을 때만 non-null이다.

## Evidence 모델

### EvidenceRef

```java
public record EvidenceRef(
    String nodeId,
    String filePath,
    int startLine,
    int startColumn,
    int endLine,
    int endColumn,
    EvidenceRole role,
    String snippet
) {}
```

### EvidenceRole

```java
public enum EvidenceRole {
    PREDICATE,
    CALL,
    INPUT_ORIGIN,
    DOMAIN_ORIGIN,
    FAILURE_OUTCOME
}
```

`snippet`은 원문 일부이며 의미 해석이나 구조 복원 입력으로 사용하지 않는다.

## Diagnostic 모델

### CandidateDiagnostic

```java
public record CandidateDiagnostic(
    CandidateDiagnosticSeverity severity,
    String code,
    String message,
    String nodeId
) {}
```

### CandidateDiagnosticSeverity

```java
public enum CandidateDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}
```

`code`는 기계 판독 가능한 안정적 식별자이고 `message`는 세부 설명이다. 관련 Fact가 있을 때 `nodeId`를 사용하며 전역 원인이면 null을 허용한다.

## 결과 Aggregate

### CandidateResolutionResult

```java
public record CandidateResolutionResult(
    String graphId,
    List<PredicateCandidate> predicates,
    List<BusinessRuleCandidate> businessRules,
    List<CandidateDiagnostic> diagnostics
) {}
```

aggregate 생성 시 다음을 검증한다.

- predicate와 business rule candidate ID uniqueness
- 의미 후보의 predicate 참조 무결성
- Evidence non-empty 규칙
- 상태별 Diagnostic 필수 규칙
- semantic과 category, ruleId 조합
- target status와 targetPath 조합

## Identity 서비스

### CandidateIdGenerator

```java
public interface CandidateIdGenerator {
    String forPredicate(String graphId, String conditionNodeId);

    String forBusinessRule(String predicateCandidateId, String ruleId, SemanticStatus status);
}
```

매칭된 의미 후보는 nonblank `ruleId`를 사용한다. 미해석과 비비즈니스 결과는 status별 예약 discriminator를 사용하며 동일 canonical input에 항상 동일 ID를 반환한다.

## 기존 모델과의 관계

- `ValidationCandidate`, `ApiCondition`, `NormalizedResult`는 그대로 유지한다.
- 신규 모델에서 기존 모델로의 변환은 Unit 02에 포함하지 않는다.
- 신규 모델은 Unit 01 Fact Graph를 참조하지만 Fact domain package에 비즈니스 의미를 역으로 추가하지 않는다.
