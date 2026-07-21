# UOW-03 Detector 책임 정리

## 목표

Detector를 실패 경로와 validation seed 검출에 한정하고 구체 의미 분류를 semantic layer로 이동한다.

## 작업

- 직접 THROW, 승인된 RETURN, validation sink, delegated boolean propagation의 seed 계약을 정의한다.
- Optional, PasswordEncoder, null utility의 이름·signature 의미 판정을 semantic classifier로 이동할 준비를 한다.
- 기존 특수 경로와 새 semantic 경로를 shadow mode로 비교한다.
- unresolved call은 추측하지 않고 diagnostic으로 남긴다.

## 전환 순서

1. `UOW-03A`: Optional, PasswordEncoder, standard guard seed의 기존 동작을 contributor adapter로 격리한다.
2. `UOW-04/05`: 새 classifier를 기존 경로와 함께 shadow 실행하여 후보, constraint, evidence를 비교한다.
3. `UOW-03B`: 동등성이 검증된 contributor의 의미 판정만 항목별로 제거한다.

`UOW-04/05` 대응 경로가 GREEN이 되기 전에 기존 `requireNonNull`, Optional, PasswordEncoder seed를 삭제하지 않는다.

## 완료 조건

- Detector가 `NOT_NULL`, `EXISTENCE`, `PASSWORD_MATCH` 같은 최종 의미를 알지 않는다.
- 후보 수와 evidence가 baseline보다 감소하지 않는다.
- 특수 처리 제거는 대응 classifier가 GREEN인 항목부터 한 건씩 수행한다.
