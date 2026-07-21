# UOW-02 boolean switch 조건 합성

## 목적

boolean switch expression의 case 선택 조건과 case 결과식을 하나의 실패 경로로 표현한다.

## 작업

- `case JEONSE -> deposit <= 0`을 `contractType == JEONSE && deposit <= 0` 의미로 보존한다.
- `case MONTHLYRENT -> rent <= 0 || deposit <= 0`도 동일하게 합성한다.
- 상수 `true`/`false`, `yield`, block body의 의미를 구분한다.
- 일반 switch statement의 제어 흐름과 혼동하지 않는다.

## 완료 조건

- 합성 condition이 구조화된 노드와 edge로 탐색 가능하다.
- false를 반환하는 default branch가 실패 predicate로 승격되지 않는다.
- 기존 switch statement 테스트가 회귀하지 않는다.

