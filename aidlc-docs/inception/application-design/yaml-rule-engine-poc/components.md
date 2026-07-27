# YAML Rule Engine PoC — Components

> **범위**: Java + Spring MVC Controller에서 시작하는 application subgraph의 semantic recipe PoC
> **설계 수준**: 컴포넌트 책임과 경계. primitive 내부 알고리즘은 Unit별 Functional Design에서 확정한다.

## 1. 설계 원칙

1. `FactCodeGraph` 생성 알고리즘과 AST/type resolution은 Java에 남긴다.
2. YAML은 유한한 typed primitive의 **조합**만 선언한다.
3. field path, literal, enum value, operator와 evidence는 runtime binding으로 얻는다.
4. 정상 scan은 기존 Java 결과가 authoritative이며 production output을 변경하지 않는다.
5. 별도 PoC evaluation에서만 YAML이 primary이고 Java는 비교 및 명시적 fallback 역할을 한다.
6. load·schema·type 오류는 명시적 PoC evaluation 시작 전에 전체 pack을 거부하되 정상 scan에는 영향을 주지 않는다.
7. 지원하지 못한 의미는 추측하지 않고 상태와 evidence를 보고한다.

## 2. 보존 컴포넌트

| 컴포넌트 | 상태 | 책임 |
| :--- | :---: | :--- |
| `SpringStaticScanService` | 변경 없음 | Spring MVC Controller와 endpoint 발견 |
| `DefaultFactCodeGraphBuilder` | 알고리즘 보존 | endpoint별 reachable graph 생성 및 budget 집행 |
| `FactGraphIndex`, `SemanticContext` | 재사용 | node/edge/call/value-flow 조회 |
| `DefaultValidationCandidateDetector` | 재사용 | 평가할 `PredicateCandidate` 생성 |
| `DefaultGraphRuleEngine`, `SemanticRuleDispatcher` | 정상 경로 보존 | Java authoritative 결과 생성 |
| `RuleOutputService`와 exporter | 변경 없음 | 정상 scan의 기존 candidate/output 계약 유지 |

`DefaultFactCodeGraphBuilder`에는 이미 `FactGraphTraversalBudget` 주입점이 있으므로, 알고리즘을 외부화하지 않고 YAML 값을 evaluation graph용 Java value object로 변환한다. 정상 scan은 기존 default budget을 계속 사용한다.

## 3. 신규 Domain 컴포넌트 — `analysis.domain.recipe`

### 3.1 Recipe Source Model

- **목적**: 세 YAML 문서의 논리적 입력과 version을 표현한다.
- **주요 모델**:
  - `RecipeSourceSet`: engine config, semantic recipes, framework catalog 경로
  - `RecipePackVersion`: 세 문서가 공유하는 schema/pack version
  - `EngineRecipeConfiguration`: traversal 및 deterministic/reporting 정책

### 3.2 Semantic Recipe Model

- **목적**: repository-independent classifier family를 typed step sequence로 표현한다.
- **주요 모델**:
  - `SemanticRecipe`: id, family, enabled, ordered steps, required pack
  - `RecipeStep`: primitive ID와 명명된 input/output binding
  - `BindingType`: `NODE`, `NODE_SET`, `PREDICATE`, `TARGET_PATH`, `LITERAL`, `OPERATOR`, `EVIDENCE`, `STATUS`
- **금지 데이터**: repository/package/aggregate/field 이름, 업무 상태, concrete target, arbitrary expression

### 3.3 Primitive Contract

- **목적**: YAML에서 호출 가능한 Java primitive의 닫힌 집합을 정의한다.
- **주요 모델**:
  - `PrimitiveDescriptor`: stable ID, input/output type, 허용 fact/edge, termination 특성
  - `PrimitiveInvocation`: compile된 descriptor와 binding slots
  - `PrimitiveOutcome`: produced bindings, diagnostic, budget consumption
- **초기 category**: `match`, `bind`, `follow`, `resolve`, `normalize`, `quantify`, `emit`

### 3.4 Compiled Plan

- **목적**: 명시적 PoC evaluation run 동안 재사용할 불변 실행 snapshot을 제공한다.
- **주요 모델**:
  - `CompiledRecipePlan`: engine config, compiled recipes, compiled catalog, version, content digest
  - `CompiledRecipe`: type 검증을 마친 invocation sequence
  - `CompiledFrameworkCatalog`: signature/type/evidence 기반 library semantics
- 모든 collection은 immutable이며 recipe/step ordering은 compile 시 고정한다.

### 3.5 Runtime Binding and Result

- **목적**: graph에서 얻은 실제 값을 recipe step 사이에 안전하게 전달한다.
- **주요 모델**:
  - `RecipeEvaluationContext`: graph/index/scope/predicate/plan/budget counter
  - `BindingEnvironment`: typed slot map; 같은 이름의 type 변경 금지
  - `RecipeCandidateResult`: normalized candidate 또는 미분류 상태와 evidence
  - `RecipeExecutionStatus`: `RESOLVED`, `UNCLASSIFIED`, `UNRESOLVED`, `PARTIAL_ANALYSIS`, `FAILED`

### 3.6 Evaluation and Differential Model

- **목적**: YAML primary 결과와 Java 비교 결과를 production output과 분리해 기록한다.
- **주요 모델**:
  - `EvaluationRecord`: endpoint/predicate별 YAML raw, Java comparison, effective source
  - `EvaluationEffectiveSource`: `YAML`, `JAVA_FALLBACK`, `NONE`
  - `BaselineDisposition`: `PRESERVE`, `REPLACE`, `UNSUPPORTED`
  - `RecipeEvaluationReport`: coverage, diff, fallback, diagnostic, performance 집계

## 4. 신규 Support 컴포넌트 — `analysis.support.recipe`

### 4.1 `YamlRecipeSourceLoader`

- 세 UTF-8 YAML 파일을 읽어 source document로 변환한다.
- filesystem 접근은 이 adapter에만 허용하며 YAML 내용이 추가 I/O를 요청할 수는 없다.

### 4.2 `RecipeSchemaValidator`

- unknown field, duplicate ID, missing field, invalid enum/value, version mismatch를 검증한다.
- `custom_expression`, callback class, script, reflection 같은 금지 필드를 fail-closed로 거부한다.

### 4.3 `SemanticRecipeCompiler`

- primitive ID를 registry descriptor에 연결한다.
- step input/output type과 binding 선행 정의를 검증한다.
- recipe와 framework catalog를 결정적 순서의 `CompiledRecipePlan`으로 만든다.

### 4.4 `DefaultSemanticPrimitiveRegistry`

- 유한한 primitive 구현을 stable ID로 제공한다.
- primitive 구현은 `FactGraphIndex`와 `SemanticContext`만 조회하며 프로젝트별 이름을 참조하지 않는다.
- `follow` 계열은 cycle set, depth와 evaluation budget을 의무적으로 사용한다.

### 4.5 `SemanticRecipeExecutor`

- compiled step을 순서대로 실행하고 typed binding을 갱신한다.
- target/literal/operator/evidence가 증명되지 않으면 `UNRESOLVED`를 반환한다.
- graph/build diagnostic 또는 budget truncation을 `PARTIAL_ANALYSIS`로 전파한다.

### 4.6 `FrameworkPackSelector`

- resolved signature, receiver type, annotation 및 dependency/graph evidence로 Spring Data, Spring Security, JPA pack을 선택한다.
- method name 단독 일치는 pack 활성화 근거로 인정하지 않는다.

### 4.7 `RecipeDiffer`와 `EvaluationReportWriter`

- candidate identity와 normalized observation을 기준으로 YAML/Java를 비교한다.
- fallback 호출, `PRESERVE/REPLACE/UNSUPPORTED`, graph coverage와 semantic coverage를 분리해 기록한다.
- report는 별도 evaluation artifact이며 기존 `EndpointRuleOutput`에 합치지 않는다.

## 5. Application Orchestration 컴포넌트

### 5.1 `RecipePlanBootstrapService`

- 명시적 PoC evaluation 시작 전에 세 YAML을 load → validate → compile한다.
- 성공한 plan만 evaluation 준비 서비스에 전달하고 오류가 하나라도 있으면 evaluation graph 생성과 recipe 실행을 거부한다.
- engine traversal config를 evaluation graph용 `FactGraphTraversalBudget`으로 변환한다.
- 정상 `ScanPreparationService`와 `RuleOutputService`는 이 컴포넌트에 의존하지 않는다.

### 5.2 `RecipeEvaluationPreparationService`

- 기존 정상 graph를 재사용하지 않고 `RepositorySource`와 `StaticScanResult`를 입력받아 evaluation graph를 별도로 생성한다.
- `CompiledRecipePlan`의 traversal config를 `FactGraphTraversalBudget`으로 변환하여 `DefaultFactCodeGraphBuilder`에 전달한다.
- YAML engine과 Java comparison engine이 같은 evaluation graph/scope를 읽도록 repository-run 범위의 `RecipeEvaluationRunContext`를 구성한다.

### 5.3 `SemanticRecipeEvaluationService`

- `RecipeEvaluationRunContext`의 evaluation graph를 받아 endpoint별 YAML과 Java 경로를 평가한다.
- YAML 결과를 evaluation의 primary로 선택한다.
- YAML no-match, compile/runtime failure 때만 Java를 effective fallback으로 선택하되 fallback 사실을 숨기지 않는다.
- 두 raw 결과는 fallback 여부와 무관하게 항상 diff에 포함한다.

## 6. DelegatedGuard 경계

- 정상 scan의 기존 Java pack은 PoC 기간 동안 output 호환성을 위해 건드리지 않는다.
- PoC evaluation pack에서는 `DelegatedGuardRule`을 제외한다.
- 호출 인자 → parameter → return predicate → domain origin이 증명되지 않으면 `UNRESOLVED_DELEGATED_GUARD`를 기록한다.
- `currentUser`, `order.state`, `HAS_CANCELLATION_PERMISSION` 등 기존 추측값을 YAML이나 신규 primitive로 옮기지 않는다.
- 기존 결과는 baseline에서 `REPLACE`로 분류하여 corrected expectation과 비교한다.

## 7. 컴포넌트 완료 기준

- 세 YAML 책임이 domain model과 schema에서 분리된다.
- compile 결과가 immutable하고 재현 가능한 digest를 가진다.
- primitive registry 밖의 동작은 실행할 수 없다.
- 정상 output과 evaluation report의 저장·소비 경계가 분리된다.
- repository-specific 상수 없이 core family와 optional framework pack을 표현할 수 있다.
