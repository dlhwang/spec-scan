# UOW-03 위임 실패 조건 승격

## 목적

직접 실패 가드가 호출한 내부 boolean 메서드의 실패 반환 경로를 `PredicateCandidate`로 승격한다.

## 작업

- 별도 `DelegatedBooleanPredicatePromoter`를 둔다.
- 직접 `THROW` 후보의 condition에서 method call을 찾는다.
- `METHOD_CALL -> CALLS(role=TARGET) -> METHOD` 경로로 호출 대상을 확정한다.
- 대상 메서드가 반환한 boolean 경로 중 호출부 실패 극성과 일치하는 내부 조건만 승격한다.
- 재귀 호출과 순환 호출은 visited 집합과 깊이 제한으로 차단한다.
- 원래의 불투명한 helper 후보는 구체 후보가 생성되면 중복 출력하지 않는다.

## 완료 조건

- ContractDetail의 세 조건이 후보가 된다.
- 후보가 없는 unresolved 호출은 기존 직접 후보를 유지하고 diagnostic을 남긴다.
- 후보 ID와 정렬이 결정적이다.

