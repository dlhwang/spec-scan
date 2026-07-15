# UoW 02 — Evidence gate

## 목적

모든 실행 조건과 제외 규칙이 endpoint에서 도달 가능한 Fact 근거를 갖도록 강제한다.

## 적용 대상

- `requestPreconditions`
- `responseAssertions`
- `excludedBusinessRules`

## Gate 조건

1. endpoint node가 존재한다.
2. candidate evidence node가 존재한다.
3. endpoint에서 evidence까지 허용 edge로 도달할 수 있다.
4. source file과 line이 존재한다.
5. rule ID 또는 derivation ID가 존재한다.

## 작업 범위

- 공통 graph evidence gate 구현
- endpoint 도달성 계산 공통화
- cross-endpoint와 ambiguous 후보 차단
- snippet만 있고 Fact node가 없는 후보 차단
- 거부 이유를 diagnostic으로 보존

## 완료 조건

- evidence 없는 조건은 출력되지 않는다.
- 다른 endpoint의 조건이 잘못 귀속되지 않는다.
- 거부 사유를 코드로 구분할 수 있다.

## 테스트

- reachable/unreachable evidence
- cross-endpoint only
- ambiguous endpoint path
- source location 누락

