# Unit 03 Graph Rule Engine 규칙

## Detector 규칙

### GRE-01 명시적 failure evidence

기본 detector는 condition과 typed branch edge로 연결된 `THROW`를 failure outcome으로 인정한다. source snippet의 `throw` 문자열 검색으로 관계를 복원하지 않는다.

### GRE-02 RETURN 기본 제외

일반 `RETURN`은 실패로 간주하지 않는다. `FailureOutcomePolicy`가 명시적으로 판정한 경우에만 후보 근거가 된다.

### GRE-03 condition당 구조 후보 하나

한 condition이 여러 failure outcome에 연결돼도 `PredicateCandidate`는 하나만 생성하고 모든 관련 outcome을 Evidence로 보존한다.

### GRE-04 method scope 준수

detector는 전달된 method scope 밖의 condition을 이름, 인접 source range 또는 비즈니스 추측으로 포함하지 않는다.

## Rule SPI 규칙

### GRE-05 다중 결과 허용

`GraphRule.match`는 null이 아닌 목록을 반환하며 0개 이상의 후보를 포함할 수 있다. `Optional`이나 단일 후보 반환으로 제한하지 않는다.

### GRE-06 중앙 분기 금지

새 Rule 추가는 `GraphRule` 구현과 pack 등록으로 수행한다. engine core의 ruleId별 `if-else`, `switch` 또는 RuleClass 수정은 금지한다.

### GRE-07 계층의 정직한 표현

framework Rule은 해당 `RuleLayer`로 등록한다. Spring 또는 JPA 구조를 범용 `JAVA_LANGUAGE` Rule로 표시하지 않는다.

### GRE-08 simple name 단독 확정 금지

simple method name 하나만 일치하는 경우 semantic `RESOLVED`를 반환하지 않는다. 구조 근거가 있더라도 resolved type/signature가 없으면 `PARTIAL` 및 diagnostic을 사용한다.

## 실행 격리 규칙

### GRE-09 Rule 예외 격리

Rule 예외는 `ruleId`, predicate ID, exception type, 안전한 message를 포함한 실행 diagnostic으로 변환한다. 다른 Rule이나 predicate 실행을 중단하지 않는다.

### GRE-10 null 및 invalid 결과 격리

null 목록, null candidate 또는 invariant를 위반한 후보는 해당 Rule contract failure로 처리하고 결과에 포함하지 않는다.

### GRE-11 등록 없는 후보 보존

활성 Rule이 없거나 모두 미매칭·실패하면 predicate를 삭제하지 않고 `UNRESOLVED` 의미 후보를 생성한다.

## 중복 및 충돌 규칙

### GRE-12 exact duplicate identity

동일 predicate의 `ruleId + sorted(nodeId + evidenceRole)`가 같을 때만 duplicate로 제거한다. confidence 차이나 diagnostic message 차이를 별도 의미 후보의 근거로 사용하지 않는다.

### GRE-13 서로 다른 Rule 보존

같은 predicate에서 서로 다른 ruleId가 매칭되면 category가 같아도 결과를 모두 보존한다.

### GRE-14 ambiguity 공개

서로 다른 category 후보가 공존하면 관련 후보에 `AMBIGUOUS_RULE_MATCH` diagnostic을 추가한다. 조용히 하나를 선택하지 않는다.

### GRE-15 precedence 비파괴성

pack precedence는 낮은 후보에 `LOWER_PRECEDENCE_MATCH`를 표시할 뿐 후보를 제거하지 않는다. 등록 순서나 confidence를 precedence로 간주하지 않는다.

## 결정성 및 report 규칙

### GRE-16 등록 순서 독립 결과

동일 pack 구성에서 Rule 목록 순서가 달라도 정규화된 candidate identity와 diagnostic 집합은 동일해야 한다.

### GRE-17 안정 정렬

predicate ID, ruleId, evidence fingerprint와 diagnostic code를 기준으로 결과를 안정 정렬한다.

### GRE-18 report counter 일치

- evaluated predicate 수는 detector 결과 수와 같다.
- executed Rule 수는 실제 호출 수다.
- failed Rule 수는 exception, null 또는 invalid result 호출 수다.
- matched candidate 수는 중복 제거 후 유효 Rule 후보 수다.
- deduplicated 수는 제거한 exact duplicate 수다.

## 안전 규칙

### GRE-19 Static Analysis Only

Rule과 policy는 target class loading, reflection, build, test, run 또는 script 실행을 요청할 수 없다.

### GRE-20 Diagnostic 최소화

Rule exception stack trace와 전체 source를 결과에 저장하지 않는다. exception type, 제한된 message, ruleId, predicate ID와 Evidence node ID만 남긴다.

## Property-Based Testing 후보

- Rule 등록 순서 변경이 정규화 결과를 바꾸지 않는다.
- duplicate 후보를 여러 번 반환해도 결과는 하나다.
- 한 Rule 예외가 다른 Rule의 후보 수에 영향을 주지 않는다.
- precedence tier 변경은 후보 identity 집합을 삭제하지 않는다.
- report counter는 생성된 실행 trace와 일치한다.
