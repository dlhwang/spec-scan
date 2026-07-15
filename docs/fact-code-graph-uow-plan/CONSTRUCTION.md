# Fact Code Graph 개선 진행 현황

## 운영 규칙

- UoW 번호 순서대로 진행한다.
- 구현과 해당 범위 테스트가 모두 통과해야 완료 처리한다.
- 허용 Operator는 `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `CONTAINS`, `NOT_CONTAINS`, `EMPTY`, `NOT_EMPTY`, `NULL`, `NOT_NULL`뿐이다.
- 모든 출력 조건은 endpoint에서 도달 가능한 Fact Code Graph evidence를 가져야 한다.
- 표현할 수 없는 조건은 유사 Operator로 변환하지 않는다.
- `excludedBusinessRules`는 graph-backed 고정 규칙만 허용하며, 근거가 부족하면 비워 둔다.

## 진행 체크리스트

- [x] UoW 00 — 현재 파이프라인 특성화
  - [x] 두 그래프의 producer/consumer를 확인한다.
  - [x] RealEstate 6개 API의 현재 결과를 baseline으로 고정한다.
  - [x] 반복 실행 결정성을 검증한다.
- [x] UoW 01 — Operator allowlist
- [x] UoW 02 — Evidence gate
- [x] UoW 03 — FactCodeGraph 단일 기준
- [x] UoW 04 — 생성자 Fact ingest
- [x] UoW 05 — Lambda 및 method reference
- [x] UoW 06 — Boolean condition tree
- [x] UoW 07 — 입력 data flow
- [x] UoW 08 — Guard outcome과 HTTP 실패
- [x] UoW 09 — POST PreCondition
- [x] UoW 10 — PUT/GET/DELETE PreCondition
- [x] UoW 11 — 응답 status와 body
- [x] UoW 12 — 응답 DTO data flow
- [x] UoW 13 — ResponseAssert invariant 전파
- [x] UoW 14 — PasswordEncoder 제어 흐름
- [x] UoW 15 — Strict excludedBusinessRules
- [x] UoW 16 — RealEstate golden regression

## 검증 기록

| UoW | 상태 | 검증 | 비고 |
|---|---|---|---|
| 00 | 완료 | PASS | FactCodeGraphBuilder/OpenApiAssembly/Baseline focused tests |
| 01 | 완료 | PASS | output/migration tests 17개 |
| 02 | 완료 | PASS | candidate/rule tests, unreachable evidence rejection |
| 03 | 완료 | PASS | assembly/delivery/migration tests 15개 |
| 04 | 완료 | PASS | object creation/constructor/super guard tests |
| 05 | 완료 | PASS | lambda/method reference source-target tests |
| 06 | 완료 | PASS | unary/nested boolean/switch structure tests |
| 07 | 완료 | PASS | method/constructor argument ordinal data-flow tests |
| 08 | 완료 | PASS | throw/handler/HTTP status Fact path tests |
| 09 | 완료 | PASS | POST body/property/collection/collection-element provenance conditions |
| 10 | 완료 | PASS | PUT domain reuse; GET/DELETE existence kept without operator weakening |
| 11 | 완료 | PASS | graph-backed status; no-content EMPTY body; explicit constructor status |
| 12 | 완료 | PASS | builder/constructor/static mapper/collection VALUE_FLOWS_TO provenance |
| 13 | 완료 | PASS | guarded response field propagation and normal-return intersection |
| 14 | 완료 | PASS | qualified PasswordEncoder positive/negated/trailing failure patterns |
| 15 | 완료 | PASS | fixed rule allowlist plus predicate/outcome evidence gate |
| 16 | 완료 | PASS | RealEstate 6 APIs, canonical operators, evidence, determinism, full regression |

## 전체 검증 결과

- 실행: `REALESTATE_WORKSPACE=D:\workspace\real-estate\RealEstate .\gradlew.bat test`
- 결과: `BUILD SUCCESSFUL`, 143 tests
- 실제 RealEstate golden에서 6개 operation과 반복 실행 결정성을 확인했다.
- POST는 body `NOT_NULL`, `propertyType NOT_NULL`, `contractDetails NOT_EMPTY`를 graph provenance로 출력한다.
- DELETE는 `$status EQ 204`, `$body EMPTY`를 실제 `ResponseEntity.noContent()` Fact로 출력한다.
- repository 존재성은 `NOT_EMPTY`로 약화하지 않고, strict excluded gate를 통과하지 못한 해석 규칙은 출력하지 않는다.

## 구현 중 발견해 보완한 ingest 단절

- fully-qualified parameter가 있는 method signature의 declaring owner 파싱
- 동일 method-call의 unresolved/resolved metadata 병합 결정성
- RealEstate 깊은 호출 경로를 위한 traversal depth/edge 예산
- Lombok `@Value`/`@AllArgsConstructor` generated constructor의 parameter-field provenance
- instance method receiver와 target instance field의 provenance
- symbol resolution 실패 시 유일한 source static method에 한정한 fallback
- indexed collection `get(index)`의 `[*]` 경로 정규화
