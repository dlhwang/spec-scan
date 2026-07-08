# UOW-03 Functional Design Plan

## Unit Context
- Unit: `UOW-03`
- Title: Validation Candidate Extraction
- Related stories:
  - `S-03`
  - `S-04`
  - `S-05`
- Goal: endpoint/type analysis 결과를 바탕으로 direct annotation condition과 validator/service-domain 기반 validation candidate를 추출한다.

## Execution Checklist
- [ ] `UOW-03`의 validation extraction workflow를 정의한다.
- [ ] annotation direct mapping 규칙을 정의한다.
- [ ] custom/unsupported annotation fallback 규칙을 정의한다.
- [ ] validator-layer candidate extraction 규칙을 정의한다.
- [ ] service/domain hint extraction 규칙을 정의한다.
- [ ] evidence, trace, confidence propagation 규칙을 정의한다.
- [ ] `business-logic-model.md`를 생성한다.
- [ ] `business-rules.md`를 생성한다.
- [ ] `domain-entities.md`를 생성한다.

## Functional Design Focus
- standard Bean Validation direct mapping
- unsupported/custom annotation fallback
- `ConstraintValidator`, Spring `Validator`, `@InitBinder` extraction
- `errors.rejectValue`, `reject` evidence capture
- `if`, `BusinessException`, `ErrorCode` hint extraction
- evidence and source trace preservation
- pre-LLM candidate readiness

## Planning Questions

## Question 1
표준 Bean Validation annotation의 direct mapping 범위는 어디까지 둘까요?

A) 핵심 annotation만 우선 지원

- `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Min`, `@Max`

B) A안 + 자주 쓰는 파생 annotation 추가

- `@Positive`, `@PositiveOrZero`, `@Negative`, `@Email` 등

C) B안 + 미지원 annotation도 raw mapping candidate metadata로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 미지원 annotation도 raw mapping candidate metadata로 보존

표준 Bean Validation은 가능한 한 direct mapping으로 처리한다. 기본 범위는 `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Min`, `@Max`이고, 추가로 `@Positive`, `@PositiveOrZero`, `@Negative`, `@NegativeOrZero`, `@Email`, `@NotEmpty`까지 우선 지원한다.

직접 매핑 가능한 annotation은 `ApiCondition`으로 생성하고, 미지원 annotation은 버리지 않고 raw metadata를 가진 `ValidationCandidate`로 보존한다. 이렇게 해야 이후 custom validator 분석 또는 LLM normalization 단계에서 활용할 수 있다.

## Question 2
unsupported/custom annotation은 어떻게 처리할까요?

A) 단순 미지원으로 버린다

B) `ValidationCandidate`로 보존하되 annotation metadata만 남긴다

C) B안 + annotation와 연결된 validator/attribute/source trace를 함께 보존한다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + annotation와 연결된 validator/attribute/source trace를 함께 보존한다

unsupported/custom annotation은 단순 미지원으로 버리지 않는다. annotation name, attribute 값, 적용 위치, target field, source trace를 `ValidationCandidate`로 보존한다.

만약 해당 annotation이 `@Constraint(validatedBy = ...)`와 연결되어 있으면 validator class 정보까지 함께 보존한다. validator 연결을 완전히 해석하지 못하더라도 annotation evidence와 unresolved reason을 남긴다.

## Question 3
validator-layer extraction은 어느 수준까지 강하게 연결할까요?

A) `ConstraintValidator#isValid`만 우선 지원

B) A안 + Spring `Validator`, `@InitBinder`, `rejectValue`, `reject`까지 연결

C) B안 + endpoint와 validator 간 연결 confidence와 binding trace까지 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + endpoint와 validator 간 연결 confidence와 binding trace까지 보존

`ConstraintValidator#isValid`, Spring `Validator#validate`, `@InitBinder`, `errors.rejectValue`, `errors.reject`를 모두 후보 추출 대상으로 둔다.

단, validator가 endpoint에 실제로 연결되는 정도는 다를 수 있으므로 binding trace와 confidence를 함께 보존한다. 예를 들어 `@InitBinder`로 명시 연결된 경우는 높은 confidence로 보고, 단순히 `supports()` 대상 DTO만 일치하는 경우는 중간 confidence로 본다. 연결 근거가 약한 경우에도 버리지 않고 candidate로 남긴다.

## Question 4
service/domain hint extraction은 어느 수준까지 확장할까요?

A) `throw new BusinessException` 중심만 우선 추출

B) A안 + `if` 조건문, `ErrorCode`, method chain context까지 추출

C) B안 + 요청 필드와 직접 연결되지 않아도 hypothesis candidate로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + 요청 필드와 직접 연결되지 않아도 hypothesis candidate로 보존

서비스/도메인 레이어에서는 `throw new BusinessException(...)`, `ErrorCode`, `if` 조건문, repository 조회, method chain context를 validation hint 후보로 추출한다.

요청 필드와 직접 연결되지 않는 경우에도 source trace, condition snippet, error code, 관련 메서드 체인 정보를 가진 hypothesis candidate로 보존한다. 다만 이 후보는 확정 condition이 아니므로 confidence를 낮게 두고, 이후 LLM normalization 또는 수동 검토 대상으로 넘긴다.

## Question 5
evidence와 confidence는 어떻게 관리할까요?

A) 최종 candidate에 evidence 문자열만 보존

B) evidence + source trace + source kind만 보존

C) B안 + extraction confidence, reasoning note, unresolved reason까지 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + extraction confidence, reasoning note, unresolved reason까지 보존

각 candidate와 direct condition은 evidence, source trace, source kind를 반드시 가진다.

추가로 extraction confidence, reasoning note, unresolved reason을 보존한다. direct annotation mapping처럼 명시적 근거가 강한 경우는 HIGH confidence로 두고, service/domain hint처럼 추론이 필요한 경우는 MEDIUM 또는 LOW confidence로 둔다.

unresolved reason은 이후 LLM normalization, 검토, 디버깅에서 중요하므로 별도 필드로 관리한다.

## Question 6
partial failure/ambiguous extraction은 어떻게 다룰까요?

A) annotation/validator/service hint 중 하나라도 실패하면 해당 endpoint 실패

B) extractor별 partial failure를 허용

C) B안 + candidate, direct condition, failure record를 분리하고 ambiguous case는 candidate 또는 failure로 보존

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + candidate, direct condition, failure record를 분리하고 ambiguous case는 candidate 또는 failure로 보존

extractor별 partial failure를 허용한다. annotation extraction 실패가 validator extraction 전체 실패로 이어지면 안 되고, service hint extraction 실패가 endpoint 전체 실패로 이어져서도 안 된다.

결과는 `direct condition`, `validation candidate`, `failure record`로 분리한다. 확정 가능한 것은 direct condition으로 저장하고, 해석 가능성이 있지만 확정하기 어려운 것은 candidate로 저장한다. 분석 자체가 불가능한 경우에는 failure record로 남긴다.

ambiguous case는 버리지 않고 candidate 또는 failure로 보존하며, source trace와 unresolved reason을 함께 기록한다.

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 `UOW-03` functional design 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변 완료 후 `aidlc-docs/construction/uow-03/functional-design/` 아래 산출물을 생성한다.

## Answer Analysis
- direct mapping 대상은 핵심 Bean Validation + 자주 쓰는 파생 annotation까지 확장하고, 미지원 annotation도 raw candidate metadata로 보존하는 방향으로 확정되었다.
- unsupported/custom annotation은 annotation attribute, target field, source trace, validator 연결 정보까지 가진 `ValidationCandidate`로 보존해야 한다.
- validator-layer extraction은 `ConstraintValidator`, Spring `Validator`, `@InitBinder`, `rejectValue`, `reject`를 포함하고 endpoint 연결 confidence와 binding trace를 유지해야 한다.
- service/domain hint는 field direct link가 없어도 hypothesis candidate로 유지하고 낮은 confidence로 후속 normalization/검토에 넘긴다.
- evidence, source trace, source kind 외에 extraction confidence, reasoning note, unresolved reason을 함께 관리해야 한다.
- 결과는 direct condition, validation candidate, failure record로 분리하고 ambiguous case를 버리지 않는 방향으로 확정되었다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
