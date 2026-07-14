# Unit 03 Graph Rule Engine NFR Design Patterns

## 1. Detect-Then-Match Pipeline Pattern

### 목적

failure outcome 탐지와 business meaning 해석을 분리해 Rule 미매칭을 비검증 제어문으로 오인하지 않는다.

### 적용

1. `ValidationCandidateDetector`가 method scope 안의 failure-linked condition을 구조 후보로 생성한다.
2. engine은 detector 결과를 immutable predicate 목록으로 확정한다.
3. 활성 `GraphRule`을 predicate별로 실행한다.
4. 매칭 0개인 predicate는 `UNRESOLVED` 의미 후보로 보존한다.

detector는 Rule registry를 알지 못하고 Rule은 candidate를 새로 탐지하지 않는다.

## 2. Conservative Failure Outcome Policy Pattern

### 목적

정상 return과 조기 종료를 검증 실패로 오인하는 false positive를 통제한다.

### 적용

- typed branch edge로 연결된 `THROW`는 core detector가 처리한다.
- `RETURN`은 등록된 `FailureOutcomePolicy`가 판정한다.
- policy는 null이 아닌 decision을 반환해야 한다.
- 구조 또는 이름 fallback이면 `PARTIAL`과 diagnostic을 강제한다.
- policy 예외는 condition 단위 diagnostic으로 격리한다.

## 3. Isolated Rule Invocation Pattern

### 목적

한 Rule 구현의 결함이 전체 graph 분석을 중단하거나 다른 Rule 결과를 오염시키지 않게 한다.

### 적용

각 `(predicateId, ruleId)` invocation을 독립 경계에서 실행한다.

- 시작 전 executed counter 증가
- 정상 목록은 candidate별 invariant 검증
- exception, null 목록, null item, invalid candidate는 invocation failure
- failure counter와 typed report diagnostic 기록
- 이미 유효한 candidate는 유지
- 다음 Rule 실행 지속

exception stack trace와 target source 전체는 report에 저장하지 않는다.

## 4. Immutable Rule Registry Snapshot Pattern

### 목적

실행 중 pack이나 Rule 등록 변경으로 결과가 비결정적으로 변하지 않게 한다.

### 적용

- constructor에서 enabled pack과 Rule을 방어적 복사한다.
- 중복 ruleId를 실행 전에 검증한다.
- Rule은 ruleId 기준 canonical 순서로 snapshot한다.
- precedence tier도 immutable map으로 복사한다.
- invocation 중 registry mutation API를 제공하지 않는다.

## 5. Deterministic Exact Dedup Pattern

### 목적

동일 의미와 Evidence의 반복 반환만 제거하고 복수 의미 후보를 보존한다.

### 적용

`CandidateMatchKey` canonical input:

1. predicate candidate ID
2. ruleId
3. `nodeId + evidenceRole` 목록을 중복 제거 후 정렬한 fingerprint

key는 hash index에 한 번 삽입한다. 이미 존재하면 candidate를 제거하고 deduplicated counter를 증가시킨다. snippet, confidence와 message는 key에 포함하지 않는다.

## 6. Non-Destructive Precedence Pattern

### 목적

pack 선호도를 표현하되 유효한 대안 후보와 Evidence를 삭제하지 않는다.

### 적용

- predicate별 매칭 후보를 모두 수집한 뒤 tier를 비교한다.
- 최고 tier보다 낮은 후보에 `LOWER_PRECEDENCE_MATCH`를 추가한다.
- 서로 다른 category가 공존하면 관련 후보에 `AMBIGUOUS_RULE_MATCH`를 추가한다.
- candidate identity와 semantic status는 바꾸지 않는다.
- 같은 tier 또는 미등록 tier에는 precedence diagnostic을 추가하지 않는다.

## 7. Linear Index and Single Sort Pattern

### 목적

Rule과 candidate 증가 시 반복 중첩 검색과 과도한 정렬을 피한다.

### 적용

- scope method, Fact node와 edge는 실행 전 index로 구성한다.
- duplicate Rule과 candidate match key는 `Set`으로 검출한다.
- predicate별 후보는 `Map<predicateId, List<candidate>>`로 그룹화한다.
- 실행 중 전체 목록을 정렬하지 않는다.
- immutable snapshot에서 candidate ID와 diagnostic key 기준으로 한 번 정렬한다.

## 8. Trace-Derived Report Pattern

### 목적

실제 실행과 report counter가 분리되어 불일치하는 문제를 막는다.

### 적용

`RuleExecutionStats`의 변경은 invocation event에 붙인다.

- predicate detected → evaluated predicates
- Rule invocation start → executed rules
- valid unique candidate accepted → matched candidates
- invocation failure → failed executions
- duplicate key encountered → deduplicated candidates

최종 report 생성 시 count가 음수가 아닌지와 `executedRules <= evaluatedPredicates × registeredRules`를 검증한다.

## 9. Explicit Unresolved Fallback Pattern

### 목적

Rule 미등록, 미매칭과 전부 실패한 predicate가 결과에서 사라지지 않게 한다.

### 적용

- predicate별 unique valid candidate가 0개인지 마지막에 검사한다.
- Unit 02 factory로 결정적 `UNRESOLVED` 후보를 하나 생성한다.
- Rule이 없으면 `RULE_UNRESOLVED`, 전부 실패면 실행 실패를 요약한 candidate diagnostic을 사용한다.
- fallback 자체도 일반 candidate invariant와 dedup 검증을 통과해야 한다.

## 10. Static-Analysis Safety Pattern

- Rule SPI에는 Fact Graph와 candidate만 전달한다.
- workspace shell, network client, class loader, reflection context를 전달하지 않는다.
- target repository build와 code execution을 금지한다.
- exception과 source evidence는 최소 정보만 report에 보존한다.

## 11. Complementary PBT Pattern

example test는 throw/return detector, 예외 격리, ambiguity, precedence와 report 사례를 문서화한다. jqwik property는 작은 fake Rule과 candidate 조합에서 순서, 중복 수, failure injection과 counter invariant를 검증한다.

PBT 범위:

- `GraphRuleEngineOrderingProperties`
- `CandidateDeduplicationProperties`
- `RuleFailureIsolationProperties`
- `RuleExecutionReportProperties`

generator는 실제 Java source 문자열 fuzzing이 아니라 작은 immutable Fact Graph, predicate, fake Rule 결과와 precedence tier를 생성한다.

## NFR Design Compliance

- 성능: index와 single sort로 반영
- 신뢰성: isolated invocation과 unresolved fallback으로 반영
- 결정성: registry snapshot과 exact dedup으로 반영
- 비파괴성: precedence diagnostic으로 반영
- 관찰성: trace-derived report로 반영
- 안전성: 제한된 SPI와 static-analysis-only로 반영
- 테스트: example/PBT 상호 보완으로 반영
