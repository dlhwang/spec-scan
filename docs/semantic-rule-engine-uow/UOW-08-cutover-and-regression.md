# UOW-08 Engine Cutover와 회귀

## 목표

N×M Rule 실행을 semantic shape 기반 단일 dispatch로 교체하고 기존 Rule 경로를 안전하게 제거한다.

## 작업

- payload kind, method identity, expression operator 기반 classifier dispatch index를 만든다.
- 기존 engine과 새 engine을 동일 corpus에서 shadow 실행한다.
- predicate, normalized constraint, endpoint output, evidence를 비교한다.
- conflict와 unresolved 처리를 semantic pipeline에 맞게 단순화한다.
- 동등성이 검증된 기존 Rule 및 중복 support를 단계적으로 삭제한다.

## 완료 조건

- 후보당 모든 classifier를 호출하지 않는다.
- graph index는 graph당 한 번 생성된다.
- 전체 테스트와 golden corpus가 통과한다.
- false positive가 증가하지 않고 ContractDetail 최종 출력 커버리지가 증가한다.
- 삭제 전후 diagnostic과 evidence 품질이 유지된다.

## 중단 기준

- 새 pipeline이 endpoint 귀속이나 JSONPath를 잃으면 cutover를 중단한다.
- snippet fallback 비중이 증가하면 구조 fact 보강 UoW로 되돌린다.
- 단순 코드 감소만 있고 정확도·설명 가능성 개선이 없으면 기존 경로를 제거하지 않는다.
