# UOW-02 Functional Design Plan

## Unit Context
- Unit: `UOW-02`
- Title: Endpoint and Type Analysis
- Related story: `S-02`
- Goal: repository source inventory를 입력받아 Spring endpoint, request binding, response binding, request/response type context를 추출한다.

## Execution Checklist
- [ ] `UOW-02`의 endpoint analysis workflow를 정의한다.
- [ ] endpoint/type analysis domain model을 정의한다.
- [ ] request/response binding 규칙을 정의한다.
- [ ] type resolution과 source trace 규칙을 정의한다.
- [ ] partial failure와 error handling rules를 정의한다.
- [ ] `business-logic-model.md`를 생성한다.
- [ ] `business-rules.md`를 생성한다.
- [ ] `domain-entities.md`를 생성한다.

## Functional Design Focus
- controller detection
- request mapping resolution
- request target location classification
- response binding classification
- request/response type extraction
- shared type and source trace resolution
- partial endpoint failure handling

## Planning Questions

## Question 1
Spring controller 탐지 범위는 어디까지 둘까요?

A) `@RestController`만 우선 지원

B) `@RestController`, `@Controller`, `@RequestMapping` 조합까지 지원

C) B안 + meta-annotation 또는 커스텀 composed mapping 가능성도 candidate로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + meta-annotation 또는 커스텀 composed mapping 가능성도 candidate로 보존

기본 지원은 `@RestController`, `@Controller`, `@RequestMapping` 조합으로 둔다. 다만 PoC라도 실제 Spring 프로젝트에서는 composed annotation이나 custom meta-annotation이 있을 수 있으므로, 완전 해석하지 못하더라도 candidate로 보존한다.

확정 가능한 controller는 resolved controller로 처리하고, meta-annotation 또는 custom composed mapping은 unresolved 또는 candidate 상태로 남겨 source trace와 confidence를 함께 보존한다.

## Question 2
request target location 분류 규칙은 어느 수준으로 고정할까요?

A) 명시 annotation 중심만 지원

- `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@RequestBody`

B) A안 + annotation 생략 시 Spring 관례 일부 반영

- 단순 타입 파라미터는 QUERY 후보 등

C) B안 + ambiguity는 unresolved binding으로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + ambiguity는 unresolved binding으로 보존

명시 annotation인 `@PathVariable`, `@RequestParam`, `@RequestHeader`, `@RequestBody`는 우선 확정 규칙으로 처리한다.

annotation이 생략된 경우에는 Spring 관례를 일부 반영하되, 단정하지 않는다. 예를 들어 단순 타입 파라미터는 QUERY 후보로 볼 수 있지만, 명확하지 않으면 `unresolved binding`으로 보존한다.

즉, 잘못 확정하는 것보다 애매한 상태를 evidence와 함께 남기는 쪽을 우선한다.

## Question 3
response binding은 어떻게 모델링할까요?

A) return type만 있으면 `RESPONSE`로 간단 연결

B) `ResponseEntity<T>`, generic wrapper, void/no-content를 구분

C) B안 + 성공/오류 응답 후보를 나눠 metadata로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 성공/오류 응답 후보를 나눠 metadata로 보존

return type만 단순히 `RESPONSE`로 연결하지 않고, `ResponseEntity<T>`, generic wrapper, `void`, no-content 가능성을 구분한다.

또한 성공 응답과 오류 응답 후보를 분리할 수 있도록 metadata를 남긴다. UOW-02에서는 오류 응답을 완전히 확정하지 않더라도, 이후 service/domain hint와 exception 분석에서 연결할 수 있도록 response binding context를 열어둔다.

## Question 4
type resolution은 어느 수준까지 추적할까요?

A) 직접 선언된 DTO/class 타입만 우선 추적

B) generic wrapper, collection, nested field까지 추적

C) B안 + 해석 불가 타입은 unresolved descriptor로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 해석 불가 타입은 unresolved descriptor로 보존

직접 선언된 DTO/class 타입뿐 아니라 generic wrapper, collection, nested field까지 추적한다.

예를 들어 `ResponseEntity<ApiResponse<List<OrderResponse>>>` 같은 구조는 가능한 범위까지 type descriptor로 풀어낸다.

다만 모든 타입을 완전히 해석하려고 하지 않는다. import 누락, 외부 라이브러리 타입, 복잡한 generic, unresolved symbol은 `unresolved descriptor`로 보존하고 source trace와 confidence를 함께 기록한다.

## Question 5
source trace는 어느 수준까지 강제할까요?

A) endpoint method 중심 trace만 보존

B) controller class + method + parameter/return type까지 보존

C) B안 + line number와 resolution confidence까지 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + line number와 resolution confidence까지 보존

controller class, endpoint method, parameter, return type trace를 모두 보존한다.

가능하다면 line number까지 기록하고, type/binding/path resolution 결과에는 confidence를 함께 둔다. 이 정보는 이후 validation candidate, LLM chunk, ApiCondition evidence로 이어지는 핵심 근거가 된다.

## Question 6
partial failure 모델은 어떻게 둘까요?

A) 한 endpoint 실패 시 전체 `UOW-02` 실패

B) endpoint 단위 partial failure 허용

C) B안 + endpoint, type resolution, binding resolution failure를 세분화해 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + endpoint, type resolution, binding resolution failure를 세분화해 보존

한 endpoint 분석 실패가 전체 UOW-02 실패로 이어지면 안 된다. endpoint 단위 partial failure를 허용한다.

또한 실패 유형은 endpoint detection failure, request mapping resolution failure, request binding resolution failure, response binding resolution failure, type resolution failure처럼 세분화한다.

성공한 endpoint 결과는 계속 산출하고, 실패한 endpoint는 원인, source trace, recoverability, confidence와 함께 별도 failure record로 남긴다.

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-02` functional design 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-02/functional-design/` 아래 산출물을 생성한다.

## Answer Analysis
- controller 탐지는 표준 Spring controller + mapping 조합을 기본으로 하고, meta-annotation/composed mapping은 candidate 또는 unresolved 상태로 보존하는 방향으로 확정되었다.
- request binding은 명시 annotation을 우선 확정하고, 관례 기반 추론은 가능하되 ambiguity는 unresolved binding으로 남긴다.
- response binding은 성공/오류 후보 metadata를 분리 보존하는 richer model로 설계해야 한다.
- type resolution은 nested/generic/collection까지 추적하고, 미해결 타입은 unresolved descriptor와 confidence를 유지한다.
- source trace는 class, method, parameter, return type, line number, confidence까지 포함하는 강한 trace 모델이 필요하다.
- partial failure는 endpoint 전체, binding, response, type resolution failure를 세분화해서 보존하는 모델로 확정되었다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
