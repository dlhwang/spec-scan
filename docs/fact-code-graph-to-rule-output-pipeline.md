# FactCodeGraph 생성 및 최종 산출물(EndpointRuleOutput) 도출 전체 파이프라인 명세서

## 1. 개요 (Overview)

본 문서는 `auto-oas` 시스템이 Git 레포지토리 소스코드를 입력받아 **FactCodeGraph(코드 그래프)**를 만들고, 이를 추상화 및 규칙(Rule) 평가 과정을 거쳐 최종 API별 산출물인 `EndpointRuleOutput`으로 변환하는 **전체 내부 로직 및 필터링/분류 규칙을 총망라하여 정리**한 문서입니다.

### 최종 산출물 구조 (`EndpointRuleOutput`)
API 엔드포인트(예: `POST /api/estate/properties`)마다 도출되는 4가지 핵심 산출물은 다음과 같습니다:

1. **`requestPreconditions` (`List<ExecutableCondition>`)**: 클라이언트가 요청 시 지켜야 하는 입력 사전 조건 (예: `@NotNull`, `deposit > 0`, `contractType != null`)
2. **`responseAssertions` (`List<ExecutableCondition>`)**: 서버가 응답 시 보장하는 조건
3. **`excludedBusinessRules` (`List<ExcludedBusinessRule>`)**: 도메인 내부 비즈니스 제약이나 위임된 가드(`JAVA_DELEGATED_GUARD`) 등 외부 API 직접 스펙에서 제외된 제약조건
4. **`diagnostics` (`List<CandidateOutputDiagnostic>`)**: 분석 중 구조 미지원(`UNSUPPORTED`)이나 의미 미해결(`UNRESOLVED`)로 빠진 진단 정보

---

## 2. 전체 파이프라인 흐름도 (Overall Architecture)

```text
[1. Source Ingestion] 
  └─ Git 소스코드 임시 워크스페이스 로드
       ↓
[2. Static Endpoint Scan] (SpringStaticScanService)
  └─ Controller 클래스 및 @PostMapping 등 API 엔드포인트(ApiEndpoint) 수집
       ↓
[3. FactCodeGraph Build] (DefaultFactCodeGraphBuilder)
  ├─ Traversal Policy로 필터링하며 Controller -> Service -> Domain 탐색
  ├─ FactMethodVisitor / FactExpressionVisitor로 AST Node & Edge 생성
  └─ DtoSchemaGraphBuilder로 DTO 타입 확장
       ↓
[4. Annotation Validation Extract] (ValidationExtractionService)
  └─ DTO / 파라미터 어노테이션(@NotNull, @Min) 조건 추출
       ↓
[5. Candidate Detection & Semantic Rule Engine] (RuleOutputService + DefaultGraphRuleEngine)
  ├─ MethodScope (도달 가능 메서드 모음) 및 Delegated Boolean 전파
  ├─ DefaultValidationCandidateDetector -> 예외/리턴 연관 조건(PredicateCandidate) 수집
  ├─ SemanticContext (그래프 인덱스 FactGraphIndex 1회 구축)
  └─ SemanticRuleDispatcher (Semantic Classifier 7종) -> BusinessRuleCandidate 정규화
       ↓
[6. Output Adaptation] (CandidateToOutputAdapter)
  ├─ RuleEffect / SemanticStatus에 따라 4대 산출물로 분기 및 중복 제거
  └─ EndpointRuleOutput (requestPreconditions, responseAssertions, excluded, diagnostics) 최종 완성
       ↓
[7. Export] (ExecutionSpecExporter / OpenApiAssemblyService)
  └─ JSON / OpenAPI YAML 실행 스펙 출력
```

---

## 3. 단계별 세부 로직 및 필터링/분류 규칙

### [1단계] Static Scan & API 엔드포인트 수집 (`SpringStaticScanService`)
- JavaParser를 이용해 소스 루트 하위의 Java 파일을 파싱합니다.
- `@RestController` 또는 `@Controller` 어노테이션이 붙은 클래스를 탐색합니다.
- 메서드의 `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` 어노테이션 및 소스 라인 위치를 파악하여 `ApiEndpoint` 객체를 생성합니다.

---

### [2단계] 코드 그래프 생성 필터링 로직 (`DefaultFactCodeGraphBuilder`)

> **질문: 모든 코드가 그래프로 들어가는가?**  
> **답변: 아니다.** 성능과 메모리 한계를 위해 `DefaultMethodTraversalPolicy`와 `FactGraphTraversalBudget` 정책에 따라 필터링되어 들어갑니다.

#### ① 메서드 탐색 필터링 규칙 (`DefaultMethodTraversalPolicy`)
각 메서드 호출(`MethodCallExpr`)을 만날 때마다 다음 3가지 중 하나로 판정합니다:

1. **`SKIP_GENERATED` (진입 및 기록 제외)**:
   - 클래스/메서드 이름에 `generated`, `$$`, `proxy`가 포함된 경우 (Lombok, ByteBuddy, Spring CGLIB 프록시 등)
2. **`RECORD_CALL_ONLY` (호출 노드만 생성하고 메서드 내부 바디 탐색 차단)**:
   - 소스 코드가 없는 경우
   - JDK 기본 클래스 (`java.*`, `javax.*`, `jakarta.*`)
   - Spring 프레임워크 클래스 (`org.springframework.*`)
   - DB Repository 클래스 (이름에 `repository` 포함)
   - 컨트롤러의 `applicationBasePackage` 패키지 경로 밖에 위치한 외부 라이브러리/클래스
3. **`VISIT_BODY` (메서드 내부 바디까지 재귀 탐색)**:
   - 애플리케이션 내부 패키지에 속하고 소스 코드가 존재하는 비즈니스 메서드만 바디 내부로 진입합니다.

#### ② 그래프 탐색 예산 제한 (`FactGraphTraversalBudget`)
- **`maxDepth` (기본값: 8)**: 컨트롤러부터 호출 깊이가 8을 초과하면 더 이상 하위 메서드로 진입하지 않습니다.
- **`maxVisitedMethodsPerApi`**: API당 방문할 수 있는 최대 메서드 개수를 제한합니다.
- **`maxEdges`**: 그래프당 생성할 수 있는 최대 Edge 수를 제한하여 OOM을 방지합니다.

---

### [3단계] AST 노드(Node) 및 관계(Edge) 구성 (`FactMethodVisitor`, `FactExpressionVisitor`)

`VISIT_BODY`로 결정된 메서드는 AST를 순회하며 노드와 에지를 생성합니다.

#### 주요 FactNode 종류 (`FactNodeType`)
- `API_METHOD` / `METHOD` / `CONSTRUCTOR`: 메서드 및 생성자
- `PARAMETER` / `LOCAL_VARIABLE`: 파라미터 및 지역 변수
- `CONDITION`: `if (...)`, `switch (...)` 등의 조건식
- `METHOD_CALL`: 메서드 호출식
- `THROW` / `RETURN`: 예외 발생 및 리턴문
- `FIELD_ACCESS` / `ENUM_CONSTANT` / `LITERAL` / `NULL_LITERAL`: 필드 접근, 이넘, 리터럴 값

#### 주요 FactEdge 종류 (`FactEdgeType`)
- `ORIGINATES_FROM`: 메서드 - 파라미터/변수 간 소유 관계
- `CALLS`: 메서드 - 메서드 호출 관계
- `CONTROLS` (`THEN_OUTCOME`, `ELSE_OUTCOME`): 조건문(`CONDITION`)이 제어하는 결과(`THROW` / `RETURN`) 관계
- `RETURNS`: 메서드 - 반환값 관계
- `OPERAND_OF`: 조건식 내부의 연산자-피연산자 관계

---

### [4단계] 검증 후보 수집 및 불리언 위임 전파 (`DefaultValidationCandidateDetector` & `DelegatedBooleanPredicatePromoter`)

그래프 생성이 완료되면, API 수용 범위(`MethodScope`) 내의 노드 중 **값 검증 후보(`PredicateCandidate`)를 탐색**합니다.

1. **조건문 기반 후보 수집**:
   - `FactNodeType.CONDITION` 노드 중 `THEN_OUTCOME` 또는 `ELSE_OUTCOME` Edge가 `THROW` 노드로 연결되는 경우 `PredicateCandidate`로 추출합니다.
2. **도간된 불리언 검증 전파 (`DelegatedBooleanPredicatePromoter`)**:
   - `if (isNotValid(...)) throw ...` 처럼 불리언 헬퍼 메서드로 검증이 위임된 경우, 헬퍼 메서드 내부의 `return true` 또는 불리언 리턴문으로 이어지는 내부 조건들(`contractType == null`, `deposit <= 0` 등)을 상위 가드의 실패 결과로 연쇄 전파하여 `PredicateCandidate`로 승격시킵니다.
3. **특수 가드 Sink 수집**:
   - `Objects.requireNonNull`, `Assert.notNull`, `Preconditions.checkNotNull` 등의 가드 메서드 호출을 `PredicateCandidate`로 수집합니다.
4. **Implicit Optional / Password Sink**:
   - `Optional.orElseThrow()`, `SpringData.findById()`, `passwordEncoder.matches()` 연쇄를 후보로 수집합니다.

---

### [5단계] 의미적 룰 엔진 및 분류 (`DefaultGraphRuleEngine` + `SemanticRuleDispatcher`)

최신 엔진은 원시 그래프를 매번 순회하는 대신, **`SemanticContext`를 생성하여 단 1회의 `FactGraphIndex` 생성 후 `SemanticRuleDispatcher`로 신속하게 정규화**합니다.

#### 7대 Semantic Classifier
1. **`BinaryConstraintClassifier`**: Binary 연산식(`== null`, `!= null`, `<= 0`, `> 100` 등)을 `NOT_NULL`, `MINIMUM`, `MAXIMUM`으로 정규화
2. **`StandardGuardMethodClassifier`**: `Objects.requireNonNull`, `Assert.notNull`, `ObjectUtils.isEmpty` 등 레지스트리 기반 가드 일괄 처리
3. **`CompositeValidationClassifier`**: 헬퍼 메서드 내부의 복합 불리언 검증 조건(`contractType == null`, `deposit <= 0`)을 개별 제약조건으로 추출
4. **`OptimisticLockClassifier`**: `@Version` 어노테이션 및 낙관적 락 필드 정합성 제약 추출
5. **`OptionalLookupClassifier`**: JDK `Optional.orElseThrow` 및 Spring Data `findById().orElseThrow()` 연쇄를 `EXISTENCE` 제약으로 통합 처리
6. **`PasswordEncoderSemanticClassifier`**: Spring Security `passwordEncoder.matches()` 실패 탐색
7. **`AuthorizationGuardClassifier`**: 권한 확인 메서드 탐색 및 타겟 경로 해소 (`SemanticTargetResolver`)

#### 레거시 룰 억제 (Suppression) 및 처리
- `SemanticRuleDispatcher`가 후보를 정규화하면, 해당 candidate ID에 대해 기존 10개 레거시 `GraphRule` 호출을 자동으로 억제(Suppress)하여 **중복 실행과 $O(N \times M)$ 비효율을 방지**합니다.
- 매칭 결과는 **`BusinessRuleCandidate`**로 반환되며 `NormalizedConstraint`, `RuleCategory`, `RuleEffect`가 정밀하게 부여됩니다.

---

### [6단계] 최종 산출물 어댑팅 (`CandidateToOutputAdapter`)

`BusinessRuleCandidate` 목록을 최종 API 산출물인 **`EndpointRuleOutput`**으로 분류 및 변환합니다.

#### 분류 규칙 (Deciding Factors)

```text
BusinessRuleCandidate
  │
  ├─ extractionStatus == UNSUPPORTED  ──> diagnostics (code: STRUCTURE_UNSUPPORTED)
  ├─ semanticStatus != RESOLVED      ──> diagnostics (code: SEMANTIC_UNRESOLVED)
  │
  └─ semanticStatus == RESOLVED
        │
        ├─ RuleEffect == REQUEST_REQUIREMENT
        │   └──> requestPreconditions (ExecutableCondition)
        │
        ├─ RuleEffect == RESPONSE_GUARANTEE
        │   └──> responseAssertions (ExecutableCondition)
        │
        └─ RuleEffect == BUSINESS_RESTRICTION (또는 JAVA_DELEGATED_GUARD)
            └──> excludedBusinessRules (ExcludedBusinessRule)
```

#### 중복 제거 (`deduplicate`)
- **`requestPreconditions` / `responseAssertions`**: `targetLocation + targetPath + operator + expectedValues + ruleId` 조합이 동일한 항목은 1개만 남기고 중복 제거합니다.
- **`excludedBusinessRules`**: `ruleId + reasonCode + targetPath` 조합으로 중복 제거합니다.

---

## 4. 결론 요약

1. **그래프 수집은 전체 코드를 넣지 않는다**: `DefaultMethodTraversalPolicy`에 의해 외부 라이브러리, JDK, Spring, Repository, 타 패키지는 바디 진입이 차단되며 애플리케이션 핵심 비즈니스 메서드만 그래프로 수집됩니다.
2. **도간된 불리언 검증까지 정밀 추적한다**: `DelegatedBooleanPredicatePromoter`와 `CompositeValidationClassifier`가 도입되어 헬퍼 메서드 내부 세부 조건(`deposit <= 0` 등)까지 승격 추출됩니다.
3. **단일 Semantic Layer로 룰 엔진이 고속화되었다**: `SemanticContext`가 그래프 인덱스를 단 1회 구축하고 `SemanticRuleDispatcher`가 7대 Classifier를 매칭한 뒤 레거시 룰 순회를 억제하여 중복 조회를 방지합니다.
4. **최종 4대 산출물로 정밀 귀속된다**: `CandidateToOutputAdapter`가 `RuleEffect`와 해소 상태에 따라 `requestPreconditions`, `responseAssertions`, `excludedBusinessRules`, `diagnostics`로 정밀하게 분기하여 최종 JSON/YAML로 출력됩니다.
