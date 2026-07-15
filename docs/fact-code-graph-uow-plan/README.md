# Fact Code Graph 기반 조건 분석 개선 계획

## 목표

API의 `requestPreconditions`, `responseAssertions`, `excludedBusinessRules`를 Fact Code Graph에서 증명된 사실만으로 생성한다. 출력 Operator는 아래 12개로 제한한다.

`EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `CONTAINS`, `NOT_CONTAINS`, `EMPTY`, `NOT_EMPTY`, `NULL`, `NOT_NULL`

## 공통 원칙

- 소스 snippet을 exporter나 normalizer가 다시 의미 해석하지 않는다.
- 모든 출력에는 endpoint부터 근거 Fact까지의 도달 경로가 있어야 한다.
- 표현할 수 없는 조건을 유사 Operator로 약화하지 않는다.
- `excludedBusinessRules`는 그래프 근거와 고정 rule template이 있을 때만 생성한다.
- 근거가 부족하면 빈 배열이 정상 결과다.

## 실행 순서

| 단계 | UoW | 결과 |
|---|---|---|
| 기반 | 00–03 | 기준선, Operator 제한, evidence gate, 단일 그래프 기준 |
| Ingest | 04–07 | 생성자, lambda, 조건식, data flow Fact |
| PreCondition | 08–10 | 실패 경로 및 RealEstate 입력 조건 |
| ResponseAssert | 11–13 | 상태·본문·응답 필드 invariant |
| 안정화 | 14–16 | PasswordEncoder, strict excluded, golden regression |

## 의존성

```text
00 → 01 → 02 → 03
               ├→ 04 → 05 → 06 → 07 → 08 → 09 → 10
               └→ 11 → 12 → 13
08 + 07 ─────────────────────────────→ 14
02 + 03 + 08 ────────────────────────→ 15
09 + 10 + 11 + 13 + 14 + 15 ───────→ 16
```

## 문서 목록

- [UoW 00 — 현재 파이프라인 특성화](uow-00-characterization.md)
- [UoW 01 — Operator allowlist](uow-01-operator-allowlist.md)
- [UoW 02 — Evidence gate](uow-02-evidence-gate.md)
- [UoW 03 — FactCodeGraph 단일 기준](uow-03-authoritative-graph.md)
- [UoW 04 — 생성자 Fact ingest](uow-04-constructor-ingest.md)
- [UoW 05 — Lambda 및 method reference](uow-05-lambda-method-reference.md)
- [UoW 06 — Boolean condition tree](uow-06-boolean-tree.md)
- [UoW 07 — 입력 data flow](uow-07-input-data-flow.md)
- [UoW 08 — Guard outcome과 HTTP 실패](uow-08-guard-outcome.md)
- [UoW 09 — POST PreCondition](uow-09-post-preconditions.md)
- [UoW 10 — PUT/GET/DELETE PreCondition](uow-10-other-preconditions.md)
- [UoW 11 — 응답 status와 body](uow-11-response-metadata.md)
- [UoW 12 — 응답 DTO data flow](uow-12-response-data-flow.md)
- [UoW 13 — ResponseAssert invariant 전파](uow-13-response-invariants.md)
- [UoW 14 — PasswordEncoder 제어 흐름](uow-14-password-encoder.md)
- [UoW 15 — Strict excludedBusinessRules](uow-15-strict-excluded-rules.md)
- [UoW 16 — RealEstate golden regression](uow-16-realestate-regression.md)

## 공통 병합 게이트

- 허용 목록 밖의 Operator가 없다.
- graph evidence가 없는 출력을 추가하지 않는다.
- endpoint 도달성을 검증한다.
- unsupported 사례는 diagnostic으로 남기고 출력하지 않는다.
- RealEstate 전용 클래스명이나 메서드명을 production 코드에 하드코딩하지 않는다.
- 동일 입력에서 node ID와 출력 순서가 결정적이다.

