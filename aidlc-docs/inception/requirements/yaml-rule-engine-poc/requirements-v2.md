# YAML Rule Engine PoC — 요구사항 v2

> **Revision**: 2
> **상태**: 승인 완료 (2026-07-22)
> **이전 문서**: `requirements.md`
> **변경 이유**: 외부화 대상을 특정 업무 규칙이 아니라 Spring MVC CodeGraph에 적용되는 제네릭 semantic classifier recipe로 재정의

## 1. 의도 분석 요약

- **사용자 요청**: Java 기반 Spring MVC 저장소의 Controller에서 시작하는 inter-procedural application subgraph를 대상으로, 실패 조건·데이터 흐름·상태 비교·프레임워크 관용구를 발견하고 정규화하는 제네릭 시맨틱 알고리즘을 YAML로 외부화할 수 있는지 검증한다.
- **요청 유형**: 기존 Semantic Analysis & Rule Engine Layer의 아키텍처 개선 PoC
- **지원 범위**: Java + Spring MVC 저장소
- **분석 범위**: Spring Controller API root에서 도달 가능한 애플리케이션 subgraph
- **복잡도**: 복잡
- **구현 상태**: 미착수

## 2. 핵심 목적

PoC의 목적은 `quantity < 0`, `order.status`, `HAS_CANCELLATION_PERMISSION` 같은 특정 저장소의 업무 규칙을 YAML에 등록하는 것이 아니다.

외부화 대상은 다음과 같은 **classifier family의 메타 룰**이다.

- 실패 분기를 제어하는 binary comparison을 요청 입력 constraint로 정규화
- null/empty guard의 대상과 의미를 정규화
- Optional lookup failure를 existence constraint로 정규화
- enum 및 복합 조건을 allowed-value/range constraint로 정규화
- 알려진 framework API 호출을 library semantics로 정규화

실제 field path, literal, enum value, method evidence와 target은 YAML 상수가 아니라 분석 실행 시 CodeGraph에서 바인딩되어야 한다.

## 3. 지원 범위와 보장 수준

### 3.1 지원 범위

- Java source root의 `.java` 파일
- Spring MVC annotation 기반 Controller 및 endpoint
- Controller API에서 호출되는 애플리케이션 package 내부 메서드
- CodeGraph가 표현하는 condition, outcome, call, operand, read/write, origin과 value-flow 관계
- Java language, JDK idiom 및 선택적으로 활성화되는 Spring ecosystem rule pack

### 3.2 “모든 Spring MVC Repo 탐색”의 정의

지원되는 Java/Spring MVC 구조를 가진 모든 입력 저장소에 대해 다음을 수행한다.

1. endpoint 탐색을 시도한다.
2. API별 reachable application subgraph 생성을 시도한다.
3. 활성화된 generic semantic recipe를 실행한다.
4. 지원되는 패턴은 normalized constraint로 출력한다.
5. 지원되지 않거나 불완전한 패턴은 `UNCLASSIFIED`, `UNRESOLVED` 또는 `PARTIAL_ANALYSIS`로 명시한다.

이는 모든 Spring MVC 저장소의 모든 업무 의미를 이해한다는 보장이 아니다. 분석 시도, 지원 범위 내 정규화와 불완전성의 명시적 보고를 보장한다.

### 3.3 제외 범위

- Kotlin 및 비 Java 언어
- Spring WebFlux functional routing 및 비 Spring MVC framework
- `FactCodeGraph` node/edge 생성 알고리즘의 YAML 외부화
- reflection, AOP runtime behavior와 generated code의 완전한 복원
- graph evidence가 없는 업무 의미의 추측
- 범용 script, arbitrary expression 또는 YAML에서의 임의 Java callback
- Phase 3 tracer 및 Phase 4 hot-reload

## 4. YAML 책임 분리

### 4.1 Engine Configuration

그래프 탐색 자원과 실행 정책을 외부화한다.

```yaml
engine:
  traversal:
    max_depth: 15
    max_visited_methods_per_api: 500
    max_edges_per_api: 10000
  behavior:
    deterministic_order: true
    report_unclassified: true
    report_truncation: true
```

### 4.2 Semantic Recipes

CodeGraph 사실을 typed primitive로 매칭·바인딩·정규화·출력하는 제네릭 classifier algorithm을 선언한다.

```yaml
recipes:
  - id: JAVA_BINARY_FAILURE_GUARD
    kind: semantic_recipe
    steps:
      - op: match.failure_condition
      - op: match.binary_comparison
      - op: resolve.request_input_path
      - op: normalize.comparison_by_failure_polarity
      - op: emit.normalized_constraint
```

각 `op`는 Java runtime이 제공하는 유한하고 typed된 primitive identifier다. 임의 표현식은 허용하지 않는다.

### 4.3 Framework/Library Catalog

Java/Spring 생태계가 공통으로 정의한 API signature와 의미를 선언한다.

```yaml
catalog:
  guards:
    - signature: java.util.Objects.requireNonNull
      argument_role: guarded_value
      semantics: NOT_NULL
```

특정 저장소의 field, aggregate 이름, 업무 상태 또는 예외 메시지는 catalog에 허용하지 않는다.

### 4.4 분석 결과

다음 값은 YAML 입력이 아니라 실행 결과다.

- `$.quantity` 같은 request target path
- `0`, `100`, enum constant 같은 expected value
- `GTE`, `EXISTS`, `STATE_IN` 같은 normalized operator
- source node와 evidence chain
- resolution status와 diagnostic

## 5. 기능 요구사항

## Requirement R2-YAML-001: Spring MVC 지원 경계

### Description

분석기는 저장소 이름이나 도메인에 관계없이 지원되는 Spring MVC endpoint를 분석 대상으로 받아야 한다.

### Acceptance Criteria

- Controller class, method, package 또는 field의 프로젝트별 이름을 allowlist로 요구하지 않는다.
- `@Controller`, `@RestController` 및 지원 mapping annotation으로 endpoint를 발견한다.
- endpoint별 reachable application subgraph를 독립적으로 생성한다.
- source root 또는 endpoint를 분석할 수 없는 경우 원인과 scope를 diagnostic으로 출력한다.
- 지원되지 않는 repository가 전체 실행 실패가 아니라 명시적 unsupported 결과를 만든다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 서로 다른 package/domain 이름의 Spring MVC repository에서 endpoint 및 graph 생성 결과
- **Goal-Driven Evidence Tracking**: `G-R2-SCOPE-01`

## Requirement R2-YAML-002: Traversal Configuration 외부화

### Description

현재 Java 기본값인 depth, visited method와 edge budget을 PoC evaluation용 engine configuration YAML로 외부화해야 한다. Phase 2 정상 scan은 이 YAML에 의존하지 않고 기존 default budget을 유지한다.

### Acceptance Criteria

- `max_depth`, `max_visited_methods_per_api`, `max_edges_per_api`를 양의 정수로 검증한다.
- 기본값은 각각 `15`, `500`, `10000`이다.
- configuration은 명시적 PoC evaluation 시작 시 immutable snapshot으로 로딩한다.
- YAML load/validate/compile 실패는 evaluation만 거부하며 정상 scan의 시작과 결과에 영향을 주지 않는다.
- budget 초과 시 해당 API를 `PARTIAL_ANALYSIS`로 표시하고 초과한 budget, 현재 값과 중단 지점을 기록한다.
- 잘못된 값이 있으면 PoC evaluation 시작 전에 configuration 전체를 거부한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration
- **Required Test Evidence**: 기본값, override, invalid value 및 각 budget truncation test
- **Goal-Driven Evidence Tracking**: `G-R2-CONFIG-01`

## Requirement R2-YAML-003: Typed Semantic Primitive Registry

### Description

Java runtime은 YAML recipe가 사용할 수 있는 유한한 semantic primitive registry를 제공해야 한다.

### Acceptance Criteria

- primitive는 stable ID, typed input/output, 허용 node/edge 종류와 종료 계약을 가진다.
- 초기 category는 `match`, `bind`, `follow`, `resolve`, `normalize`, `quantify`, `emit`을 포함한다.
- 존재하지 않거나 type이 맞지 않는 primitive 조합은 load 단계에서 거부한다.
- 모든 `follow` primitive는 cycle detection과 traversal budget을 적용한다.
- primitive는 특정 field, aggregate 또는 단일 repository 이름을 전제로 하지 않는다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, schema validation
- **Required Test Evidence**: primitive 계약별 정상 조합, type mismatch와 unknown primitive test
- **Goal-Driven Evidence Tracking**: `G-R2-PRIMITIVE-01`

## Requirement R2-YAML-004: Runtime Binding 기반 결과 생성

### Description

YAML recipe는 source code에서 발견한 node를 변수로 바인딩하고 결과의 target, operator와 expected value를 해당 바인딩에서 생성해야 한다.

### Acceptance Criteria

- concrete field path와 literal을 recipe 상수로 작성하지 않는다.
- request target은 origin/value-flow와 binding metadata로 해석한다.
- expected value는 matched literal 또는 enum node에서 가져온다.
- normalized operator는 source operator와 failure polarity를 typed normalizer로 계산한다.
- target 또는 의미를 증명할 수 없으면 값을 발명하지 않고 `UNRESOLVED`로 출력한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration, metamorphic
- **Required Test Evidence**: `quantity`, `count`, `requestedUnits`로 이름을 변경해도 동일 recipe가 올바른 각 target을 바인딩하는 test
- **Goal-Driven Evidence Tracking**: `G-R2-BINDING-01`

## Requirement R2-YAML-005: Core Semantic Recipe Family

### Description

Java/Spring MVC 저장소에서 구조적으로 반복되는 classifier family를 core semantic recipe로 검증한다.

### Acceptance Criteria

- Binary failure guard: comparison, failure outcome, polarity와 literal binding
- Composite validation: `&&`, `||`, enum guard와 복수 requirement
- Standard null/empty guard: argument role 및 normalized semantics
- JDK Optional lookup failure: terminal과 failure semantics
- 각 family는 구체 도메인 이름 없이 최소 2개 이상의 서로 다른 fixture/repository에 적용된다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: unit, integration, regression
- **Required Test Evidence**: family별 recipe fixture, cross-repository 결과와 rename test
- **Goal-Driven Evidence Tracking**: `G-R2-CORE-01` ~ `G-R2-CORE-04`

## Requirement R2-YAML-006: 선택적 Framework Rule Pack

### Description

Spring Data, Spring Security와 JPA semantics는 core가 아니라 dependency/graph evidence에 따라 활성화되는 선택적 framework pack으로 관리한다.

### Acceptance Criteria

- Spring Data pack은 repository lookup과 Optional terminal을 다룬다.
- Spring Security pack은 `PasswordEncoder` type/signature evidence가 있을 때 password match semantics를 다룬다.
- JPA pack은 `@Version` evidence가 있을 때 optimistic lock semantics를 다룬다.
- 단순 method name만으로 framework semantics를 확정하지 않는다.
- 관련 dependency/evidence가 없는 repository에서는 해당 pack이 candidate를 생성하지 않는다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: integration, negative regression
- **Required Test Evidence**: framework 존재/부재, 동일 method name의 unrelated call과 type-resolution failure test
- **Goal-Driven Evidence Tracking**: `G-R2-PACK-01` ~ `G-R2-PACK-03`

## Requirement R2-YAML-007: Delegated Guard 일반화 또는 제거

### Description

현재 `DelegatedGuardRule`의 도메인 추측을 제거하고 graph evidence로 의미를 증명할 수 있는 경우에만 generic delegated guard를 생성해야 한다.

### Acceptance Criteria

- `order.` 같은 aggregate prefix를 하드코딩하지 않는다.
- `currentUser`, `HAS_CANCELLATION_PERMISSION` 같은 업무 target/operator를 발명하지 않는다.
- 호출 인자, parameter binding, return predicate와 domain origin을 evidence로 연결할 수 있어야 한다.
- 일반화된 target/operator를 증명할 수 없으면 `UNRESOLVED_DELEGATED_GUARD`로 남긴다.
- PoC 범위에서 일반화가 불가능하면 해당 rule을 기본 pack에서 제거하는 것이 허용된다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: regression, negative
- **Required Test Evidence**: 기존 hardcoded order/cancellation 결과가 생성되지 않고 evidence 유무에 따라 resolved/unresolved가 구분되는 test
- **Goal-Driven Evidence Tracking**: `G-R2-DELEGATED-01`

## Requirement R2-YAML-008: 미분류 및 불완전성 보고

### Description

분석기는 지원되지 않는 의미, type resolution 실패와 traversal truncation을 정상 탐지 결과와 구분해야 한다.

### Acceptance Criteria

- 의미를 분류하지 못한 failure candidate는 `UNCLASSIFIED`로 집계한다.
- target 또는 origin을 찾지 못하면 `UNRESOLVED`로 표시한다.
- budget 초과 또는 graph 일부 누락은 `PARTIAL_ANALYSIS`로 표시한다.
- diagnostic에는 repository, endpoint, node, classifier/primitive와 원인 코드가 포함된다.
- 불완전한 결과를 정상 완료로 조용히 승격하지 않는다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: integration, negative
- **Required Test Evidence**: unknown custom guard, unresolved receiver와 각 budget 초과 fixture
- **Goal-Driven Evidence Tracking**: `G-R2-DIAG-01`

## Requirement R2-YAML-009: Cross-Repository 일반화 Corpus

### Description

PoC는 synthetic 단일 예제가 아니라 구조와 이름이 다른 Spring MVC repository에서 recipe 일반성을 검증해야 한다.

### Acceptance Criteria

- 5~10개의 실제 또는 현실적인 독립 Spring MVC corpus를 선정한다.
- 각 corpus는 약 6~10개 endpoint를 포함하거나 동등한 분석 복잡도를 가진다.
- package, aggregate, field와 method 이름이 서로 다른 corpus를 포함한다.
- 동일 semantic shape의 rename/mutation variant를 별도 holdout으로 둔다.
- 결과는 graph coverage와 semantic rule coverage로 분리하여 측정한다.
- recipe 또는 Java code를 holdout 이름에 맞춰 튜닝하지 않는다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: evaluation, holdout, regression
- **Required Test Evidence**: repository별 graph coverage, semantic coverage, unclassified/unresolved/truncation report
- **Manual Verification Rationale**: corpus 대표성과 label은 사람의 검토가 필요함
- **Goal-Driven Evidence Tracking**: `G-R2-CORPUS-01`

## Requirement R2-YAML-010: 회귀와 교정 분리

### Description

기존 결과를 무조건 보존하지 않고 검증된 동작과 알려진 잘못된 동작을 구분해 비교해야 한다.

### Acceptance Criteria

- baseline entry를 `PRESERVE`, `REPLACE`, `UNSUPPORTED`로 분류한다.
- `PRESERVE` 항목은 외부 관찰 결과 diff 0건을 요구한다.
- `REPLACE` 항목은 승인된 corrected expectation과 일치해야 한다.
- `UNSUPPORTED` 항목은 잘못된 의미를 만들지 않고 명시적 diagnostic을 생성해야 한다.
- 신규 내부 trace와 performance metric만 외부 동등성 비교에서 제외한다.

### Verification Expectations

- **Automation Required**: Yes
- **Expected Test Level**: snapshot, regression
- **Required Test Evidence**: disposition별 diff 및 corrected expectation report
- **Goal-Driven Evidence Tracking**: `G-R2-REGRESSION-01`

## 6. 비기능 요구사항

- **NFR-R2-001 결정론**: 동일 source, graph, configuration과 recipe pack은 동일 결과와 정렬을 생성한다.
- **NFR-R2-002 종료 보장**: graph traversal과 recipe follow는 cycle detection과 유한 budget을 적용한다.
- **NFR-R2-003 무추측 원칙**: graph evidence로 증명할 수 없는 target, operator와 업무 의미를 생성하지 않는다.
- **NFR-R2-004 저장소 독립성**: core recipe와 primitive에 특정 project/package/aggregate/field 이름을 포함하지 않는다.
- **NFR-R2-005 진단 가능성**: unsupported, unclassified, unresolved와 partial 상태를 구분한다.
- **NFR-R2-006 스키마 안전성**: unknown field, duplicate ID, invalid type과 forbidden expression을 load 전에 거부한다.
- **NFR-R2-007 성능 관찰**: 실행 시간, peak memory, graph size와 primitive별 evaluation count를 측정하되 PoC 합격선으로 사용하지 않는다.
- **NFR-R2-008 테스트 전략**: example, integration, snapshot, rename/metamorphic와 cross-repository holdout을 사용한다.
- **NFR-R2-009 보안 경계**: YAML은 code execution, filesystem/network access와 reflection을 유발할 수 없다.

## 7. PoC 성공 기준

### Go

- core semantic recipe 4개 family가 도메인 이름 없이 표현된다.
- 각 family가 2개 이상의 서로 다른 corpus/variant에서 재사용된다.
- `PRESERVE` baseline diff가 0건이다.
- `REPLACE` 대상의 hardcoded domain semantics가 제거된다.
- 모든 unsupported/unresolved/truncated 결과가 명시적으로 보고된다.
- arbitrary expression 또는 단일 repository 전용 primitive가 없다.

### Partial

- 일부 classifier family만 repository-independent recipe로 일반화되며 적용 가능 범위를 명확히 제한할 수 있다.

### No-Go

- repository마다 recipe 또는 primitive 수정이 필요하다.
- concrete field/literal/business operator를 YAML에 넣어야만 결과를 만들 수 있다.
- graph evidence 없이 의미를 추측하거나 arbitrary expression이 필요하다.

## 8. 확장 설정

| Extension | Enabled | Decided At |
| :--- | :--- | :--- |
| Security Baseline | No | Requirements Analysis v1, 유지 |
| Property-Based Testing | No | Requirements Analysis v1, 유지 |

## 9. Revision 1 대비 변경점

| 영역 | Revision 1 | Revision 2 |
| :--- | :--- | :--- |
| 외부화 단위 | 기존 룰 5종 | generic classifier family와 semantic recipe |
| concrete value | 명시적 금지 규칙 부족 | field/literal/target은 runtime binding으로만 생성 |
| traversal budget | Java runtime 책임 | Java 집행은 유지하되 engine YAML configuration으로 외부화 |
| Password | 대표 core 이관 대상 | 선택적 Spring Security pack |
| Delegated guard | 대표 이관 범위 밖 | hardcoding 제거 또는 기본 pack에서 삭제 |
| 회귀 기준 | 기존 결과 완전 동등성 | `PRESERVE/REPLACE/UNSUPPORTED` disposition별 판정 |
| corpus | Golden Corpus | cross-repository graph/rule coverage + rename holdout |

## 10. 추적 문서

- 이전 요구사항: `requirements.md`
- 사용자 답변 v1: `requirement-verification-questions.md`
- Revision 2 영속 사양: `specs/deep-interview-generic-semantic-recipe-scope.md`
- Revision 2 승인: `requirements-v2-approval.md`
