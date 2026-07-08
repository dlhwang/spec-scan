# UOW-04 Functional Design Plan

## Unit Context
- Unit: `UOW-04`
- Title: Candidate Chunk and Normalization
- Related stories:
  - `S-06`
  - `S-07`
- Goal: validation extraction 결과를 guarded candidate chunk로 만들고, 이를 LLM normalization에 전달한 뒤 schema/semantic validation을 거쳐 accepted or rejected normalization 결과로 변환한다.

## Execution Checklist
- [ ] `UOW-04`의 chunk generation workflow를 정의한다.
- [ ] chunk boundary와 grouping 규칙을 정의한다.
- [ ] invalid candidate filtering 규칙을 정의한다.
- [ ] prompt building과 LLM invocation 규칙을 정의한다.
- [ ] response schema/semantic validation 규칙을 정의한다.
- [ ] retry, rejection, acceptance decision 규칙을 정의한다.
- [ ] `business-logic-model.md`를 생성한다.
- [ ] `business-rules.md`를 생성한다.
- [ ] `domain-entities.md`를 생성한다.

## Functional Design Focus
- endpoint or validator-scope chunk grouping
- `candidateId` generation and uniqueness
- invalid candidate exclusion before LLM
- prompt input schema
- JSON-only response contract
- schema validation, enum validation, candidateId validation
- evidence 없는 rule 차단
- accepted/rejected/retry result routing

## Planning Questions

## Question 1
candidate chunk grouping 기준은 어디까지 세분화할까요?

A) endpoint 단위만 사용

B) endpoint 단위 + validator method 단위

C) B안 + 필요 시 source kind별 sub-grouping metadata까지 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 필요 시 source kind별 sub-grouping metadata까지 보존

기본 grouping은 endpoint 단위와 validator method 단위를 함께 사용한다.

endpoint context가 중요한 후보는 endpoint 단위로 묶고, `ConstraintValidator#isValid`, Spring `Validator#validate`처럼 특정 validator method 내부 evidence가 중요한 후보는 validator method 단위로 묶는다.

추가로 source kind별 metadata를 보존한다. 예를 들어 `BEAN_VALIDATION_UNSUPPORTED`, `CONSTRAINT_VALIDATOR`, `SPRING_VALIDATOR`, `SERVICE_DOMAIN_HINT` 같은 source kind를 chunk metadata에 포함해 LLM prompt와 후속 검증에서 사용할 수 있게 한다.

## Question 2
invalid candidate filtering은 어느 수준까지 pre-LLM에서 강제할까요?

A) evidence 없는 경우만 제외

B) evidence, source trace, candidateId 기본 무결성까지 검사

C) B안 + endpoint linkage, enum/domain validity, duplicate candidateId까지 검사

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + endpoint linkage, enum/domain validity, duplicate candidateId까지 검사

LLM 호출 전에 evidence, source trace, candidateId 기본 무결성을 반드시 검사한다.

추가로 endpoint linkage, targetLocation/operator/sourceKind 같은 enum/domain validity, duplicate candidateId 여부를 검사한다. evidence가 없거나 candidateId가 중복되거나 source trace가 없는 candidate는 LLM에 보내지 않는다.

LLM은 후보를 새로 발명하는 단계가 아니라 근거 있는 candidate를 정규화하는 단계이므로, pre-LLM filtering을 강하게 적용한다.

## Question 3
LLM prompt/input schema는 어느 수준까지 구조화할까요?

A) 자유 형식 prompt + JSON only 출력 지시

B) 고정된 JSON input envelope + JSON only output schema

C) B안 + operator enum, confidence enum, forbidden behaviors까지 prompt contract에 명시

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + operator enum, confidence enum, forbidden behaviors까지 prompt contract에 명시

LLM 입력은 자유 형식 prompt가 아니라 고정된 JSON input envelope로 구성한다.

입력에는 endpoint context, candidate list, allowed operator enum, allowed confidence enum, targetLocation enum, evidence, source trace, forbidden behaviors를 포함한다.

금지 사항도 prompt contract에 명시한다. 예를 들어 evidence 없는 rule 생성 금지, 입력에 없는 candidateId 생성 금지, enum에 없는 operator 사용 금지, 전체 소스 추론 금지, JSON 외 텍스트 출력 금지 등을 포함한다.

## Question 4
LLM 응답 검증은 어디까지 강하게 할까요?

A) JSON parse 가능 여부와 필수 필드만 검사

B) A안 + schema validation, enum validation, candidateId validation

C) B안 + evidence presence, unsupported rule creation 금지, semantic consistency까지 검사

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + evidence presence, unsupported rule creation 금지, semantic consistency까지 검사

LLM 응답은 JSON parse 여부와 필수 필드만 검사해서는 부족하다.

schema validation, enum validation, candidateId validation을 수행하고, 추가로 evidence presence, unsupported rule creation 금지, semantic consistency를 검사한다.

예를 들어 응답의 candidateId는 입력 candidate에 존재해야 하고, targetPath는 candidate의 evidence 또는 static facts와 연결 가능해야 한다. 입력 evidence 없이 새 rule을 만든 경우 rejected 처리한다.

## Question 5
retry/rejected 정책은 어떻게 둘까요?

A) 실패 응답은 모두 rejected

B) parse/schema 오류만 retry, 나머지는 rejected

C) B안 + semantic inconsistency와 low-quality response도 제한적 retry 후 rejected

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + semantic inconsistency와 low-quality response도 제한적 retry 후 rejected

JSON parse 오류와 schema 오류는 제한적 retry 대상이다.

candidateId 불일치, enum 오류, semantic inconsistency, low-quality response도 한정된 횟수까지 retry할 수 있다. 다만 evidence 없는 rule 생성, 입력 candidate와 무관한 rule 생성, 반복 실패는 rejected로 분기한다.

retry는 무제한으로 두지 않고, promptVersion, inputHash, attempt count를 기록한다.

## Question 6
normalization 결과 모델은 어떻게 분리할까요?

A) accepted result만 최종 저장

B) accepted와 rejected를 분리 저장

C) B안 + retry history, raw response, validator finding까지 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + retry history, raw response, validator finding까지 보존

accepted result와 rejected result를 모두 분리 저장한다.

추가로 retry history, raw request/response, promptVersion, inputHash, validator finding을 보존한다. accepted 결과만 저장하면 왜 실패했는지, 왜 거절됐는지, 어떤 prompt에서 문제가 있었는지 추적할 수 없다.

PoC 단계에서도 LLM normalization은 비결정성이 있으므로 raw response와 validator finding을 보존해야 디버깅과 재현성 검토가 가능하다.

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-04` functional design 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-04/functional-design/` 아래 산출물을 생성한다.

## Answer Analysis
- chunk grouping은 endpoint 단위와 validator method 단위를 모두 사용하고, source kind별 sub-grouping metadata를 보존하는 방향으로 확정되었다.
- pre-LLM filtering은 evidence, source trace, candidateId 무결성뿐 아니라 endpoint linkage, enum/domain validity, duplicate candidateId까지 강하게 검사해야 한다.
- prompt/input은 고정 JSON envelope와 허용 enum, forbidden behavior까지 포함한 강한 contract로 설계해야 한다.
- response validation은 schema/enum/candidateId 검증을 넘어 evidence presence, unsupported rule creation 금지, semantic consistency까지 포함해야 한다.
- retry는 parse/schema 오류뿐 아니라 semantic inconsistency와 low-quality response도 제한적으로 허용하되, 반복 실패나 rule fabrication은 rejected로 처리한다.
- normalization 결과는 accepted/rejected 분리 저장에 더해 retry history, raw request/response, promptVersion, inputHash, validator finding까지 보존해야 한다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
