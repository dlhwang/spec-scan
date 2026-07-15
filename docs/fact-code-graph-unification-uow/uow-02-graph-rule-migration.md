# UoW 02 — 서비스 및 Validator 규칙 GraphRule 이전

## 목적

`RuleBasedConditionNormalizer`의 실행 흐름 기반 판정을 개별 `GraphRule`로 이전함.

## 선행 조건

- UoW 01 완료

## 작업 범위

- repository 존재성 규칙을 기존 또는 신규 existence rule로 이전
- 버전 일치 규칙을 프로젝트 확장 rule로 이전
- 권한 검사를 authorization rule로 이전
- 상태 전이 및 상태 guard를 state precondition rule로 이전
- custom validator의 predicate와 failure outcome을 fact로 수집
- 신규 규칙을 rule pack 및 catalog에 등록
- `RuleEffect` enum과 세 가지 effect 정의
- `BusinessRuleCandidate`에 effect 필드 추가
- 기존 및 신규 모든 `GraphRule`이 effect를 명시하도록 변경
- effect에 따라 `EndpointRuleOutput`의 세 배열로 분기
- request, response, excluded 변환 adapter를 분리

## 구현 원칙

- `evidenceSnippet.contains(...)` 판정 사용 금지
- predicate와 failure outcome evidence 필수
- endpoint root에서 rule evidence까지 도달 가능해야 함
- RealEstate 등 특정 프로젝트 용어는 `PROJECT_EXTENSION`에 격리
- 기존 rule로 표현 가능한 의미는 중복 rule을 만들지 않음
- `BusinessRuleCategory`와 `RuleEffect`를 혼용하지 않음
- adapter가 constraint kind나 target 상태만으로 출력 방향을 추론하지 않음
- `BUSINESS_RESTRICTION`도 allowlist와 predicate/failure evidence gate를 통과해야 함

## 예상 영향 파일

- `analysis/support/rule/pack/*Rule.java`
- `analysis/support/rule/pack/InitialRulePacks.java`
- `analysis/support/rule/pack/InitialRuleCatalog.java`
- `analysis/support/fact/DefaultFactCodeGraphBuilder.java`
- `analysis/support/output/CandidateToOutputAdapter.java`
- `analysis/domain/candidate/RuleEffect.java`
- `analysis/domain/candidate/BusinessRuleCandidate.java`
- request precondition output adapter
- response assertion output adapter
- excluded business rule output adapter

## 테스트

- `findById().orElseThrow()` 존재성 규칙
- 버전 불일치 실패 경로
- 권한 거부 실패 경로
- 상태 guard 허용 및 거부 경로
- validator의 명시적 rejection 경로
- 이름만 비슷하고 구조적 근거가 없는 오탐 방지
- endpoint에서 도달 불가능한 rule 배제
- `REQUEST_REQUIREMENT`가 request에만 들어가는지 검증
- `RESPONSE_GUARANTEE`가 response에만 들어가는지 검증
- `BUSINESS_RESTRICTION`이 gate 통과 시 excluded에만 들어가는지 검증
- 하나의 candidate가 의도하지 않은 복수 배열에 들어가지 않는지 검증
- category가 같고 effect가 다른 candidate의 분리 검증

## 완료 조건

- 기존 `SERVICE_HINT`의 지원 의미가 개별 `GraphRule`로 표현됨.
- 신규 규칙 결과에 predicate와 failure evidence가 포함됨.
- 모든 `BusinessRuleCandidate`가 null이 아닌 `RuleEffect`를 가짐.
- 세 출력 배열이 effect 기준으로 명시적이고 상호 배타적으로 분리됨.
- response effect candidate가 `responseAssertions`로 변환됨.
- 동일 의미가 기존 정규화와 graph rule에서 중복 출력되지 않도록 테스트됨.
- `RuleBasedConditionNormalizer`의 서비스 규칙 로직을 호출하지 않아도 신규 테스트가 통과함.
