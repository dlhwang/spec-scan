# UoW 09 — POST PreCondition

## 대상

`POST /api/estate/properties`

## 목적

UoW 04–08의 기능을 이용해 첫 번째 endpoint 수직 슬라이스를 완성한다.

## 목표 출력

- `$.propertyType NOT_NULL`
- `$.contractDetails NOT_EMPTY`
- `$.contractDetails[*].contractType NOT_NULL`

downstream이 조건부 구조를 지원할 때만 다음을 추가한다.

- `JEONSE → deposit GT 0`
- `MONTHLYRENT → deposit GT 0 AND rent GT 0`

## 작업 범위

- `Property.validate` guard rule
- `ContractDetail.isNotValid` guard rule
- 실패 조건의 기계적 반전
- request path origin과 evidence 첨부

## 완료 조건

- 각 조건에 endpoint부터 guard까지의 Fact 경로가 있다.
- 조건부 구조를 지원하지 않으면 금액 조건을 억지로 평탄화하지 않는다.
- 다른 endpoint에 조건이 잘못 귀속되지 않는다.

## 테스트

- RealEstate POST golden assertion
- null과 empty 구분
- 허용 Operator 검증
- 조건별 source evidence 검증

