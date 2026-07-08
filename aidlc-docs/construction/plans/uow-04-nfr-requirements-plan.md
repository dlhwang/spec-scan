# UOW-04 NFR Requirements Plan

## Unit Context
- Unit: `UOW-04`
- Title: Candidate Chunk and Normalization
- Goal: candidate chunk generation과 LLM normalization에 필요한 핵심 비기능 요구사항과 tech stack 결정을 확정한다.

## Execution Checklist
- [ ] `UOW-04` functional design을 기준으로 핵심 NFR 축을 식별한다.
- [ ] LLM 안정성, retry, schema validation, 비용/캐싱, 실패 격리 요구를 정리한다.
- [ ] `nfr-requirements.md`를 생성한다.
- [ ] `tech-stack-decisions.md`를 생성한다.

## NFR Focus
- LLM response stability
- schema and semantic validation reliability
- bounded retry and failure isolation
- chunk integrity and reproducibility
- cost control and caching policy
- observability for accepted/rejected/retry outcomes

## Planning Questions

## Question 1
`UOW-04`의 성능/처리량 목표는 어느 수준으로 둘까요?

A) PoC 수준이라 명시적 수치 없이 합리적 완료 시간만 요구

B) 샘플 repository 기준 chunk 수십 개를 수 초~수십 초 내 안정 처리 목표

C) B안 + pre-LLM filtering, prompt build, response validation 단계별 대략적 시간 예산도 둔다

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 2
LLM 응답 안정성과 retry 요구는 어느 수준까지 강제할까요?

A) parse/schema 오류만 bounded retry

B) A안 + semantic inconsistency와 low-quality response도 bounded retry

C) B안 + promptVersion/inputHash 기준 재시도 추적과 failure class별 retry policy 분리

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 3
비용/캐싱 요구는 어디까지 둘까요?

A) PoC라 비용 통제는 크게 신경 쓰지 않는다

B) 동일 inputHash 재실행 시 normalization 재사용 가능성을 둔다

C) B안 + chunk deduplication, promptVersion/inputHash 기반 캐시 키, cached vs fresh result 구분까지 요구한다

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 4
실패 격리와 가용성 요구는 어떻게 둘까요?

A) 한 chunk 실패 시 전체 normalization run 실패

B) chunk 단위 실패 격리

C) B안 + accepted/rejected/retryable/invalid candidate를 분리하고 일부 실패가 전체 파이프라인을 막지 않게 한다

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 5
보안/데이터 최소화 측면에서 raw request/response와 chunk artifact는 어디까지 보존할까요?

A) 디버깅을 위해 최대한 많이 남긴다

B) evidence와 trace 중심으로 제한 저장

C) B안 + raw request/response는 필요 최소 범위만 보존하고 전체 소스/불필요한 장문 컨텍스트는 금지한다

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 6
관측성과 품질 검증 요구는 어디까지 둘까요?

A) accepted/rejected 개수 정도만 남긴다

B) A안 + retry count, invalid candidate count, schema failure count 정도를 남긴다

C) B안 + semantic failure count, cache hit 여부, promptVersion별 결과, chunk validation result까지 남긴다

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-04` NFR requirements 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-04/nfr-requirements/` 아래 산출물을 생성한다.
