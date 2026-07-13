# Deep Interview Questions

API Contract 설계 개선 및 검증 어설션 모델 정렬을 위한 설계 상의 모호성을 해결하기 위한 질문입니다.
각 질문에 대해 `[Answer]: ` 뒤에 선택하신 알파벳을 입력해 주시기 바랍니다. (예: `[Answer]: A`)

## Question 1
기존의 "도메인 규칙 승격 계획"이 담겨 있던 `validation-condition-improvement-plan.md` 파일의 처리 방식을 어떻게 지정해야 합니까?

A) 기존 파일명은 유지하고, 파일 내부 내용 전체를 "검증 어설션 모델(Precondition & Assertion 분리) 정렬 계획"으로 완전히 덮어써서 개정합니다. (Modify)

B) 기존 `validation-condition-improvement-plan.md` 파일은 삭제하고, 새 방향을 직관적으로 보여주는 신규 계획서 파일(예: `validation-assertion-separation-plan.md`)로 교체합니다. (Replace)

C) Other (please describe after [Answer]: tag below)

[Answer]: A

## Question 2
`AUTH`(예: currentUser), `RESOURCE`(예: order.state), 내부 리포지토리 존재 여부 및 Optimistic Lock 등 최종 실행 계약에서 제외할 비즈니스 규칙들의 정적 분석 수집 수준을 어떻게 정해야 합니까?

A) 정적 분석 후보 추출(candidate extraction) 단계부터 관련 키워드나 힌트를 Java 코드 분석 대상에서 완전히 차단하여 수집하지 않습니다. (원천 배제)

B) 정적 분석 및 후보 수집 단계에서는 일단 흔적(evidence)으로 수집해 두어 추후 분석 가치를 보존하고, 최종 JSON 계약 조립(assembly) 시점에만 필터링하여 결과 모델(`requestPreconditions`, `responseAssertions`)에서 배제합니다. (최종 조립 시 배제)

C) Other (please describe after [Answer]: tag below)

[Answer]: B

## Question 3
계약 모델 이원화에 따른 `RequestPrecondition` 및 `ResponseAssertion` 모델의 Java 소스 코드 상의 구조적 분리 수준을 어떻게 설계해야 합니까?

A) `RequestPrecondition`과 `ResponseAssertion` 클래스를 아예 별개의 상속 계층이 없는 완전 독립된 타입으로 정의하여 구조적 의존성을 차단합니다.

B) 기존 `ApiCondition` 클래스의 필드 구조를 공통 부모 또는 단일 클래스로 최대한 재활용하되, `conditionType: PRECONDITION | ASSERTION`과 같은 필드를 추가하여 런타임/추출 시에만 개념적으로 분리하여 조립합니다.

C) Other (please describe after [Answer]: tag below)

[Answer]: B
