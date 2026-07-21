# Component Inventory

## Package Inventory Summary

| Subsystem / Layer | Package | Primary Responsibility | File Count |
| :--- | :--- | :--- | :---: |
| **API Intelligence Core** | `io.atworks.apiintelligence.domain` | CodeGraph, API, Evidence, Intelligence 모델 정의 | ~15 |
| **API Intelligence Adapters**| `io.atworks.apiintelligence.adapter` | JavaParser/Fact Graph 어댑터, Source Workspace 어댑터 | ~10 |
| **API Intelligence Ports** | `io.atworks.apiintelligence.port.out` | Outbound Port 인터페이스 정의 | ~8 |
| **SpecScan Entrypoints** | `io.atworks.specscan` | CLI (`GitSpecScanMain`), Demo, WebServer | 4 |
| **SpecScan Ingestion** | `io.atworks.specscan.ingestion.*` | Git 클론, 워크스페이스 및 소스 루트 탐지 | ~10 |
| **SpecScan Analysis Apps** | `io.atworks.specscan.analysis.application` | Scan, Extraction, Assembly, Preparation 서비스 | 6 |
| **SpecScan Fact CodeGraph**| `io.atworks.specscan.analysis.support.fact` | FactGraph 빌더, Method/Expression Visitor, DTO Schema Builder | 9 |
| **SpecScan Semantic Rules** | `io.atworks.specscan.analysis.support.semantic` | FactGraphIndex, Classifiers, Semantic Rule Dispatcher | 21 |
| **SpecScan Rule Engine** | `io.atworks.specscan.analysis.support.rule` | Rule Engine, Rule Packs, Candidate Detectors | ~16 |
| **SpecScan Output Adapters**| `io.atworks.specscan.analysis.support.output` | Candidate/Precondition/Response Output Adapters | 8 |

---

## Detailed Key Classes by Feature Component

### 1. CodeGraph / FactGraph Generation Pipeline Component
- `DefaultFactCodeGraphBuilder.java` - 그래프 구축 오케스트레이터
- `FactMethodVisitor.java` - 메서드 및 제어 흐름 AST Visitor
- `FactExpressionVisitor.java` - 표현식 및 연산자 AST Visitor
- `DtoSchemaGraphBuilder.java` - DTO 및 Bean Validation 확장 빌더
- `ExceptionHandlerFactScanner.java` - `@ExceptionHandler` 예외 상태 매퍼
- `DeterministicFactNodeIdGenerator.java` - 결정론적 노드/엣지 ID 생성기
- `FactGraphAccumulator.java` - 그래프 데이터 축적기

### 2. Semantic Classifiers Component
- `SemanticRuleDispatcher.java` - 그래프 시맨틱 규칙 디스패처
- `FactGraphIndex.java` - Fast Lookup 그래프 인덱서
- `BinaryConstraintClassifier.java` - 조건 판단식 분류기
- `OptionalLookupClassifier.java` - Entity Lookup 및 404 예외 분류기
- `OptimisticLockClassifier.java` - 낙관적 락 분류기
- `PasswordEncoderSemanticClassifier.java` - 암호 검증 분류기
- `AuthorizationGuardClassifier.java` - 권한/역할 분류기
- `StandardGuardMethodClassifier.java` - 가드 API 분류기

### 3. Assembly & Output Generation Component
- `OpenApiAssemblyService.java` - 스펙 아웃풋 종합 조립기
- `RuleOutputService.java` - 엔드포인트 규칙 아웃풋 도출 서비스
- `OpenApiGenerator.java` - OpenAPI 3.0 YAML 변환 생성기
- `StructuredSpecExporter.java` - 정적 분석 JSON exporter
- `ExecutionSpecExporter.java` - 백엔드 실행 모델 JSON exporter

---

## Total Package & Class Count Metrics

- **Total Java Packages**: 18
- **Total Application Source Files**: ~110 Java Files
- **Total Test Source Files**: ~45 Test Files (JUnit 5 / AssertJ / Jqwik)
