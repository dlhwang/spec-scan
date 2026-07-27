# Deep Interview Specification: Generic Semantic Recipe 범위

> **문서 유형**: 요구사항 Revision 2 영속 사양
> **상태**: 승인 대기
> **관련 문서**: `../requirements-v2.md`
> **선행 사양**: `deep-interview-yaml-externalization-boundary.md`

## 의사결정 사항 (Decisions)

1. **지원 envelope**: Java 기반 Spring MVC repository를 대상으로 한다.
   - Controller endpoint를 root로 reachable application subgraph를 분석한다.
   - 모든 업무 의미의 이해가 아니라 지원 recipe 실행과 불완전성의 명시적 보고를 보장한다.

2. **외부화 단위**: 특정 업무 조건이 아니라 generic classifier family의 semantic recipe를 외부화한다.
   - `quantity < 0`은 YAML 입력이 아니라 source에서 발견되는 실행 바인딩이다.
   - recipe는 failure condition, binary comparison, origin resolution과 normalization 같은 구조를 선언한다.

3. **YAML 책임 분리**: engine configuration, semantic recipes와 framework/library catalog를 분리한다.
   - traversal budget은 configuration으로 외부화한다.
   - graph traversal 집행과 primitive 구현은 Java에 유지한다.

4. **Framework semantics**: Spring Data, Spring Security와 JPA는 선택적 pack으로 관리한다.
   - Password validation은 Spring MVC core가 아니라 Spring Security evidence가 있을 때만 활성화한다.

5. **Delegated semantics**: graph evidence 없이 domain target/operator를 추측하지 않는다.
   - 일반화할 수 없으면 unresolved로 남기거나 rule을 제거한다.

6. **회귀 정책**: 기존 결과를 `PRESERVE`, `REPLACE`, `UNSUPPORTED`로 구분한다.
   - 알려진 hardcoded 결과까지 동등성 명목으로 보존하지 않는다.

## 하드 제약 조건 (Constraints)

1. Core recipe와 primitive에 특정 repository, package, aggregate, field 또는 업무 상태 이름을 하드코딩하지 않는다.
2. Concrete field path, literal과 enum value는 YAML에 선언하지 않고 CodeGraph runtime binding에서 가져온다.
3. Graph evidence로 증명할 수 없는 target, operator와 business semantics를 생성하지 않는다.
4. `FactCodeGraph` 생성, index, cycle detection, traversal scheduling과 budget 집행은 Java 책임으로 유지한다.
5. `max_depth`, `max_visited_methods_per_api`, `max_edges_per_api`는 YAML configuration에서 읽되 Java가 검증하고 집행한다.
6. Budget 초과, type resolution 실패와 의미 미분류를 정상 결과로 숨기지 않는다.
7. `custom_expression`, inline script, reflection과 arbitrary callback을 허용하지 않는다.
8. Password semantics는 Spring Security type/signature evidence 없이 method name만으로 확정하지 않는다.
9. Delegated guard는 `order.`, `currentUser`, `HAS_CANCELLATION_PERMISSION` 같은 값을 생성해서는 안 된다.
10. Generalization은 cross-repository corpus와 rename/mutation holdout으로 증명한다.
11. `PRESERVE` 결과는 diff 0건이어야 하며 `REPLACE` 결과는 corrected expectation과 일치해야 한다.
12. Revision 2 승인 전에는 Workflow Planning 또는 구현으로 진행하지 않는다.

## 선행 사양과의 관계

- 선행 사양의 제한형 primitive, Java runtime 불변식, arbitrary expression 금지와 Phase 2 Gate 결정은 유지한다.
- 선행 사양의 “대표 기존 룰 5종”은 “generic classifier family 및 선택적 framework pack”으로 대체한다.
- 선행 사양의 “기존 결과 완전 동등성”은 disposition별 회귀 정책으로 대체한다.

## 관련 요구사항

- `R2-YAML-001` ~ `R2-YAML-010`
- `NFR-R2-001` ~ `NFR-R2-009`

## 인터뷰 메타데이터

| 항목 | 값 |
| :--- | :--- |
| Slug | `generic-semantic-recipe-scope` |
| 작성 일시 | `2026-07-22T10:22:36.2801573+09:00` |
| 참여자 | 사용자, Codex |
| 선행 문서 | `../requirements-v2.md` |

## 작성 완료 체크리스트

- [x] 외부화 단위가 generic semantic recipe로 명시됨
- [x] Spring MVC 지원 envelope가 명시됨
- [x] concrete value의 runtime binding 원칙이 명시됨
- [x] hardcoded domain semantics 금지가 명시됨
- [x] cross-repository 검증 기준이 명시됨

