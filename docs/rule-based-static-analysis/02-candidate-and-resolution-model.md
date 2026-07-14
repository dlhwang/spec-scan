# Work Unit 02: 후보, 해석 상태, Evidence 모델

## 목적

코드 구조 추출, 비즈니스 의미 해석, 요청 필드 연결을 하나의 `GENERIC` 상태에 섞지 않는 결과 모델을 정의한다.

## 분석 단계별 상태

하나의 `ResolutionStatus`만으로 모든 실패를 표현하지 않는다.

```java
enum ExtractionStatus {
    EXTRACTED,
    PARTIAL,
    UNSUPPORTED
}

enum SemanticStatus {
    RESOLVED,
    UNRESOLVED,
    NOT_BUSINESS_RULE
}

enum TargetResolutionStatus {
    RESOLVED,
    NOT_APPLICABLE,
    UNRESOLVED
}
```

`UNSUPPORTED`는 구조 추출 단계의 상태다. 의미 룰이 없다는 것과 구분한다.

## Predicate 구조 분류

`PredicateType`은 관찰 가능한 코드 구조만 나타낸다.

```java
enum PredicateType {
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

`REPOSITORY_LOOKUP`처럼 프레임워크 의미가 포함된 이름은 PredicateType에 넣지 않는다. `findById().orElseThrow()`의 구조는 `LOOKUP_CHAIN`, 의미는 Rule Pack에서 `EXISTENCE`로 결정한다.

## 비즈니스 분류

상위 taxonomy는 리포팅과 출력 호환을 위한 선택적 분류다.

```java
enum BusinessRuleCategory {
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

새 Rule 추가 시 Enum 수정이 필수가 되어서는 안 된다. 구체적인 정체성은 안정적인 `ruleId`에 있다.

## 후보 모델

```java
record BusinessRuleCandidate(
    String candidateId,
    String ruleId,
    PredicateType predicateType,
    BusinessRuleCategory category,
    ExtractionStatus extractionStatus,
    SemanticStatus semanticStatus,
    TargetResolutionStatus targetStatus,
    NormalizedConstraint constraint,
    double confidence,
    List<EvidenceRef> evidence,
    List<Diagnostic> diagnostics
) {}
```

## Constraint 모델

입력 제약과 런타임 의존 조건을 구분한다.

```java
enum ConstraintKind {
    INPUT_LITERAL,
    INPUT_TO_DOMAIN,
    RUNTIME_DEPENDENT,
    CONTROL_FLOW_ONLY
}

record NormalizedConstraint(
    ConstraintKind kind,
    String targetPath,
    String operator,
    List<String> expectedValues,
    String expectedSource
) {}
```

소스에서 기대값을 얻지 못한 경우 빈 배열로 의미를 숨기기보다 `kind`와 `expectedSource`를 명시한다.

## Evidence 모델

```java
record EvidenceRef(
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

`EvidenceRole` 예시는 `PREDICATE`, `CALL`, `INPUT_ORIGIN`, `DOMAIN_ORIGIN`, `FAILURE_OUTCOME`이다.

## 상태 결정 규칙

- 구조를 추출했지만 Rule이 없으면 `EXTRACTED + UNRESOLVED`다.
- 타입 해석이 실패했지만 제한적 구조를 얻으면 `PARTIAL`이다.
- 의미는 확인했지만 targetPath를 연결하지 못하면 `RESOLVED + target UNRESOLVED`다.
- 파서 또는 지원 범위 밖 구조이면 `UNSUPPORTED`다.
- `NOT_BUSINESS_RULE`은 명시적인 분류 근거가 있을 때만 사용한다.
- 단순 Rule 미매칭을 `NOT_BUSINESS_RULE`로 바꾸지 않는다.

## 완료 기준

- `GENERIC` 없이 네 가지 대표 실패 상황을 서로 구분할 수 있다.
- 의미가 해석됐지만 target만 미해석인 결과를 표현할 수 있다.
- 모든 후보가 최소 하나의 Evidence와 진단 정보를 가진다.
- 소스에서 확인하지 않은 값은 constraint에 사실처럼 들어가지 않는다.
- 직렬화 결과의 null, 빈 목록, 미해석 의미가 문서화돼 있다.

## 비목표

- 모든 BusinessRuleCategory 확정
- 기존 API 출력 스키마 즉시 교체
- Rule 구현

