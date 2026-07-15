# UoW 16 — RealEstate golden regression

## 목적

RealEstate 6개 API를 이용해 전체 개선의 정확성과 오탐 방지를 고정한다.

## 대상 API

- `login`
- `getProperties`
- `getProperty`
- `post`
- `put`
- `remove`

## 검증 항목

### Operator

- 12개 외 Operator가 없다.
- `EXISTS_IN_REPOSITORY`, `REQUIRED`, `EQUALS`가 없다.

### Evidence

- 모든 출력에 Fact node ID가 있다.
- endpoint 도달 경로와 source location이 있다.

### PreCondition

- 도메인 guard가 관련 endpoint에만 적용된다.
- repository 존재성을 `NOT_EMPTY`로 오변환하지 않는다.

### ResponseAssert

- DELETE status는 `EQ 204`, body는 `EMPTY`다.
- 모든 정상 경로에서 참인 assertion만 존재한다.

### excludedBusinessRules

- strict evidence gate를 통과한다.
- 근거가 부족하면 빈 배열이다.

### 결정성

- 반복 실행에서 node ID, 정렬, JSON 결과가 같다.

## 완료 조건

- 6개 API golden test 통과
- 기존 synthetic rule 및 false-positive 회귀 테스트 통과
- warning이 원인별 diagnostic으로 분류됨
- RealEstate 전용 production 하드코딩이 없음

