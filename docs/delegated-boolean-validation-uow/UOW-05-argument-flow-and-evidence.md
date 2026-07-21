# UOW-05 인자 데이터 흐름과 evidence

## 목적

helper 내부 파라미터 기반 predicate를 API 입력에서 유래한 경로로 추적할 수 있게 한다.

## 작업

- 호출 인자 ordinal과 대상 메서드 파라미터 index를 연결한다.
- 기존 `HAS_ARGUMENT`, `ORIGINATES_FROM`, `VALUE_FLOWS_TO` 계약을 우선 사용한다.
- 부족하면 전용 edge를 추가하되 기존 의미와 중복하지 않는다.
- 승격 후보 evidence에 내부 predicate, helper call, 최종 failure outcome, 입력 origin을 포함한다.
- 컬렉션 lambda 흐름을 통해 `$.contractDetails[*].deposit` 같은 경로를 보존한다.

## 완료 조건

- 이름 유사성이나 snippet 추측 없이 인자와 파라미터가 연결된다.
- evidence만으로 API 입력에서 예외까지의 경로를 재구성할 수 있다.

