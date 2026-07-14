# Unit 03 Graph Rule Engine Logical Components

## 1. DefaultValidationCandidateDetector

### 책임

- method scope 안 condition과 branch outcome index 구성
- 명시적 throw outcome 탐지
- return outcome을 policy chain에 전달
- condition당 하나의 predicate candidate 생성
- predicate와 failure outcome Evidence 구성

### 의존성

- `FailureOutcomePolicy` 목록
- Unit 02 candidate ID와 Evidence mapper
- Fact graph integrity index

Rule registry와 business category에는 의존하지 않는다.

## 2. FailureOutcomePolicy

### 책임

- return 또는 custom outcome의 실패 여부 판정
- fallback extraction status와 diagnostic 반환

### 제약

- target code를 실행하지 않는다.
- simple name 단독으로 완전 판정을 반환하지 않는다.
- project-specific policy는 `PROJECT_EXTENSION` 구성에서만 등록한다.

## 3. GraphRule

### 책임

- predicate 주변 Fact 구조를 해석
- 0개 이상의 `BusinessRuleCandidate` 반환
- 사용한 type fallback을 status와 diagnostic에 반영

Rule은 registry, 다른 Rule과 engine mutable state를 알지 못한다.

## 4. RulePackRegistry

### 책임

- enabled pack filter
- ruleId uniqueness 검증
- immutable Rule 및 precedence snapshot
- ruleId canonical ordering
- 등록 Rule 수 제공

동적 plugin loading이나 runtime mutation은 제공하지 않는다.

## 5. DefaultGraphRuleEngine

### 책임

- detector 호출과 predicate snapshot
- registry의 Rule invocation orchestration
- Rule별 failure boundary 적용
- candidate 검증 및 수집
- dedup, ambiguity, precedence 처리 호출
- unresolved fallback 생성
- candidate aggregate와 execution report 조립

## 6. RuleInvocationBoundary

### 책임

- Rule 호출 전후 stats event 기록
- exception, null 및 invalid result 격리
- 안전한 execution diagnostic 생성
- 유효 candidate만 accumulator에 전달

catch 범위는 Rule invocation에 한정하며 JVM fatal error를 일반 Rule failure로 숨기지 않는다.

## 7. CandidateMatchKeyFactory

### 책임

- predicate ID와 ruleId 정규화
- Evidence node ID 및 role 중복 제거와 정렬
- 결정적 fingerprint와 match key 생성

snippet과 diagnostic message를 key 입력으로 사용하지 않는다.

## 8. RuleMatchAccumulator

### 책임

- exact duplicate hash index
- predicate별 candidate grouping
- matched 및 deduplicated counter event
- immutable candidate 목록 생성

Unit 02 `CandidateResolutionAccumulator`로 최종 결과를 전달한다.

## 9. RuleConflictAnnotator

### 책임

- predicate별 category ambiguity 검출
- pack tier 비교
- `AMBIGUOUS_RULE_MATCH`와 `LOWER_PRECEDENCE_MATCH` diagnostic 추가
- 후보 identity 및 의미 상태 보존

## 10. UnresolvedCandidateFactory

### 책임

- match 0개 predicate의 결정적 fallback 후보 생성
- Rule 없음, 미매칭, 전부 실패 원인별 diagnostic 선택
- Unit 02 invariant 준수

## 11. RuleExecutionStats

### 책임

- registered, evaluated, executed, matched, failed, deduplicated count
- engine invocation 내부 mutable event 수집
- 불변식 검증 후 immutable `RuleExecutionReport` 생성

thread-safe counter는 사용하지 않는다. 초기 engine은 single-thread invocation이다.

## 12. RuleExecutionDiagnosticCollector

### 책임

- Rule·policy failure diagnostic 수집
- `code + ruleId + predicateId` 기준 안정 정렬
- 동일 invocation failure의 중복 report 방지
- exception message 길이 제한

## 13. PBT Test Components

### GraphRuleEngineArbitraries

- 작은 valid Fact Graph와 method scope
- predicate 목록
- empty, single, duplicate, conflicting candidate를 반환하는 fake Rule
- 예외와 null을 반환하는 failure Rule
- Rule 등록 permutation
- precedence tier map

### Property Classes

- `GraphRuleEngineOrderingProperties`
- `CandidateDeduplicationProperties`
- `RuleFailureIsolationProperties`
- `RuleExecutionReportProperties`

## Component Dependency Rules

| Source | Allowed Target |
|:---|:---|
| Detector | Fact domain, failure policy, candidate support |
| Rule | Fact domain과 candidate domain |
| Registry | Rule Pack과 precedence only |
| Engine | detector, registry, invocation, accumulator, conflict, report |
| Invocation boundary | Rule, candidate validator, stats, diagnostic collector |
| Conflict annotator | candidate와 precedence snapshot |
| PBT | public engine contracts와 fake Rules |

engine domain과 SPI는 Spring, JavaParser, legacy graph와 기존 output model에 의존하지 않는다.

## Infrastructure Assessment

- cache: 불필요
- queue: 불필요
- database: 불필요
- remote service: 불필요
- parallel executor: 초기 PoC에서 불필요
- deployment change: 없음

모든 component는 현재 로컬 Java 프로세스에서 단일 thread로 동작한다.
