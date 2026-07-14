# Unit 03 Graph Rule Engine 도메인 모델

## Candidate Detection 계약

### MethodScope

```java
public record MethodScope(
    String graphId,
    Set<String> methodNodeIds
) {}
```

scope는 non-empty method node ID 집합이며 모두 같은 graph에 존재해야 한다.

### ValidationCandidateDetector

```java
public interface ValidationCandidateDetector {
    List<PredicateCandidate> detect(FactCodeGraph graph, MethodScope scope);
}
```

### FailureOutcomePolicy

```java
public interface FailureOutcomePolicy {
    String id();

    FailureOutcomeDecision evaluate(
        FactCodeGraph graph,
        FactNode condition,
        FactNode outcome
    );
}
```

### FailureOutcomeDecision

```java
public record FailureOutcomeDecision(
    boolean failure,
    ExtractionStatus extractionStatus,
    List<CandidateDiagnostic> diagnostics
) {}
```

기본 throw 판정은 detector core에 포함하고 return 기반 판정만 policy로 확장한다.

## Rule SPI

### GraphRule

```java
public interface GraphRule {
    String id();

    RuleLayer layer();

    List<BusinessRuleCandidate> match(
        FactCodeGraph graph,
        PredicateCandidate candidate
    );
}
```

### RuleLayer

```java
public enum RuleLayer {
    JAVA_LANGUAGE,
    JDK_IDIOM,
    SPRING,
    SPRING_DATA_JPA,
    PROJECT_EXTENSION
}
```

## Rule Pack

### RulePack

```java
public record RulePack(
    String id,
    boolean enabled,
    List<GraphRule> rules,
    RulePrecedence precedence
) {}
```

pack 안의 ruleId는 유일해야 한다. collection은 방어적으로 복사한다.

### RulePrecedence

```java
public record RulePrecedence(
    Map<String, Integer> tiers
) {
    public static final int DEFAULT_TIER = 0;
}
```

큰 tier가 더 높은 precedence다. tier가 없는 Rule은 0이며 같은 tier는 선호 관계가 없다. precedence는 후보 제거에 사용하지 않는다.

## 실행 계약

### GraphRuleEngine

```java
public interface GraphRuleEngine {
    GraphRuleEngineResult evaluate(
        FactCodeGraph graph,
        MethodScope scope
    );
}
```

pack과 detector는 engine constructor에서 주입한다.

### GraphRuleEngineResult

```java
public record GraphRuleEngineResult(
    CandidateResolutionResult candidates,
    RuleExecutionReport report
) {}
```

### RuleExecutionReport

```java
public record RuleExecutionReport(
    int registeredRules,
    int evaluatedPredicates,
    int executedRules,
    int matchedCandidates,
    int failedRuleExecutions,
    int deduplicatedCandidates,
    List<RuleExecutionDiagnostic> diagnostics
) {}
```

모든 count는 0 이상이며 collection은 immutable이다.

### RuleExecutionDiagnostic

```java
public record RuleExecutionDiagnostic(
    RuleExecutionDiagnosticSeverity severity,
    String code,
    String ruleId,
    String predicateCandidateId,
    String exceptionType,
    String message
) {}
```

### RuleExecutionDiagnosticSeverity

```java
public enum RuleExecutionDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}
```

최소 안정 diagnostic code는 다음과 같다.

- `RULE_EXECUTION_FAILED`
- `RULE_RETURNED_NULL`
- `RULE_RETURNED_INVALID_CANDIDATE`
- `FAILURE_POLICY_FAILED`
- `AMBIGUOUS_RULE_MATCH`
- `LOWER_PRECEDENCE_MATCH`
- `RULE_UNRESOLVED`

Candidate 자체에 연결되는 ambiguity, precedence와 unresolved code는 `CandidateDiagnostic`으로도 표현한다. 실행 실패의 원본 정보는 report diagnostic에 둔다.

## 내부 support 모델

### CandidateMatchKey

```java
record CandidateMatchKey(
    String predicateCandidateId,
    String ruleId,
    String evidenceFingerprint
) {}
```

### RuleExecutionStats

```java
final class RuleExecutionStats {
    // mutable counters, engine invocation 내부에서만 사용
}
```

mutable stats는 engine 실행 범위를 벗어나 노출하지 않고 마지막에 immutable report로 변환한다.

## 기존 모델과의 관계

- Unit 01 `FactCodeGraph`는 read-only 입력이다.
- Unit 02 `PredicateCandidate`, `BusinessRuleCandidate`, `CandidateResolutionResult`를 재사용한다.
- 기존 `ValidationCandidate`, `ApiCondition`, `NormalizedResult`는 변경하지 않는다.
- 구체 Rule과 pack은 Unit 04에서 이 SPI를 구현한다.
