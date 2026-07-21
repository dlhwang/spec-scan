# UOW-06 RuleOutput 회귀

## 목적

승격된 predicate가 해당 POST endpoint의 `requestPreconditions` 또는 엄격한 근거가 있는 `excludedBusinessRules`에 반영되는지 검증한다.

## 작업

- `RuleOutputService`가 동일한 authoritative `FactCodeGraph`와 승격기를 사용하도록 연결한다.
- null, enum 분기, 숫자 경계 rule 분류를 검증한다.
- 다른 endpoint로 후보가 누출되지 않는지 검증한다.
- 구체 predicate가 있는 경우 `isNotValid(...)` 같은 불투명한 중복 rule을 제거한다.

## 완료 조건

- POST endpoint 출력에 세 도메인 제약이 나타난다.
- endpoint 귀속, JSONPath, operator가 근거와 일치한다.
- 기존 RuleOutput 테스트와 전체 테스트가 통과한다.

