# Unit 03 Graph Rule Engine Tech Stack Decisions

## 결정

Unit 03은 신규 dependency, framework 또는 외부 인프라를 추가하지 않는다. Unit 01·02에서 승인하고 검증한 Java 및 테스트 스택을 재사용한다.

## Java 및 SPI

- Java 21 interface, record, enum 사용
- Rule registration은 constructor injection과 immutable `RulePack` 사용
- 동적 classpath scanning 또는 plugin framework 사용 안 함
- 중복·참조 index는 JDK `Map`과 `Set` 사용
- immutable snapshot은 JDK 방어적 복사 사용

## Example-Based Testing

- JUnit Jupiter 5.9.3 유지
- AssertJ 3.24.2 유지
- fake `GraphRule`과 `FailureOutcomePolicy`를 test fixture로 구현
- mocking framework 추가 없음

## Property-Based Testing

- 기존 `net.jqwik:jqwik:1.7.4` 재사용
- 신규 dependency 선언 없음
- 작은 fake Rule set, predicate set, candidate list generator 사용
- 순서 permutation, duplicate multiplicity, failure injection과 precedence tier property 검증
- shrinking과 seed 출력 유지

## Concurrency

초기 PoC engine은 단일 thread에서 결정적으로 실행한다. parallel Rule execution, executor service와 synchronization은 Unit 03 범위에 추가하지 않는다.

선택 근거:

- 현재 traversal budget과 초기 Rule Pack 규모에서 병렬화 필요성이 입증되지 않았다.
- single-thread 실행이 deterministic report 및 failure isolation 검증을 단순화한다.
- 향후 측정 결과가 병렬화를 요구하면 별도 NFR 및 설계 변경으로 다룬다.

## Serialization 및 Configuration

- core engine model에 Jackson annotation 추가 없음
- 외부 YAML/JSON Rule DSL 추가 없음
- pack은 Java 구성 및 constructor injection으로 제공
- output serialization은 후속 migration Unit 책임

## Dependency 변경 범위

`build.gradle` 변경 없음. JUnit, AssertJ, jqwik와 JavaParser 버전도 변경하지 않는다.

## 검증

- 기존 test platform에서 example 및 PBT 발견·실행
- 신규 engine domain이 Spring 또는 serialization library 없이 컴파일됨
- 전체 suite regression 0건
- 기존 output model과 Unit 01·02 public 계약 변경 0건
