# Unit 03 Graph Rule Engine 구현 요약

## 결과

Fact Graph에서 failure-linked predicate를 탐지하고 독립 GraphRule을 결정적이고 실패 격리된 방식으로 실행하는 병렬 Rule Engine을 구현했다. 구체 Rule Pack과 기존 output 연결은 포함하지 않았다.

## 선행 정확도 수정

### Nested branch outcome

Unit 01 `FactMethodVisitor`가 nested `if` 내부 throw를 상위 condition에 직접 연결하던 `findAll` 추출을 direct branch statement 추출로 변경했다. nested condition은 자체 outcome edge만 가진다.

### Evidence-aware meaning identity

Unit 02 candidate ID에 Evidence fingerprint overload를 추가했다. 기존 overload는 유지하며 신규 engine은 같은 predicate와 ruleId라도 Evidence가 다르면 서로 다른 candidate ID를 생성할 수 있다.

## 구현 내용

- method scope, detector, failure policy와 GraphRule SPI
- Java/JDK/Spring/JPA/project extension Rule layer
- immutable Rule Pack과 explicit precedence tier
- duplicate ruleId 사전 거부와 canonical Rule ordering
- throw 기본 탐지와 return policy 확장
- policy exception의 detection report 격리
- Rule exception, null list, null/invalid candidate 격리
- Evidence node ID와 role 기반 exact dedup
- category ambiguity와 non-destructive precedence diagnostic
- predicate별 explicit unresolved fallback
- trace-derived execution counters와 stable diagnostics
- candidate와 report를 조합한 immutable engine result
- single-thread deterministic execution

## 범용성 경계

- detector는 typed Fact node와 edge만 사용한다.
- engine은 특정 class, package, method와 business 이름을 하드코딩하지 않는다.
- Rule 추가는 GraphRule 구현과 Rule Pack 등록만 요구한다.
- simple name 단독 semantic 확정 로직을 engine에 두지 않는다.
- framework 의미는 RuleLayer와 후속 concrete Rule Pack 책임이다.

## 검증 Evidence

- 신규 Rule tests: 14개
- Rule example-based tests: 10개
- Rule jqwik properties: 4개
- upstream regression examples: 2개
- Evidence-aware identity property: 500 tries 추가
- Rule PBT tries: ordering 200, dedup 300, isolation 200, report 300
- PBT seed: JUnit XML에 property별 기록
- 전체 suite: 82/82 통과, 25 suites
- failures/errors/skips: 0/0/0
- 기존 output 및 legacy normalizer 변경: 없음
- 작업 범위 scoped `git diff --check`: 통과

## 검증 중 발견하고 수정한 결함

1. Unit 01 branch visitor의 recursive `findAll`이 nested throw를 상위 condition에도 연결하는 문제를 수정했다.
2. Unit 02 의미 후보 ID가 Evidence를 구분하지 못하는 충돌을 호환 overload로 보완했다.
3. finite jqwik generator가 exhaustive mode에서 승인된 tries보다 일찍 종료되는 문제를 blocker Goal G011로 기록하고 randomized mode로 수정했다.
4. policy failure를 기존 list-only detector 계약에서 report로 전달할 수 있도록 호환 `ReportedValidationCandidateDetector`와 immutable `CandidateDetectionResult`를 추가했다.

## 기존 작업트리 주의사항

기존 미커밋 `build.gradle`, 문서와 사용자 파일은 보존했다. 이번 Unit이 의도한 upstream 두 파일과 신규 rule package/test 범위의 scoped diff check는 통과했다.

## PBT Compliance

- PBT-01~04: ordering, dedup, isolation, report와 identity invariant 충족
- PBT-07: 작은 fake Rule, multiplicity와 count generator 사용
- PBT-08: shrinking 유지 및 seed 기록
- PBT-09: 기존 jqwik 1.7.4 재사용
- PBT-10: Rule example 10개와 property 4개 병행
