# Unit 02 후보와 해석 모델 Tech Stack Decisions

## 결정

Unit 02는 신규 dependency나 framework를 추가하지 않는다. Unit 01에서 승인하고 도입한 현재 기술 스택을 재사용한다.

## Java 및 도메인 모델

- Java 21 record와 enum 사용
- core domain은 Jackson annotation에 의존하지 않음
- immutable collection은 JDK 방어적 복사로 구현
- candidate ID는 JDK 표준 hash API 또는 Unit 01의 결정적 ID 패턴을 재사용

## Example-Based Testing

- JUnit Jupiter 5.9.3 유지
- AssertJ 3.24.2 유지
- 기존 `useJUnitPlatform()` 유지

## Property-Based Testing

- `net.jqwik:jqwik:1.7.4` 재사용
- 신규 dependency 선언 없음
- property class는 `*Properties` 이름을 사용
- identity property는 최소 500회, 상태 조합 property는 최소 200회 실행
- shrinking과 seed 출력 유지

## Serialization

Unit 02 core model에는 serializer annotation을 추가하지 않는다. null, 빈 목록, `UNKNOWN`의 외부 표현은 후속 output adapter Unit에서 명시적으로 매핑한다.

## Dependency 변경 범위

`build.gradle` 변경 없음. JUnit, AssertJ, jqwik, JavaParser 버전 변경도 포함하지 않는다.

## 검증

- 기존 JUnit과 jqwik engine에서 신규 테스트 발견 및 실행
- 전체 test suite regression 0건
- core domain이 serialization library 없이 컴파일됨
- 기존 output model의 public signature 변경 0건
