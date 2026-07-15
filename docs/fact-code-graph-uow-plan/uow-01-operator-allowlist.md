# UoW 01 — Operator allowlist

## 목적

최종 출력 Operator를 12개 canonical 값으로 제한한다.

## 허용 값

`EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `CONTAINS`, `NOT_CONTAINS`, `EMPTY`, `NOT_EMPTY`, `NULL`, `NOT_NULL`

## 작업 범위

- canonical Operator 모델 또는 공통 상수 정의
- `EQUALS → EQ`, `GREATER_THAN → GT` 등 명확한 별칭만 매핑
- `REQUIRED`, `EXISTS_IN_REPOSITORY`, `VALIDATION_LOGIC` 같은 의미 Operator 제거
- exporter 직전 allowlist 검증
- 지원하지 않는 값은 diagnostic 처리

## 규칙

- `REQUIRED`는 자동 변환하지 않는다.
- null 거부 Fact가 있으면 `NOT_NULL`이다.
- empty 거부 Fact가 있으면 `NOT_EMPTY`이다.
- 근거가 불명확하면 출력하지 않는다.

## 완료 조건

- 최종 JSON에 허용 목록 밖의 Operator가 없다.
- 미지원 Operator를 유사 Operator로 추정하지 않는다.

## 테스트

- 12개 허용 값 통과
- 미지원 값 거부
- 별칭 정규화
- `REQUIRED` 오변환 방지

