# U01 Generalization Baseline — Business Rules

## 1. 규칙 표기

- **MUST**: 위반 시 G01 실패
- **MUST NOT**: 금지 조건; 발견 즉시 실패
- **SHOULD**: 정당한 예외와 diagnostic이 있을 때만 미충족 허용

## 2. Corpus Manifest Rules

### BR-U01-001 — Corpus ID uniqueness

모든 `corpusId`는 manifest version 안에서 유일해야 한다. 중복은 `DUPLICATE_CORPUS_ID`로 실패한다.

### BR-U01-002 — Supported source kind

source kind는 `CHECKED_IN_FIXTURE` 또는 `LOCAL_SNAPSHOT`이어야 한다. evaluation 시 remote clone은 허용하지 않는다.

### BR-U01-003 — Source integrity

- `CHECKED_IN_FIXTURE`는 content digest를 가져야 한다.
- `LOCAL_SNAPSHOT`은 content digest를 가져야 하며 VCS metadata가 있으면 commit reference도 기록한다.
- 실제 content가 digest와 다르면 `CORPUS_INTEGRITY_MISMATCH`다.

### BR-U01-004 — Required corpus

required corpus가 unavailable, invalid 또는 integrity mismatch이면 전체 G01을 실패시켜야 한다. denominator에서 제외하여 계속할 수 없다.

### BR-U01-005 — Optional corpus

optional corpus가 unavailable이면 `CORPUS_UNAVAILABLE` diagnostic과 제외 사유를 남기고 계속할 수 있다. 실행 가능한 것으로 잘못 집계해서는 안 된다.

### BR-U01-006 — Spring MVC scope

각 corpus는 source root와 Spring MVC endpoint scope를 가져야 한다. project-specific controller allowlist가 아니라 annotation 기반 discovery 결과를 scope와 대조한다.

### BR-U01-007 — Corpus diversity

최종 manifest는 다음을 만족해야 한다.

- 5~10개 corpus
- repository별 약 6~10개 endpoint 또는 동등 복잡도
- 서로 다른 package, aggregate, field와 method naming
- rename/mutation holdout 최소 1세트 이상

## 3. Observation Rules

### BR-U01-010 — Observation is fact, not truth

현재 Java output은 `ObservedSnapshot`에 기록되는 사실일 뿐 자동으로 approved expectation이 되지 않는다.

### BR-U01-011 — Canonical semantics

semantic equality는 category/effect/constraint/status/diagnostic/evidence fingerprint를 사용한다. elapsed time, memory와 internal trace ordering은 equality에서 제외한다.

### BR-U01-012 — Deterministic ordering

같은 source revision과 configuration의 snapshot은 동일 ordering과 semantic content를 생성해야 한다.

### BR-U01-013 — No silent omission

graph build failure, unsupported endpoint와 candidate resolution failure를 snapshot에서 제거해서는 안 된다. diagnostic 또는 coverage state로 남겨야 한다.

### BR-U01-014 — Production isolation

baseline runner는 production CLI/정상 scan의 성공·실패·output을 변경해서는 안 된다. YAML/bootstrap dependency도 추가해서는 안 된다.

## 4. Identity Rules

### BR-U01-020 — Exact identity scope

`ObservationIdentity`는 같은 source revision의 added/removed/changed 비교에만 사용한다.

### BR-U01-021 — Scenario identity scope

rename/mutation variant는 `ScenarioIdentity`로 비교한다. raw node ID, source line, concrete field name과 predicate candidate ID를 cross-variant key로 사용하면 안 된다.

### BR-U01-022 — Identity collision

같은 scope에서 두 observation이 동일 exact identity 또는 scenario occurrence를 주장하면 `IDENTITY_COLLISION`으로 실패한다. 임의 ordinal을 뒤늦게 부여해 충돌을 숨기지 않는다.

### BR-U01-023 — Scenario mapping completeness

holdout으로 표시한 원본과 variant는 같은 `scenarioId` 및 대응되는 `semanticRole/occurrenceKey`를 가져야 한다.

## 5. Disposition Rules

### BR-U01-030 — Explicit disposition

모든 baseline entry는 `PRESERVE`, `REPLACE`, `UNSUPPORTED` 중 정확히 하나를 가져야 한다. 기본값과 자동 추론은 없다.

### BR-U01-031 — Rationale required

모든 disposition에는 비어 있지 않은 rationale이 필요하다.

### BR-U01-032 — Preserve expectation

`PRESERVE`는 승인된 canonical observation을 명시해야 하며 승인되지 않은 added/removed/changed diff가 있으면 실패한다.

### BR-U01-033 — Replace expectation

`REPLACE`는 corrected semantic expectation을 가져야 한다. 현재 잘못된 observation 자체를 corrected expectation으로 복사할 수 없다.

### BR-U01-034 — Unsupported expectation

`UNSUPPORTED`는 expected status/diagnostic을 가져야 하며 normalized business meaning 생성을 허용하지 않는다.

### BR-U01-035 — New observation

baseline에 없는 신규 observation은 자동 승인하지 않고 `BASELINE_ENTRY_UNLABELED` change proposal로 분류한다.

### BR-U01-036 — Delegated legacy classification

graph evidence 없이 `currentUser`, `order.state`, 특정 permission/operator를 합성한 legacy delegated observation은 `PRESERVE`로 분류하면 안 된다. 승인된 rationale과 corrected expectation을 가진 `REPLACE` 또는 명시적 `UNSUPPORTED`여야 한다.

## 6. Coverage Rules

### BR-U01-040 — Separate coverage dimensions

graph coverage와 semantic coverage를 하나의 비율로 합치면 안 된다.

### BR-U01-041 — Denominator transparency

모든 coverage는 numerator, denominator와 제외 목록을 함께 제공해야 한다.

### BR-U01-042 — Required corpus exclusion prohibited

required corpus 실패를 denominator에서 제외하여 coverage를 높여서는 안 된다.

### BR-U01-043 — Incomplete states retained

`UNCLASSIFIED`, `UNRESOLVED`, `PARTIAL_ANALYSIS`와 graph diagnostic은 별도 count로 유지한다.

### BR-U01-044 — No premature success threshold

U01은 YAML semantic coverage의 Go 기준을 정하지 않는다. corpus/labeling/determinism/측정 가능성만 G01 조건으로 사용한다.

## 7. Diff Rules

### BR-U01-050 — Exact diff classification

동일 source 비교는 `ADDED`, `REMOVED`, `CHANGED`, `UNCHANGED`를 구분해야 한다.

### BR-U01-051 — Semantic comparison

formatting, serialization order와 metric 변화만으로 `CHANGED`를 생성하지 않는다. canonical semantic field 변화만 의미 차이다.

### BR-U01-052 — Disposition-aware verdict

- `PRESERVE`: canonical expectation과 exact match
- `REPLACE`: corrected expectation과 match
- `UNSUPPORTED`: expected diagnostic/status와 match하며 normalized meaning 없음

### BR-U01-053 — Holdout comparison

rename/mutation 비교에서는 concrete target 문자열 동일성을 요구하지 않는다. scenario가 정의한 expected rename과 semantic shape을 비교한다.

## 8. Re-baseline Rules

### BR-U01-060 — No automatic overwrite

runner가 baseline manifest를 자동 덮어쓰는 기능은 금지한다.

### BR-U01-061 — Proposal required

baseline 변경은 before/after semantic diff, 영향 corpus/scenario/disposition과 rationale을 포함한 `BaselineChangeProposal`을 먼저 생성해야 한다.

### BR-U01-062 — Explicit approval

승인 상태가 아닌 proposal은 새 baseline version을 만들 수 없다.

### BR-U01-063 — Version history

승인된 baseline은 새 version으로 저장하고 이전 version을 삭제하거나 제자리 수정하지 않는다.

### BR-U01-064 — Preserve regression cannot auto-update

`PRESERVE` 변화는 Java output 변화라는 이유만으로 자동 승인할 수 없다.

## 9. Error and Diagnostic Rules

| Code | Trigger | Severity | Gate effect |
| :--- | :--- | :--- | :--- |
| `DUPLICATE_CORPUS_ID` | corpus ID 중복 | ERROR | Fail |
| `CORPUS_UNAVAILABLE` | optional corpus 없음 | WARNING | Continue with exclusion |
| `REQUIRED_CORPUS_UNAVAILABLE` | required corpus 없음 | ERROR | Fail |
| `CORPUS_INTEGRITY_MISMATCH` | digest/commit 불일치 | ERROR | Fail |
| `CORPUS_SCOPE_INVALID` | source root/endpoint scope 불일치 | ERROR 또는 WARNING | required fail, optional exclude |
| `IDENTITY_COLLISION` | exact/scenario identity 충돌 | ERROR | Fail |
| `BASELINE_ENTRY_UNLABELED` | disposition 누락 | ERROR | Fail |
| `CORRECTED_EXPECTATION_REQUIRED` | REPLACE expectation 누락 | ERROR | Fail |
| `UNSUPPORTED_DIAGNOSTIC_REQUIRED` | UNSUPPORTED expectation 누락 | ERROR | Fail |
| `SNAPSHOT_NON_DETERMINISTIC` | 반복 실행 semantic diff | ERROR | Fail |
| `HOLDOUT_MAPPING_INCOMPLETE` | 원본/variant scenario 대응 누락 | ERROR | Fail |

## 10. G01 Gate Rules

### BR-U01-070 — All mandatory evidence

다음 evidence가 모두 존재해야 한다.

- corpus manifest review
- disposition completeness report
- deterministic repeated snapshot diff
- identity collision report
- graph/semantic coverage snapshot
- rename/mutation holdout isolation report

### BR-U01-071 — Gate approval boundary

G01 성공은 U02 착수 허가일 뿐 YAML externalization의 Go 판정이 아니다.

### BR-U01-072 — Failure ownership

U01 gate 실패는 snapshot formatting으로 숨기지 않는다. corpus, identity, labeling 또는 current Java observation 중 실제 소유 원인을 수정한 뒤 전체 evidence를 재생성한다.
