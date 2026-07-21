# UOW-06 Authorization 격리

## 목표

메서드 이름만으로 cancellation 권한을 생성하는 오탐을 기본 출력에서 제거한다.

## 작업

- `AuthorizationGuardCallRule`을 기본 pack에서 분리한다.
- annotation, qualified signature 또는 YAML descriptor로 권한 종류가 증명되는 경우만 분류한다.
- `canEdit`, `canView`, `canCancel`을 서로 다른 의미로 구분하거나 unresolved로 유지한다.
- 기존 PoC 기대값이 있다면 사용자 정의 rule fixture로 이전한다.

## 완료 조건

- 임의의 `can*` 메서드가 `HAS_CANCELLATION_PERMISSION`을 만들지 않는다.
- 근거 없는 권한 조건은 diagnostic으로 관측 가능하다.
- 명시적으로 등록한 cancellation guard만 기존 출력을 재현한다.

