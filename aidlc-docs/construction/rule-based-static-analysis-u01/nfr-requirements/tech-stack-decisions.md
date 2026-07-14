# Unit 01 Fact Code Graph Tech Stack Decisions

## Java 및 빌드

- Java 21 유지
- Gradle Java plugin과 기존 `useJUnitPlatform()` 유지
- JavaParser 3.25.7 및 symbol solver 유지

Unit 01은 parser 또는 Java toolchain 업그레이드를 포함하지 않는다.

## Example-Based Testing

- JUnit Jupiter 5.9.3 유지
- AssertJ 3.24.2 유지
- JUnit Platform launcher의 기존 runtime dependency 유지

기존 44개 회귀 테스트가 사용하는 test platform을 변경하지 않는다.

## Property-Based Testing

- 선택: `net.jqwik:jqwik:1.7.4`
- configuration: `testImplementation`
- 실행: 기존 `useJUnitPlatform()`을 통해 일반 Gradle test task에 포함

선택 근거:

- jqwik 1.7.4는 JUnit Platform 1.9.3 세대와 정렬된다.
- 현재 JUnit Jupiter 5.9.3을 유지할 수 있다.
- Unit 01의 PBT 요구를 충족하면서 테스트 플랫폼 전체 migration을 피한다.

수용한 tradeoff:

- 최신 jqwik 기능은 사용하지 않는다.
- 전체 test stack upgrade는 별도 유지보수 Unit으로 분리한다.

## PBT 실행 정책

- property class 이름은 `*Properties`로 끝낸다.
- 기본 tries는 property 성격에 따라 명시하되 최소 100회로 한다.
- deterministic identity property는 최소 500회로 한다.
- CI 또는 로컬 실패 output에서 jqwik seed를 보존한다.
- shrinking을 끄는 설정을 추가하지 않는다.

## Serialization

Unit 01 core graph 모델은 Jackson annotation에 의존하지 않는다. JSON export가 필요해지는 후속 Unit에서 adapter 또는 serializer 설정을 추가한다.

## Configuration

초기 구현에서는 별도 YAML parser dependency를 추가하지 않는다. `FactGraphTraversalBudget.defaults()`와 명시적 constructor 주입으로 기본값 및 override를 제공한다.

향후 CLI 또는 설정 파일 연결은 adapter 책임으로 둔다.

## Dependency 변경 범위

승인된 구현 단계에서 `build.gradle`에 다음 한 줄만 추가한다.

```gradle
testImplementation 'net.jqwik:jqwik:1.7.4'
```

JUnit, AssertJ, JavaParser 버전은 변경하지 않는다.

## 검증

- Gradle dependency resolution 성공
- 기존 JUnit 테스트 실행 가능
- jqwik sample property 발견 및 실행
- 전체 test suite regression 0건
- dependency tree에서 JUnit Platform conflict 없음

