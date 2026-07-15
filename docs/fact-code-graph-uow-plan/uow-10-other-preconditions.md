# UoW 10 — PUT/GET/DELETE PreCondition

## 대상

- `PUT /api/estate/properties/{propertyId}`
- `GET /api/estate/properties/{propertyId}`
- `DELETE /api/estate/properties/{propertyId}`

## 작업 범위

- POST 도메인 guard를 PUT에 재사용
- path variable의 구조적 null/empty 조건 분석
- `findById(...).orElseThrow(...)` Fact와 실패 경로 연결

## repository 존재성 정책

- `EXISTS_IN_REPOSITORY` Operator를 만들지 않는다.
- 존재성을 `NOT_EMPTY`로 약화하지 않는다.
- 실행 불가능하지만 graph-backed라면 strict excluded 후보로 넘긴다.
- evidence가 부족하면 출력하지 않는다.

## 완료 조건

- PUT에 관련 도메인 조건만 나타난다.
- GET/DELETE에서 repository guard가 graph에 유지된다.
- 존재성 의미가 허용 Operator로 오변환되지 않는다.

## 테스트

- endpoint별 golden condition
- cross-endpoint contamination
- repository guard 출력 정책

