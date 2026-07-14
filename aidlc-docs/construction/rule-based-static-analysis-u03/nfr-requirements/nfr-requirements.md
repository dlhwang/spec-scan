# Unit 03 Graph Rule Engine NFR Requirements

## 범위

candidate detector, failure outcome policy, GraphRule SPI, Rule Pack, engine orchestration, 중복·충돌·precedence 처리와 execution report에 적용한다. 구체 Rule Pack과 기존 output pipeline에는 동작 변경을 요구하지 않는다.

## 성능 및 확장성

### NFR-GRE-01 예측 가능한 실행 복잡도

predicate 수를 `P`, 활성 Rule 수를 `R`, 반환된 총 후보 수를 `M`이라 할 때 기본 Rule 실행은 `P × R`회 이하여야 한다. 중복과 충돌 검사는 hash index를 사용하고 결과 안정 정렬을 제외한 후보 처리는 `O(M)`을 목표로 한다.

### NFR-GRE-02 Rule 중복 실행 금지

같은 engine invocation에서 동일 predicate와 동일 ruleId 조합을 두 번 실행하지 않는다. pack 간 중복 ruleId는 실행 전에 검출하고 typed diagnostic 또는 구성 거부로 처리한다.

### NFR-GRE-03 안정 정렬 단일 적용

Rule 실행 중 매번 전체 결과를 정렬하지 않는다. candidate 및 report diagnostic 정렬은 immutable snapshot 생성 경계에서 한 번 수행한다.

### NFR-GRE-04 초기 측정 기준

초기 PoC에서는 wall-clock SLA를 강제하지 않는다. 검증 fixture마다 predicate 수, 등록 Rule 수, 실제 실행 수, 반환 후보 수, 중복 제거 수와 execution duration을 Evidence로 기록한다.

## 신뢰성 및 부분 성공

### NFR-GRE-05 Rule 실행 격리

- 한 Rule의 runtime exception이 다른 Rule 또는 predicate 실행을 중단시키지 않는다.
- null 목록, null candidate와 invariant 위반 candidate를 해당 Rule failure로 격리한다.
- 실패 정보는 ruleId와 predicate ID를 가진 execution diagnostic으로 남긴다.

### NFR-GRE-06 Policy 실행 격리

return 기반 `FailureOutcomePolicy` 예외는 해당 outcome 판정에만 영향을 주며 다른 condition detection을 계속한다. policy failure diagnostic을 report에 남긴다.

### NFR-GRE-07 미매칭 보존

활성 Rule 0개, 모든 Rule 미매칭 또는 모든 Rule 실패 상황에서 predicate 손실은 0건이어야 한다. 각 predicate는 하나의 `UNRESOLVED` 의미 결과를 가져야 한다.

### NFR-GRE-08 Invalid Graph 조기 거부

입력 Fact Graph 또는 method scope의 참조 무결성이 깨졌으면 Rule 실행 전에 거부한다. 일부 Rule 실행 후 graph 오류를 발견해 partial 결과를 반환하지 않는다.

## 결정성 및 재현성

### NFR-GRE-09 등록 순서 독립성

동일 Rule 집합, pack precedence와 입력 graph에서 Rule 등록 순서를 바꿔도 정규화된 candidate identity, candidate diagnostic 및 report diagnostic 집합이 동일해야 한다.

### NFR-GRE-10 결정적 Exact Dedup

중복 key는 다음 canonical input으로 만든다.

- predicate candidate ID
- ruleId
- 정렬된 evidence node ID와 role

snippet, confidence, diagnostic message, collection iteration 순서는 key에 포함하지 않는다.

### NFR-GRE-11 비파괴 Precedence

precedence tier 변경은 exact duplicate가 아닌 candidate identity를 제거하지 않는다. 낮은 tier 표시는 안정 diagnostic code로만 반영한다.

### NFR-GRE-12 Immutable 결과

Rule 반환 목록, pack 규칙 목록과 report diagnostic의 외부 변경이 engine result를 바꾸지 않아야 한다.

## 관찰 가능성과 정합성

### NFR-GRE-13 Report Counter 정확성

다음 counter는 실행 trace와 정확히 일치해야 한다.

- registered rules
- evaluated predicates
- executed rules
- matched candidates
- failed rule executions
- deduplicated candidates

count는 음수일 수 없고 overflow가 예상되는 입력은 구성 또는 실행 전 거부한다.

### NFR-GRE-14 안정 Diagnostic Code

최소 다음 code를 안정된 계약으로 제공한다.

- `RULE_EXECUTION_FAILED`
- `RULE_RETURNED_NULL`
- `RULE_RETURNED_INVALID_CANDIDATE`
- `DUPLICATE_RULE_ID`
- `FAILURE_POLICY_FAILED`
- `AMBIGUOUS_RULE_MATCH`
- `LOWER_PRECEDENCE_MATCH`
- `RULE_UNRESOLVED`
- `INVALID_METHOD_SCOPE`

### NFR-GRE-15 Diagnostic 최소화

exception stack trace나 전체 source를 결과에 저장하지 않는다. exception type, 제한된 message, ruleId, predicate ID와 관련 node ID만 보존한다.

## 정확성

### NFR-GRE-16 Failure Outcome False Positive 통제

기본 detector는 명시적 throw branch만 자동 인정한다. policy 없는 일반 return은 failure candidate로 생성하지 않는다.

### NFR-GRE-17 타입 Fallback 투명성

resolved type/signature가 아닌 구조 또는 제한된 이름 heuristic을 사용한 Rule 후보는 `PARTIAL`이며 fallback diagnostic을 가져야 한다. simple name 단독 semantic resolved 결과는 0건이어야 한다.

### NFR-GRE-18 복수 매칭 보존

같은 predicate의 서로 다른 ruleId 또는 evidence 조합은 모두 보존한다. category 충돌에는 ambiguity diagnostic 제공률 100%를 요구한다.

## 보안 및 안전

### NFR-GRE-19 Static Analysis Only

engine, Rule과 policy는 대상 repository의 class loading, reflection, build, test, run, annotation processor 또는 script를 실행하지 않는다.

### NFR-GRE-20 Rule 입력 제한

Rule은 immutable Fact Graph와 candidate metadata만 입력으로 받는다. workspace shell, network client 또는 mutable parser context를 SPI에 제공하지 않는다.

## 테스트 품질

### NFR-GRE-21 Example-Based Tests

다음 사례를 JUnit assertion으로 고정한다.

- throw branch detector와 일반 return 제외
- 같은 종류 검증 두 개 모두 탐지
- Rule 0개와 모두 미매칭
- 한 Rule 복수 후보 반환
- Rule exception, null 목록과 invalid candidate 격리
- exact duplicate 제거
- category ambiguity와 precedence diagnostic
- report counter 일치

### NFR-GRE-22 Property-Based Tests

jqwik를 사용해 최소 다음 property를 검증한다.

- Rule 등록 순서 permutation에 대한 결과 불변성
- duplicate 후보 반복 수와 무관한 dedup 결과
- 임의 한 Rule 실패가 다른 Rule 후보 집합에 영향을 주지 않음
- precedence tier 변경이 candidate identity 집합을 삭제하지 않음
- report counter가 생성된 execution trace와 일치함

### NFR-GRE-23 재현 가능한 PBT

- jqwik shrinking과 seed 출력을 유지한다.
- 순서 property는 최소 200회 실행한다.
- dedup 및 counter property는 각각 최소 300회 실행한다.
- 발견된 결함은 축소 입력의 example regression으로 추가한다.

## PBT Compliance

| Rule | 상태 | 적용 내용 |
|:---|:---|:---|
| PBT-01 | Compliant | 순서, dedup, isolation, precedence, counter invariant 식별 |
| PBT-02 | Planned | 작은 Rule 및 candidate collection generator 사용 |
| PBT-03 | Planned | 결과 집합과 report invariant 검증 |
| PBT-04 | Planned | 같은 engine input의 idempotency 검증 |
| PBT-05 | N/A | 별도 reference engine 없음 |
| PBT-06 | N/A | 외부 mutable state machine이 아님 |
| PBT-07 | Planned | domain-specific fake Rule generator 제공 |
| PBT-08 | Planned | shrinking 및 seed 재현 유지 |
| PBT-09 | Compliant | 기존 jqwik 1.7.4 재사용 |
| PBT-10 | Compliant | example-based test와 PBT 병행 |

## 초기 수용 기준

- 대표 detector 및 engine example test 전체 통과
- PBT property 전체 통과
- Rule 예외 격리율 100%
- 등록 순서 permutation 결과 차이 0건
- exact duplicate 잔존 0건
- 미매칭 predicate 손실 0건
- report counter 불일치 0건
- 기존 전체 test suite regression 0건
