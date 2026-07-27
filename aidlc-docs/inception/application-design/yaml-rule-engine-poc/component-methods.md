# YAML Rule Engine PoC — Component Methods

> 아래 signature는 Application Design 수준의 Java 계약 초안이다. 세부 validation, traversal 및 normalization 알고리즘은 Unit별 Functional Design에서 확정한다.

## 1. Domain Contracts — `analysis.domain.recipe`

```java
public record RecipeSourceSet(
    Path engineConfig,
    Path semanticRecipes,
    Path frameworkCatalog
) {}

public record EngineRecipeConfiguration(
    int maxDepth,
    int maxVisitedMethodsPerApi,
    int maxEdgesPerApi,
    boolean deterministicOrder,
    boolean reportUnclassified,
    boolean reportTruncation
) {}

public record CompiledRecipePlan(
    RecipePackVersion version,
    EngineRecipeConfiguration engine,
    List<CompiledRecipe> recipes,
    CompiledFrameworkCatalog frameworkCatalog,
    String contentDigest
) {}
```

불변 조건:

- traversal 값은 모두 양수이며 기본값은 `15`, `500`, `10000`이다.
- `CompiledRecipePlan`의 collection과 nested model은 immutable이다.
- digest는 정규화된 세 source document와 schema version으로 계산한다.

```java
public interface SemanticPrimitive {
    PrimitiveDescriptor descriptor();
    PrimitiveOutcome execute(
        RecipeEvaluationContext context,
        BindingEnvironment bindings,
        PrimitiveInvocation invocation
    );
}

public interface SemanticPrimitiveRegistry {
    Optional<PrimitiveDescriptor> descriptor(String primitiveId);
    SemanticPrimitive require(String primitiveId);
    List<PrimitiveDescriptor> descriptors();
}
```

계약:

- `descriptor()`는 stable ID와 정확한 input/output `BindingType`을 제공한다.
- `execute()`는 graph와 evaluation context만 읽고 외부 I/O나 reflection을 수행하지 않는다.
- `follow` primitive는 context의 budget/cycle guard를 우회할 수 없다.

```java
public record RecipeExecutionResult(
    String recipeId,
    String predicateCandidateId,
    RecipeExecutionStatus status,
    List<BusinessRuleCandidate> candidates,
    List<RecipeDiagnostic> diagnostics,
    RecipeExecutionMetrics metrics
) {}

public record EvaluationRecord(
    String repositoryId,
    String operationKey,
    String predicateCandidateId,
    RecipeExecutionResult yamlResult,
    GraphRuleEngineResult javaResult,
    EvaluationEffectiveSource effectiveSource,
    List<SemanticDifference> differences,
    BaselineDisposition disposition
) {}
```

## 2. Load, Validation and Compile — `analysis.support.recipe`

### `YamlRecipeSourceLoader`

```java
public interface RecipeSourceLoader {
    LoadedRecipeSources load(RecipeSourceSet sources);
}
```

- 세 파일을 UTF-8로 읽는다.
- syntax/read 오류를 파일·line/column이 포함된 diagnostic으로 반환한다.
- 부분 성공 plan은 만들지 않는다.

### `RecipeSchemaValidator`

```java
public interface RecipeSchemaValidator {
    RecipeValidationResult validate(LoadedRecipeSources sources);
}
```

- engine, recipes, catalog schema를 각각 검증한 뒤 cross-document ID/version/reference를 검증한다.
- unknown field, duplicate recipe/catalog ID, invalid primitive category, forbidden expression을 오류로 처리한다.

### `SemanticRecipeCompiler`

```java
public interface RecipeCompiler {
    RecipeCompilationResult compile(
        LoadedRecipeSources sources,
        SemanticPrimitiveRegistry registry
    );
}
```

- validation 오류가 있으면 `CompiledRecipePlan`을 반환하지 않는다.
- step의 input binding이 앞 단계나 evaluation context에 존재하는지 검사한다.
- registry의 descriptor와 type이 맞지 않으면 compile 오류다.
- recipe ID와 step ordering은 정규화하여 결정적으로 고정한다.

## 3. Runtime Execution

### `SemanticRecipeExecutor`

```java
public interface RecipeEngine {
    RecipeEngineResult evaluate(
        CompiledRecipePlan plan,
        FactCodeGraph graph,
        MethodScope scope
    );
}
```

상위 실행 계약:

1. `DefaultValidationCandidateDetector` adapter로 predicate를 얻고 candidate ID 순으로 정렬한다.
2. 각 predicate에 적용 가능한 recipe를 ID 순으로 실행한다.
3. binding type 위반은 해당 execution의 `FAILED`이며 report에 남긴다.
4. graph/build truncation 또는 follow budget 초과는 `PARTIAL_ANALYSIS`다.
5. 어떤 recipe도 분류하지 못하면 `UNCLASSIFIED`다.
6. recipe가 의미를 인식했으나 target/origin을 증명하지 못하면 `UNRESOLVED`다.

### `FrameworkPackSelector`

```java
public interface FrameworkPackSelector {
    ActiveFrameworkPacks select(
        CompiledFrameworkCatalog catalog,
        FactCodeGraph graph,
        RepositoryEvidence repositoryEvidence
    );
}
```

- Spring Data: repository receiver/type과 Optional terminal evidence 필요
- Spring Security: `PasswordEncoder` resolved type/signature 필요
- JPA: `@Version` annotation 또는 동등한 resolved metadata 필요
- 같은 method name만 존재하는 경우 선택하지 않는다.

### `TraversalBudgetAdapter`

```java
public interface TraversalBudgetAdapter {
    FactGraphTraversalBudget toFactGraphBudget(EngineRecipeConfiguration configuration);
}
```

- 신규 graph 알고리즘을 만들지 않고 기존 `DefaultFactCodeGraphBuilder.build(..., budget)`에 전달한다.
- recipe follow용 counter는 같은 configuration에서 별도 endpoint-local execution state로 만든다.

## 4. Application Services

### `RecipePlanBootstrapService`

```java
public final class RecipePlanBootstrapService {
    public RecipeBootstrapResult bootstrap(RecipeSourceSet sources);
}
```

- 호출 순서: `load → validate → compile → immutable snapshot publish`
- compile 실패 시 evaluation graph 생성과 recipe evaluation을 시작하지 않는다.
- 정상 scan entry point에서는 이 서비스를 호출하지 않는다.
- hot-reload와 version cache는 PoC 범위 밖이다.

### `RecipeEvaluationPreparationService`

```java
public final class RecipeEvaluationPreparationService {
    public RecipeEvaluationRunContext prepare(
        RepositorySource repository,
        StaticScanResult scanResult,
        CompiledRecipePlan plan
    );
}
```

- `plan.engine()`을 `FactGraphTraversalBudget`으로 변환한다.
- 기존 정상 scan의 `FactGraphBuildResult`를 재사용하지 않고 evaluation graph를 별도로 생성한다.
- YAML executor와 Java comparison engine에 동일한 evaluation graph와 method scope를 제공한다.

### `SemanticRecipeEvaluationService`

```java
public final class SemanticRecipeEvaluationService {
    public RecipeEvaluationReport evaluate(
        RecipeEvaluationRunContext context,
        BaselineManifest baseline
    );
}
```

- 정상 `RuleOutputService.generate(...)`와 독립적으로 호출한다.
- bootstrap 또는 evaluation graph 생성 실패는 evaluation 실패로만 반환하며 정상 scan 상태를 변경하지 않는다.
- YAML raw 결과와 Java comparison 결과를 둘 다 생성한다.
- evaluation effective result 선택 규칙:
  - YAML `RESOLVED`, `UNRESOLVED`, `PARTIAL_ANALYSIS` 결과가 있으면 `YAML`
  - YAML `UNCLASSIFIED`, `FAILED` 또는 plan 실행 불가이면 Java 결과가 있을 때 `JAVA_FALLBACK`
  - 양쪽 모두 결과가 없으면 `NONE`
- fallback을 적용해도 YAML raw status와 reason은 report에서 제거하지 않는다.

### `RecipeDiffer`

```java
public interface RecipeDiffer {
    List<SemanticDifference> compare(
        RecipeEngineResult yaml,
        GraphRuleEngineResult javaResult,
        BaselineManifest baseline
    );
}
```

비교 key는 repository, operation key, predicate candidate ID, normalized category/effect/constraint/evidence fingerprint의 조합이다. 내부 실행 시간과 trace ordering은 외부 동등성에서 제외하되 별도 metric으로 보존한다.

### `EvaluationReportWriter`

```java
public interface EvaluationReportWriter {
    Path write(RecipeEvaluationReport report, Path outputDirectory);
}
```

- 정상 OpenAPI/structured/execution output 경로와 다른 evaluation 디렉터리에 쓴다.
- 기존 `EndpointRuleOutput` 또는 exporter schema를 변경하지 않는다.

## 5. 기존 컴포넌트와의 접점

| 기존 method | PoC 사용 방식 |
| :--- | :--- |
| `DefaultFactCodeGraphBuilder.build(scan, source, budget)` | 정상 scan은 default budget, evaluation graph는 YAML-derived budget 전달 |
| `DefaultGraphRuleEngine.evaluate(graph, scope)` | Java comparison 결과 생성; 정상 scan에서는 계속 authoritative |
| `RuleOutputService.generate(context)` | 변경 없이 production-compatible output 생성 |
| `ScanPreparationService.prepare(source)` | 정상 경로 보존; PoC용 overload/facade 여부는 Unit 설계에서 결정 |

## 6. 예외 및 Diagnostic 계약

| 단계 | 오류 | 처리 |
| :--- | :--- | :--- |
| Load | read/syntax 오류 | bootstrap 전체 실패, evaluation 시작 금지; 정상 scan 영향 없음 |
| Validate | unknown/duplicate/forbidden/type 오류 | bootstrap 전체 실패, evaluation 시작 금지; 정상 scan 영향 없음 |
| Compile | primitive/reference/binding 오류 | bootstrap 전체 실패, evaluation 시작 금지; 정상 scan 영향 없음 |
| Graph build | root/type/budget 문제 | endpoint별 `PARTIAL_ANALYSIS` 또는 unsupported diagnostic |
| Execute | primitive runtime failure | 해당 predicate `FAILED`, Java fallback 및 원인 기록 |
| Resolve | target/origin 증명 실패 | `UNRESOLVED`; 값을 발명하지 않음 |
| Match | 지원 recipe 없음 | `UNCLASSIFIED`; Java fallback 여부 기록 |
