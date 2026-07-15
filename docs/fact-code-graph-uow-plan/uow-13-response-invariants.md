# UoW 13 — ResponseAssert invariant 전파

## 목적

upstream invariant가 정상 응답 필드까지 유지될 때만 ResponseAssert를 생성한다.

## 허용 근거

- `if (x == null) throw` 같은 null guard
- `if (isEmpty(x)) throw` 같은 empty guard
- 검증 annotation 또는 persistence nullable Fact
- 등록된 non-empty 생성기 계약

## 보수적 정책

- 단순 DTO assignment는 assertion 근거가 아니다.
- 일부 정상 반환 경로에서 null 가능하면 공통 assertion을 만들지 않는다.
- 빈 collection이 정상일 수 있으면 `NOT_EMPTY`를 만들지 않는다.
- 정상 반환 경로들의 assertion 교집합만 공통 출력한다.

## 우선 대상

- `getProperties.propertyList NOT_NULL`
- property response의 `propertyType NOT_NULL`
- 증명 가능한 경우 `contractDetails NOT_EMPTY`

## 완료 조건

- invariant source부터 response field까지 evidence path가 있다.
- `NOT_NULL`과 `NOT_EMPTY`가 구분된다.
- 입력 조건을 응답 조건으로 단순 복사하지 않는다.

## 테스트

- invariant 유지/소실
- 다중 정상 반환 경로
- 빈 collection 허용
- conditional null assignment

