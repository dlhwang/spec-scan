# U01 Generalization Baseline — Functional Design 승인

> **상태**: 승인 완료 (2026-07-22)
> **산출물**: `../u01-generalization-baseline/functional-design/`

## 설계 요약

- Java `ObservedSnapshot`과 사람 승인 `BaselineManifest`를 분리한다.
- 동일 source는 `ObservationIdentity`, rename/mutation은 `ScenarioIdentity`로 비교한다.
- 모든 disposition에 rationale을 요구한다.
- `REPLACE`는 corrected expectation, `UNSUPPORTED`는 diagnostic expectation이 필수다.
- checked-in fixture와 digest-pinned local snapshot을 함께 사용한다.
- required corpus failure는 G01 실패, optional failure는 명시적 exclusion이다.
- baseline 자동 덮어쓰기를 금지하고 승인된 change proposal만 새 version을 만든다.
- graph coverage와 semantic coverage를 별도로 계산한다.
- runner는 explicit test/evaluation 전용이며 정상 scan에 연결하지 않는다.

## 선택지

### Option A: 변경 요청

`[Rationale]:`에 수정할 logic, entity, rule 또는 error flow를 작성한다.

### Option B: 승인 및 U01 NFR Requirements 계속 (권장)

Functional Design을 승인하고 U01 NFR Requirements 단계로 이동한다.

### Option C: 승인하되 여기서 중지

Functional Design을 승인 상태로 기록하지만 NFR Requirements는 시작하지 않는다.

[Answer]: B
[Rationale]: U01 Functional Design을 승인하고 NFR Requirements 단계로 진행한다.
