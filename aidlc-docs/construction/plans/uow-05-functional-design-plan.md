# UOW-05 Functional Design Plan

## Unit Context
- Unit: `UOW-05`
- Title: Contract Assembly and Output
- Related story:
  - `S-08`
- Goal: direct condition과 validated normalization result를 최종 `ApiCondition` 및 OpenAPI 산출물로 조립하고, review/debug 가능한 출력물로 저장한다.

## Execution Checklist
- [ ] `UOW-05`의 contract assembly workflow를 정의한다.
- [ ] `ApiCondition` 조립 규칙을 정의한다.
- [ ] OpenAPI assembly 규칙을 정의한다.
- [ ] evidence/confidence/provenance merge 규칙을 정의한다.
- [ ] partial success와 output persistence 규칙을 정의한다.
- [ ] review/debug artifact 출력 규칙을 정의한다.
- [ ] `business-logic-model.md`를 생성한다.
- [ ] `business-rules.md`를 생성한다.
- [ ] `domain-entities.md`를 생성한다.

## Functional Design Focus
- direct condition + normalization result merge
- `ApiCondition` field population
- OpenAPI path/schema generation
- evidence and llmReason preservation
- partial endpoint success/failure handling
- file-based output and result store abstraction
- demo/review artifact layout

## Planning Questions

## Question 1
`ApiCondition` 조립 시 direct condition과 normalized result가 충돌하면 어떻게 처리할까요?

A) direct condition 우선

B) confidence가 높은 쪽 우선

C) direct condition은 authoritative로 두고, normalized result는 보완/추가 규칙만 허용하며 충돌은 conflict record로 남긴다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) direct condition은 authoritative로 두고, normalized result는 보완/추가 규칙만 허용하며 충돌은 conflict record로 남긴다

표준 Bean Validation처럼 정적으로 확정 가능한 direct condition은 authoritative source로 본다. LLM normalized result는 direct condition을 덮어쓰지 않고, 보완 또는 추가 규칙으로만 반영한다.

예를 들어 direct condition이 `@Max(50)`에서 `LTE 50`을 만들었는데 normalized result가 `LTE 100`을 제안하면 direct condition을 유지하고, normalized result는 conflict record로 남긴다. conflict에는 targetPath, operator, expected, evidenceSource, normalizedBy, confidence, llmReason을 포함한다.

## Question 2
OpenAPI 변환 범위는 어디까지 둘까요?

A) endpoint/path/method와 request/response schema 중심

B) A안 + 기본 validation을 schema constraint로 가능한 범위까지 반영

C) B안 + OpenAPI에 다 못 담는 validation은 internal `ApiCondition`에만 남기고 양쪽 trace를 연결

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + OpenAPI에 다 못 담는 validation은 internal `ApiCondition`에만 남기고 양쪽 trace를 연결

OpenAPI 변환은 endpoint/path/method와 request/response schema를 기본으로 한다. 가능한 경우 Bean Validation 기반의 기본 constraint는 OpenAPI schema constraint로 반영한다.

예를 들어 `@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern` 등은 가능한 범위에서 required, minLength, maxLength, minimum, maximum, pattern 등으로 변환한다.

다만 Spring Validator, ConstraintValidator 내부 로직, service/domain hint, LLM normalized rule처럼 OpenAPI 표준 schema에 자연스럽게 담기 어려운 조건은 internal `ApiCondition`에 남긴다. OpenAPI와 ApiCondition 사이에는 endpointId, operationId, targetPath 기반 trace를 연결한다.

## Question 3
evidence, confidence, provenance는 어느 수준까지 최종 출력에 남길까요?

A) `ApiCondition`에 핵심 필드만 남긴다

B) evidence, confidence, source trace, llmReason, llmGenerated를 남긴다

C) B안 + normalizedBy, evidenceSource, reviewStatus, conflict 여부까지 남긴다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + normalizedBy, evidenceSource, reviewStatus, conflict 여부까지 남긴다

최종 `ApiCondition`에는 evidence, confidence, source trace, llmReason, llmGenerated를 남긴다.

추가로 `normalizedBy`, `evidenceSource`, `reviewStatus`, `conflict` 여부를 저장한다. 이 정보가 있어야 정적 분석으로 확정된 조건과 LLM이 정규화한 조건을 구분할 수 있고, 사람이 검토해야 하는 후보를 분리할 수 있다.

## Question 4
partial success/failure 출력은 어떻게 다룰까요?

A) 일부 endpoint 실패 시 전체 출력 실패

B) 성공 endpoint만 출력하고 실패는 로그로만 남긴다

C) 성공 결과와 실패 endpoint record를 함께 저장하고, manifest에서 분리해 보여준다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) 성공 결과와 실패 endpoint record를 함께 저장하고, manifest에서 분리해 보여준다

일부 endpoint 실패가 전체 output 실패로 이어지면 안 된다. 성공한 endpoint의 OpenAPI와 ApiCondition은 출력하고, 실패한 endpoint는 failure record로 따로 저장한다.

출력 manifest에는 successfulEndpoints, failedEndpoints, skippedCandidates, rejectedNormalizations, conflicts 같은 요약 정보를 포함한다. 이렇게 해야 데모와 디버깅에서 성공 범위와 실패 범위를 명확히 구분할 수 있다.

## Question 5
output persistence는 어느 수준까지 추상화할까요?

A) 파일 출력만 우선 구현

B) 파일 출력 구현 + `ResultStorePort` 계약 유지

C) B안 + artifact 종류별 manifest와 output metadata를 표준화

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + artifact 종류별 manifest와 output metadata를 표준화

PoC 구현은 파일 출력으로 시작한다. 다만 `ResultStorePort` 계약을 유지해 이후 DB 저장이나 API 서버 저장으로 확장할 수 있게 한다.

추가로 artifact 종류별 manifest와 output metadata를 표준화한다. 예를 들어 `openapi.json`, `api-conditions.json`, `normalization-rejections.json`, `conflicts.json`, `manifest.json` 같은 출력물을 명확히 구분하고, 각 artifact의 path, type, generatedAt, sourceRunId, endpointCount를 metadata로 남긴다.

## Question 6
review/debug artifact는 어디까지 남길까요?

A) 최종 OpenAPI와 ApiCondition만 저장

B) A안 + rejected normalization, invalid candidate, failure summary 저장

C) B안 + chunk JSON, retry history summary, conflict records, output manifest까지 저장

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + chunk JSON, retry history summary, conflict records, output manifest까지 저장

최종 OpenAPI와 ApiCondition만 저장하면 PoC 검토가 부족하다. rejected normalization, invalid candidate, failure summary를 저장하고, 추가로 chunk JSON, retry history summary, conflict records, output manifest까지 남긴다.

특히 LLM normalization이 포함된 PoC에서는 raw chunk와 retry/rejection 요약이 있어야 결과를 검토하고 재현할 수 있다. 단, 전체 소스코드를 저장하는 방식이 아니라 candidate evidence와 source trace 중심으로 제한한다.

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-05` functional design 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-05/functional-design/` 아래 산출물을 생성한다.

## Answer Analysis
- direct condition은 authoritative source로 유지하고, normalized result는 보완/추가만 허용하며 충돌은 별도 conflict record로 저장하는 방향으로 확정되었다.
- OpenAPI에는 표준 schema constraint로 표현 가능한 것만 반영하고, 나머지 규칙은 internal `ApiCondition`에 남기면서 trace를 연결해야 한다.
- 최종 `ApiCondition`에는 evidence, confidence, source trace, llmReason 외에 normalizedBy, evidenceSource, reviewStatus, conflict 여부까지 보존해야 한다.
- partial success는 성공 결과와 실패 endpoint record를 함께 저장하고 manifest에서 분리해 보여주는 방식으로 확정되었다.
- output persistence는 `ResultStorePort` 계약을 유지하면서 artifact별 manifest와 output metadata를 표준화하는 방향으로 정리되었다.
- review/debug artifact는 final outputs 외에도 rejected normalization, invalid candidate, failure summary, chunk JSON, retry summary, conflict records, manifest까지 포함해야 한다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
