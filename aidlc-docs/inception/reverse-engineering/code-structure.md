# Code Structure & File Inventory

## Build System & Environment

- **Build Tool**: Gradle 9.0 (Gradle Wrapper included: `gradlew`, `gradlew.bat`)
- **JDK Version**: Java 21 (Language Level: `BLEEDING_EDGE`)
- **Core Library**: JavaParser `3.25.7` (AST 정적 코드 파싱 및 심볼 해설)
- **Serialization**: Jackson Databind (`com.fasterxml.jackson.core:jackson-databind`)
- **Testing Framework**: JUnit 5, AssertJ, Jqwik 1.9.2 (Property-Based Testing)

---

## Package Hierarchy

```text
src/main/java/
├── io/atworks/apiintelligence/                  # [Core Domain & Abstractions]
│   ├── adapter/
│   │   ├── fact/                                # Fact Graph Evidence 어댑터
│   │   ├── file/                                # Run Artifact JSON 직렬화 어댑터
│   │   ├── javaparser/                          # JavaParser 기반 CodeGraph/ApiDiscovery 어댑터
│   │   ├── openai/                              # OpenAI LLM 어댑터 (Optional)
│   │   └── source/                              # Workspace 및 Git Source 어댑터
│   ├── application/                             # API Intelligence 스캔 서비스 및 Orchestrator
│   ├── config/                                  # 시스템 설정 및 진단
│   ├── domain/                                  # 도메인 모델 (graph, api, evidence, intelligence, run, source)
│   └── port/out/                                # Outbound Ports (CodeGraphPort, ApiDiscoveryPort 등)
│
└── io/atworks/specscan/                         # [Engine Implementation & Analysis Pipeline]
    ├── GitExecutionSpecScanService.java         # Git 실행 스캔 오케스트레이션 서비스
    ├── GitSpecScanMain.java                     # CLI 엔트리포인트
    ├── SpecScanDemoRunner.java                  # E2E 파이프라인 실데모 러너
    ├── SpecScanWebServer.java                   # Embedded HTTP 웹 서버 및 UI API
    ├── analysis/
    │   ├── application/                         # 파이프라인 응용 서비스 (Scan, Validation, Rule, Assembly)
    │   ├── domain/                              # 엔드포인트, 조건, 검증, 규칙 도메인 객체
    │   └── support/                             # 분석 서포트 모듈 (fact, semantic, rule, candidate, output)
    └── ingestion/
        ├── adapter/                             # Git 클론 및 로컬 디렉토리 어댑터
        ├── application/                         # RepositoryIngestionService
        ├── domain/                              # Ingestion 도메인 엔티티 (Workspace, SourceRoot)
        └── port/                                # Ingestion Ports
```

---

## Existing Files Inventory

### 1. Engine Core & Main Entry Points (`io.atworks.specscan`)
- `GitSpecScanMain.java` - CLI 실행 엔트리포인트. CLI 인자를 받아 E2E 파이프라인 기동.
- `SpecScanDemoRunner.java` - E2E 파이프라인 통합 데모 러너. 임시 워크스페이스 생성 후 전체 파이프라인 수행.
- `SpecScanWebServer.java` - 내장 Sun HTTP Server 기반 웹 API 서버. 스캔 요청 수신 및 결과 제공.
- `GitExecutionSpecScanService.java` - Ingestion -> Scan -> Extraction -> Assembly 전체 파이프라인 오케스트레이션.

### 2. Analysis Application Layer (`io.atworks.specscan.analysis.application`)
- `SpringStaticScanService.java` - 소스 루트 탐색 및 `EndpointExtractor`를 통한 Controller/Endpoint 스캔.
- `ValidationExtractionService.java` - DTO 어노테이션, Custom Validator, Service Hint 검증 candidate 추출.
- `RuleOutputService.java` - Graph evidence와 정규화된 규칙을 병합하여 엔드포인트별 규칙 아웃풋 도출.
- `OpenApiAssemblyService.java` - `StructuredSpec`, `ExecutionModel`, `OpenAPI YAML`, `FactGraph JSON` 조립 및 저장.
- `ScanAnalysisContext.java` - 파이프라인 수행 컨텍스트 DTO.
- `ScanPreparationService.java` - 정적 분석 전 워크스페이스 준비 및 검증.

### 3. CodeGraph & Fact Graph Construction (`io.atworks.specscan.analysis.support.fact`)
- `DefaultFactCodeGraphBuilder.java` - Endpoint AST로부터 전체 `FactCodeGraph`를 다차원 구축하는 핵심 빌더.
- `FactMethodVisitor.java` - 메서드 내부 제어 흐름(Return, Throw, If, Switch, Lambda) 파싱 및 Outcome 통합.
- `FactExpressionVisitor.java` - 표현식(Binary, MethodCall, InstanceOf, Unary) 파싱 및 CodeNode/Edge 바인딩.
- `DtoSchemaGraphBuilder.java` - DTO 타입 스키마 및 Bean Validation 어노테이션 노드 확장.
- `ExceptionHandlerFactScanner.java` - `@ExceptionHandler` 스캔을 통한 예외-HTTP Status 노드/엣지 매핑.
- `DeterministicFactNodeIdGenerator.java` - 결정론적 노드/엣지 ID 생성기.
- `FactGraphAccumulator.java` - Traversal 중 노드 및 엣지 수집 및 한계(Budget) 관리.
- `FactGraphIntegrityValidator.java` - 그래프 무결성 검증기.

### 4. Semantic Classifiers & Dispatcher (`io.atworks.specscan.analysis.support.semantic`)
- `SemanticRuleDispatcher.java` - Graph Index 기반 시맨틱 규칙 디스패처.
- `FactGraphIndex.java` - Node Type, Edge Type, Call Chain 기반 Fast Lookup 그래프 인덱스.
- `SemanticContext.java` - 시맨틱 분석 실행 컨텍스트.
- `BinaryConstraintClassifier.java` - 숫자/문자열 범위 비교식 분류기.
- `OptionalLookupClassifier.java` - Optional lookup 및 404 Entity Not Found 패턴 분류기.
- `OptimisticLockClassifier.java` - Version conflict 낙관적 락 패턴 분류기.
- `PasswordEncoderSemanticClassifier.java` - 비밀번호 검증 패턴 분류기.
- `AuthorizationGuardClassifier.java` - Role/Permission 권한 검증 패턴 분류기.
- `StandardGuardMethodClassifier.java` - `Objects.requireNonNull`, `Assert.hasText` 가드 API 분류기.

### 5. Rule Engine & Output Adapters (`io.atworks.specscan.analysis.support.rule` / `output`)
- `DefaultGraphRuleEngine.java` - 규칙 팩 실행 엔진.
- `RulePackRegistry.java` - 시스템 규칙 팩 레지스트리.
- `CandidateToOutputAdapter.java` - ValidationCandidate -> ApiCondition 변환 어댑터.
- `RequestBindingConditionAdapter.java` - RequestBinding 조건 변환 어댑터.
- `ResponseInvariantAdapter.java` - Response 불변식 변환 어댑터.
- `OpenApiGenerator.java` - Execution Model JSON -> OpenAPI 3.0 YAML 변환기.

---

## Key Node & Edge Kinds Specification

### 1. CodeNode / FactNode Types (`FactNodeType`)
| Node Type | Description | Payload Data |
| :--- | :--- | :--- |
| `API_METHOD` | Spring Controller 진입점 메서드 | Controller Class, Method Signature, Line Range |
| `METHOD` | 일반 서비스/도메인 메서드 | Declaring Class, Method Signature |
| `CONSTRUCTOR` | 명시적/가상(Lombok, Record) 생성자 | Owner Class, Constructor Signature |
| `RETURN` | 반환문 구문 노드 | Return Expression Snippet |
| `THROW` | 예외 던짐 구문 노드 | Exception Expression Snippet |
| `VALUE_FIELD` | DTO 필드 또는 객체 속성 | Field Name, Root Expression Kind |
| `PARAMETER` | 메서드/생성자 매개변수 | Parameter Name, Type, Ordinal |
| `OBJECT_CREATION` | `new` 객체 생성 표현식 | Created Type, Argument Count |
| `PREDICATE_CANDIDATE` | 조건 검증식 후보 | Predicate Type, Expression Snippet |
| `EXCEPTION` | 도출된 예외 타입 | Exception Class Name |
| `HTTP_STATUS` | 매핑된 HTTP 응답 상태 코드 | Status Code (200, 400, 404 등), Status Name |

### 2. CodeEdge / FactEdge Types (`FactEdgeType`)
| Edge Type | Description | Role / Purpose |
| :--- | :--- | :--- |
| `CALLS` | 메서드/생성자 호출 관계 | `CALL`, `TARGET`, `CONSTRUCTOR`, `SOURCE_FALLBACK` |
| `OPERAND_OF` | 표현식 피연산자 관계 | `ARGUMENT`, `RECEIVER`, `LEFT`, `RIGHT` |
| `READS` | 변수/필드 읽기 참조 | `VARIABLE_READ`, `FIELD_READ` |
| `WRITES` | 변수/필드 쓰기 할당 | `VARIABLE_WRITE` |
| `VALUE_FLOWS_TO` | 데이터 흐름 연결 | `CALL_ARGUMENT_TO_PARAMETER`, `LOMBOK_CONSTRUCTOR_ARGUMENT` |
| `THROWS` | 메서드에서 예외 던짐 | `EXCEPTION` |
| `HANDLED_BY` | 예외 핸들러 바인딩 | `HANDLER` |
| `MAPS_TO` | 예외 핸들러 -> HTTP Status | `HTTP_STATUS` |
| `ORIGINATES_FROM` | 매개변수 출처 바인딩 | `CALL_ARGUMENT` |

---

## Key Design Patterns Applied

1. **Hexagonal Architecture (Ports and Adapters)**:
   - Application Core와 Ingestion/Parser/LLM/Storage 간의 결합도를 낮추기 위해 Port 인터페이스와 Adapter 구현체를 엄격히 분리.
2. **Visitor Pattern (`JavaParser AST Visitors`)**:
   - AST 노드를 순회하며 노드 및 엣지를 추출하는 `FactMethodVisitor`, `FactExpressionVisitor` 구현.
3. **Pipeline Pattern**:
   - `Ingestion` -> `Static Scan` -> `CodeGraph Construction` -> `Validation Extraction` -> `Contract Assembly`로 이어지는 5단계 파이프라인.
4. **Strategy & Classifier Pattern (Rule Engine)**:
   - 다양한 검증 패턴(`BinaryConstraint`, `OptionalLookup`, `Authorization` 등)을 격리된 Classifier 구현체로 분리하여 유연하게 확장.
5. **Builder & Accumulator Pattern**:
   - 그래프 생성 시 Traversal Budget 및 메모리 한계를 제어하며 안전하게 그래프 노드/엣지를 수집하는 `FactGraphAccumulator` 활용.
