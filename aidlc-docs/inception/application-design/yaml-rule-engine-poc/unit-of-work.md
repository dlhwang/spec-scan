# YAML Rule Engine PoC — Unit of Work

> **형태**: brownfield 단일 Java/Gradle application 내부의 논리적 개발·검증 Unit
> **진행 방식**: 단일 팀, 선행 Unit evidence 승인 후 다음 Unit 착수
> **기준 설계**: `application-design.md`

## 1. 공통 경계

- U01~U06은 독립 deployable이나 Gradle submodule이 아니다.
- production 정상 scan은 YAML/bootstrap/evaluation graph에 의존하지 않는다.
- PoC evaluation은 YAML-derived budget으로 별도 graph를 만들고 YAML/Java 비교 경로가 이를 공유한다.
- `FactCodeGraph` 생성·탐색 알고리즘과 primitive 구현은 Java에 남는다.
- YAML에는 repository-specific field, literal, aggregate, 업무 상태와 임의 expression을 넣지 않는다.
- Unit 완료는 코드 작성이 아니라 해당 Unit의 자동화 evidence와 문서가 모두 충족될 때만 인정한다.

## 2. Unit 요약

| Unit | Capability | Primary output | Hard prerequisite |
| :--- | :--- | :--- | :--- |
| U01 | Generalization Baseline | corpus/baseline/coverage 계약 | 없음 |
| U02 | YAML Configuration & Schema | strict schema와 immutable compiled plan | U01 승인 |
| U03 | Typed Primitive Runtime | registry/binding/executor/evaluation graph runtime | U02 승인 |
| U04 | Core Semantic Recipes | 4개 generic classifier family | U03 승인 |
| U05 | Framework Packs & Delegated Cleanup | optional packs와 no-guess correction | U03, U04 승인 |
| U06 | Cross-Repository Evaluation Gate | diff/fallback/coverage/Go 판정 report | U01~U05 승인 |

## 3. U01 — Generalization Baseline

### 목적

YAML engine 구현 전에 현재 graph/rule 결과, 알려진 오탐과 cross-repository 일반성의 측정 기준을 고정한다.

### 소유 책임

- 5~10개 Spring MVC corpus manifest와 repository selection rationale
- repository별 약 6~10개 endpoint 또는 동등 복잡도 기록
- candidate baseline과 `PRESERVE`, `REPLACE`, `UNSUPPORTED` disposition
- package/aggregate/field/method rename 및 mutation holdout
- graph coverage와 semantic coverage의 분리 정의
- 정상 scan과 evaluation artifact의 identity/canonical comparison key 계약

### 제외

- YAML parser/schema 구현
- primitive 또는 recipe 구현
- 기존 결과를 무조건 정답으로 승인하는 작업

### 입력

- 승인된 Requirements v2와 Application Design
- 기존 Java engine 결과, FactCodeGraph와 관련 regression fixture
- `DelegatedGuardRule` 등 알려진 교정 대상

### 출력

- `CorpusManifest`
- `BaselineManifest`
- disposition별 expected snapshot
- rename/mutation holdout set
- coverage 및 candidate identity specification

### 주 요구사항

- R2-YAML-009, R2-YAML-010
- 보조: R2-YAML-001, R2-YAML-005~008
- NFR-R2-004, NFR-R2-008

### Definition of Done

- [ ] 5~10개 corpus와 endpoint scope가 manifest에 고정됨
- [ ] 모든 baseline entry에 disposition과 근거가 있음
- [ ] 기존 hardcoded delegated 결과가 `REPLACE`로 식별됨
- [ ] rename/mutation holdout은 implementation tuning 대상과 분리됨
- [ ] graph coverage와 semantic coverage 산식이 서로 독립적으로 정의됨
- [ ] baseline snapshot runner가 결정적 ordering으로 diff를 생성함

### 필수 Evidence

- corpus manifest review
- disposition completeness test
- snapshot determinism test
- rename/mutation holdout isolation check

### Exit Gate

U01 evidence가 승인되기 전 U02 schema 또는 runtime 구현에 착수하지 않는다.

## 4. U02 — YAML Configuration & Schema

### 목적

세 YAML의 책임과 strict validation을 구현하고 typed immutable plan을 안전하게 compile할 기반을 만든다.

### 소유 책임

- `engine-config.yaml`, `semantic-recipes.yaml`, `framework-catalog.yaml` schema
- UTF-8 source loading과 파일별 syntax/location diagnostic
- unknown field, duplicate ID, missing field, invalid enum/value 검증
- schema/pack version 및 cross-document reference 검증
- forbidden expression/callback/script/reflection field 거부
- primitive descriptor 기반 binding type compile validation
- immutable `CompiledRecipePlan`, deterministic ordering과 content digest
- 정상 scan이 bootstrap에 의존하지 않는 integration boundary

### 제외

- primitive의 graph 탐색 알고리즘
- core recipe의 실제 semantic 결과
- hot-reload, version cache와 remote registry

### 입력

- U01 baseline/candidate identity 계약
- Application Design의 `analysis.domain.recipe`, `analysis.support.recipe` 경계
- primitive descriptor의 최소 compile-time contract

### 출력

- recipe source/domain model
- loader, validator와 compiler contract
- strict schema test fixture
- immutable compiled plan
- evaluation-only `RecipePlanBootstrapService`

### Package Touchpoints

- 신규 `io.atworks.specscan.analysis.domain.recipe`
- 신규 `io.atworks.specscan.analysis.support.recipe`
- 기존 `analysis.support.rule.yaml`은 migration reference/adapter로만 사용

### 주 요구사항

- R2-YAML-002, R2-YAML-003
- 보조: R2-YAML-006
- NFR-R2-001, NFR-R2-006, NFR-R2-009

### Definition of Done

- [ ] 세 schema가 책임별로 분리되고 version 계약을 가짐
- [ ] positive integer budget 기본값 `15/500/10000`과 override가 검증됨
- [ ] unknown/duplicate/missing/invalid/forbidden input이 모두 load 전에 거부됨
- [ ] 존재하지 않는 primitive, type mismatch와 선행 binding 누락이 compile에서 거부됨
- [ ] 같은 세 문서는 동일 ordering/digest의 immutable plan을 생성함
- [ ] invalid YAML이 정상 scan을 실패시키지 않는 isolation test가 통과함

### 필수 Evidence

- schema validation parameterized tests
- compile type/reference negative tests
- immutable/digest determinism test
- normal-scan bootstrap isolation integration test

### Exit Gate

U02의 strict validation과 정상 scan isolation evidence가 승인되기 전 U03 executor는 plan을 소비하지 않는다.

## 5. U03 — Typed Primitive Runtime

### 목적

유한한 typed primitive를 graph에 안전하고 결정적으로 실행하며 runtime binding과 불완전성 상태를 생성한다.

### 소유 책임

- `SemanticPrimitiveRegistry`, descriptor와 invocation contract
- `BindingEnvironment` type safety와 invocation-local lifetime
- `match`, `bind`, `follow`, `resolve`, `normalize`, `quantify`, `emit` runtime skeleton
- graph/index adapter와 candidate detection adapter
- cycle detection, depth/visit/edge 및 primitive evaluation counters
- `RESOLVED`, `UNCLASSIFIED`, `UNRESOLVED`, `PARTIAL_ANALYSIS`, `FAILED` 상태
- YAML-derived budget으로 별도 evaluation graph를 생성하는 preparation service
- 동일 evaluation graph/scope를 YAML과 Java comparison에 전달하는 run context

### 제외

- classifier family별 recipe YAML
- framework-specific semantics
- final differential report와 Go/No-Go 판정

### 입력

- U02 `CompiledRecipePlan`
- 기존 `FactCodeGraph`, `FactGraphIndex`, `SemanticContext`, `PredicateCandidate`
- U01 candidate identity/coverage contract

### 출력

- primitive registry와 runtime executor
- typed binding/result/diagnostic model
- `RecipeEvaluationPreparationService`
- `RecipeEvaluationRunContext`
- primitive-level metrics

### Package Touchpoints

- `analysis.domain.recipe`
- `analysis.support.recipe`
- read-only adapter to `analysis.domain.fact`, `analysis.domain.semantic`, `analysis.domain.candidate`
- reuse of `DefaultFactCodeGraphBuilder` and `DefaultValidationCandidateDetector`

### 주 요구사항

- R2-YAML-001~004, R2-YAML-008
- NFR-R2-001~003, NFR-R2-005, NFR-R2-007, NFR-R2-009

### Definition of Done

- [ ] registry 밖의 primitive 실행 경로가 없음
- [ ] binding slot은 compile/runtime 모두 type safe함
- [ ] 모든 follow가 cycle guard와 finite budget을 사용함
- [ ] graph truncation과 primitive limit가 `PARTIAL_ANALYSIS`로 전파됨
- [ ] 증명되지 않은 target/operator/value는 `UNRESOLVED`이며 합성되지 않음
- [ ] 정상 graph와 YAML-budget evaluation graph가 별도 생성됨
- [ ] 동일 graph/plan 입력에서 ordering과 결과가 결정적임

### 필수 Evidence

- primitive contract tests
- type mismatch/unknown primitive negative tests
- cycle and each-budget truncation tests
- runtime binding rename/metamorphic test
- normal/evaluation graph separation integration test

### Exit Gate

U03 종료·무추측·격리 evidence가 승인되기 전 U04/U05 recipe를 runtime에 등록하지 않는다.

## 6. U04 — Core Semantic Recipes

### 목적

Java/Spring MVC repository 전반에서 반복되는 4개 classifier family를 repository-independent recipe로 증명한다.

### 소유 책임

- binary failure guard recipe
- composite validation recipe (`&&`, `||`, enum/복수 requirement)
- standard null/empty guard recipe
- JDK Optional lookup failure recipe
- runtime-bound target/literal/operator/evidence emit
- family별 최소 2개 cross-domain fixture와 rename variant

### 제외

- Spring Data/Security/JPA optional packs
- DelegatedGuard correction
- repository별 field/literal 또는 업무 operator

### 입력

- U03 primitive registry/runtime
- U01 family baseline과 fixtures
- U02 semantic recipe schema

### 출력

- core semantic recipe YAML
- 필요한 경우 반복 패턴에 근거한 범용 primitive 확장 제안
- family별 regression/metamorphic evidence

### 주 요구사항

- R2-YAML-004, R2-YAML-005
- NFR-R2-003, NFR-R2-004, NFR-R2-008

### Definition of Done

- [ ] 4개 family가 concrete project/domain 이름 없이 표현됨
- [ ] target/value/operator는 모두 graph binding에서 생성됨
- [ ] family별 서로 다른 2개 이상의 corpus/variant에서 같은 recipe가 동작함
- [ ] rename/mutation 후 recipe 수정 없이 결과 의미가 보존됨
- [ ] custom expression이나 repository-specific primitive가 없음
- [ ] 표현 불가 케이스는 escape hatch 없이 backlog/diagnostic으로 남음

### 필수 Evidence

- family-level integration tests
- cross-domain fixture tests
- rename/metamorphic tests
- core YAML forbidden-constant review

### Exit Gate

4개 core family evidence가 승인되기 전 U05 framework semantics 또는 U06 최종 coverage를 판단하지 않는다.

## 7. U05 — Framework Packs & Delegated Cleanup

### 목적

framework semantics를 evidence 기반 optional pack으로 격리하고 legacy DelegatedGuard의 domain 추측을 제거한다.

### 소유 책임

- Spring Data repository/Optional lookup pack
- Spring Security `PasswordEncoder` type/signature pack
- JPA `@Version` optimistic-lock pack
- dependency/type/signature/annotation을 사용하는 `FrameworkPackSelector`
- method-name-only negative cases
- evaluation registry에서 legacy `DelegatedGuardRule` 제거
- generic evidence chain이 없을 때 `UNRESOLVED_DELEGATED_GUARD`

### 제외

- 정상 production Java pack의 Phase 2 제거
- `currentUser`, `order.state`, permission 이름 등 업무 의미 합성
- graph evidence가 없는 framework 활성화

### 입력

- U03 runtime/evaluation graph
- U04 core recipe conventions
- U01 `REPLACE/UNSUPPORTED` baseline
- U02 framework catalog schema

### 출력

- optional framework catalog/recipes
- pack activation evidence와 diagnostics
- DelegatedGuard corrected expectation
- positive/negative framework fixture

### 주 요구사항

- R2-YAML-006, R2-YAML-007
- 보조: R2-YAML-008, R2-YAML-010
- NFR-R2-003~005, NFR-R2-008

### Definition of Done

- [ ] framework pack은 required dependency와 graph/type evidence가 있을 때만 활성화됨
- [ ] 같은 method name의 unrelated call이 candidate를 만들지 않음
- [ ] type resolution 실패를 name match로 보완하지 않음
- [ ] evaluation default pack에서 legacy DelegatedGuard가 제외됨
- [ ] generic evidence chain이 없으면 `UNRESOLVED_DELEGATED_GUARD`가 생성됨
- [ ] hardcoded domain target/operator가 신규 YAML/primitive에 존재하지 않음

### 필수 Evidence

- framework presence/absence integration tests
- unrelated method/type-resolution negative tests
- delegated no-guess regression test
- `REPLACE` corrected expectation diff

### Exit Gate

optional pack isolation과 delegated correction evidence가 승인되기 전 U06 Go/Partial/No-Go를 판정하지 않는다.

## 8. U06 — Cross-Repository Evaluation Gate

### 목적

모든 Unit의 결과를 cross-repository corpus에서 측정하고 YAML externalization의 Go, Partial 또는 No-Go를 근거로 판정한다.

### 소유 책임

- evaluation-only bootstrap과 run orchestration
- YAML primary, Java always-compare, explicit fallback selection
- raw YAML 결손을 보존하는 differential evaluator
- disposition-aware `PRESERVE/REPLACE/UNSUPPORTED` verdict
- graph/semantic coverage, fallback, diagnostic와 performance report
- repository/endpoint/predicate/recipe deterministic serialization
- 성공 기준에 따른 최종 decision report

### 제외

- 정상 `EndpointRuleOutput`와 exporter schema 변경
- fallback으로 YAML regression을 성공 처리
- performance 수치만으로 PoC 합격/불합격 판정
- Phase 3 tracer, Phase 4 hot-reload

### 입력

- U01 corpus/baseline/holdout
- U02 compiled plan
- U03 runtime/evaluation graph
- U04 core recipes
- U05 framework packs/delegated correction

### 출력

- `RecipeEvaluationReport`
- repository별 graph/semantic coverage
- fallback 및 diagnostic distribution
- disposition별 diff
- Go/Partial/No-Go decision record

### 주 요구사항

- R2-YAML-001, R2-YAML-008~010
- 모든 NFR-R2-001~009의 최종 통합 검증

### Definition of Done

- [ ] 5~10개 corpus와 rename holdout이 동일 plan으로 실행됨
- [ ] YAML/Java가 같은 evaluation graph/scope에서 비교됨
- [ ] YAML bootstrap/graph failure가 정상 scan에 영향을 주지 않음
- [ ] 모든 fallback에 reason과 원래 YAML status가 보존됨
- [ ] `PRESERVE` diff 0건, `REPLACE` corrected expectation 일치, `UNSUPPORTED` diagnostic 존재
- [ ] core family별 2개 이상 corpus/variant 재사용 evidence가 있음
- [ ] arbitrary expression과 repository-specific primitive가 0건임
- [ ] Go/Partial/No-Go 판정과 근거가 report에 명시됨

### 필수 Evidence

- cross-repository evaluation run
- normal-scan isolation regression
- disposition snapshot report
- holdout generalization report
- deterministic rerun comparison
- performance/peak-memory observation report

### Exit Gate

U06 report 승인이 Phase 2 종료 및 Phase 3/4 투자 여부의 유일한 decision gate다.

## 9. Unit 간 공통 변경 통제

- primitive 표현력이 부족할 때 `custom_expression`을 만들지 않는다.
- 표현 불가 케이스는 새 범용 primitive, 신규 recipe kind 또는 Java 유지 대상으로 분류한다.
- 선행 Unit의 public contract가 변경되면 영향받는 후속 Unit evidence를 무효화하고 재검증한다.
- 문서/fixture 준비는 계약 승인 후 병렬화할 수 있지만 implementation hard gate는 우회하지 않는다.
- 각 Unit의 Functional/NFR Design 승인 전 해당 Unit 구현에 착수하지 않는다.
