# Stage 01 Planner Review

## Result

PASS

## Intent

Fact Graph의 failure-linked condition을 구조 후보로 탐지하고 독립 GraphRule을 격리 실행해 0개 이상의 의미 후보와 실행 report를 생성한다. Rule 등록 순서, confidence와 특정 프로젝트 명명 규칙으로 결과를 선택하지 않는다.

## Scope

- 신규 `analysis.domain.rule` engine domain과 SPI
- 신규 `analysis.support.rule` detector, registry, engine, invocation, dedup, conflict, fallback, report support
- Unit 01 nested branch outcome 직접 연결 수정
- Unit 02 Evidence-aware business candidate identity 확장
- detector와 engine example tests
- ordering, dedup, isolation, report jqwik properties
- 기존 output 경로 비변경 및 전체 regression

## Ordered Implementation

1. nested branch outcome fixture로 Unit 01 upstream 결함 재현 및 추출 범위 수정
2. 동일 predicate/rule의 서로 다른 Evidence fixture로 Unit 02 identity 충돌 재현 및 overload 추가
3. Rule domain, SPI, immutable pack과 report 모델 구현
4. conservative detector와 method scope 검증 구현
5. registry와 isolated invocation 구현
6. exact dedup, ambiguity, precedence와 unresolved fallback 구현
7. default engine orchestration 및 report 검증 구현
8. example tests, PBT, 전체 regression과 scoped diff 검증

## Scope Exclusions

- 구체 Java/JDK/Spring/JPA Rule
- 외부 Rule DSL과 동적 plugin loading
- parallel Rule execution
- target path resolver
- 기존 output pipeline 연결

## Planner Notes

선행 두 수정은 Unit 03 계약을 구현할 수 있게 하는 최소 dependency correction이다. 기존 public method는 제거하지 않고 새 overload와 더 정확한 branch 관계를 추가한다.
