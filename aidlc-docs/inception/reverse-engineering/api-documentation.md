# API Documentation

Auto-OAS 엔진이 제공하는 외부 CLI 인터페이스, 내장 HTTP API, 및 내부 핵심 파이프라인 API 명세입니다.

---

## 1. External Command-Line Interfaces (CLI)

### 1.1 `GitSpecScanMain`
- **Main Class**: `io.atworks.specscan.GitSpecScanMain`
- **Usage**:
  ```powershell
  ./gradlew run --args="<GIT_REPO_URL> [OUTPUT_PATH]"
  ```
- **Arguments**:
  - `GIT_REPO_URL` (Required): 분석할 외부 Git 레포지토리 HTTPS URL (예: `https://github.com/example/sample-app.git`)
  - `OUTPUT_PATH` (Optional): 최종 OpenAPI 3.0 YAML 파일이 저장될 상대/절대 경로 (기본값: `build/openapi.yaml`)
- **Output Artifacts**:
  - `openapi.yaml` - 최종 OpenAPI 3.0 명세 문서
  - `api-spec-analysis.json` - 정적 분석 및 제약 조건 리포트 JSON
  - `api-execution-model.json` - 백엔드 실행 경로 모델 JSON
  - `validation-evidence-graph.json` - 전체 Fact CodeGraph JSON

### 1.2 `SpecScanDemoRunner`
- **Main Class**: `io.atworks.specscan.SpecScanDemoRunner`
- **Usage**:
  ```powershell
  ./gradlew runDemo
  ```
- **Purpose**: 파이프라인 E2E 검증용 실데모 러너. 내장 테스트 소스코드를 대상으로 파이프라인을 실행하고 결과를 검증.

---

## 2. Embedded Web Server APIs (`SpecScanWebServer`)

내장 HTTP 서버(Sun `HttpServer`, 기본 포트: 8080)를 통해 제공되는 REST API입니다.

### 2.1 `/api/scan` [POST]
- **Purpose**: Git 레포지토리에 대한 정적 분석 및 OpenAPI 명세 생성 요청.
- **Request Body (JSON)**:
  ```json
  {
    "repositoryUrl": "https://github.com/example/sample-app.git",
    "requestedRef": "main",
    "outputPath": "build/outputs/openapi.yaml"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "status": "SUCCESS",
    "scannedClasses": 42,
    "discoveredEndpoints": 15,
    "extractedConditions": 48,
    "outputPath": "build/outputs/openapi.yaml",
    "elapsedMillis": 1420
  }
  ```

### 2.2 `/api/health` [GET]
- **Purpose**: 엔진 헬스 체크 및 가동 상태 확인.
- **Response (200 OK)**:
  ```json
  {
    "status": "UP",
    "engineVersion": "1.0.0-PoC",
    "javaVersion": "21"
  }
  ```

---

## 3. Internal Application Service Interfaces

### 3.1 `GitExecutionSpecScanService`
- **Package**: `io.atworks.specscan`
- **Method Signature**:
  ```java
  public ScanResult executeScan(String repoUrl, String ref, Path outputPath) throws IngestionException;
  ```
- **Description**: Ingestion -> Static Scan -> CodeGraph Construction -> Validation Extraction -> Contract Assembly 전 과정을 순차 수행.

### 3.2 `DefaultFactCodeGraphBuilder`
- **Package**: `io.atworks.specscan.analysis.support.fact`
- **Method Signature**:
  ```java
  public FactGraphBuildResult build(StaticScanResult scan, RepositorySource source, FactGraphTraversalBudget budget);
  ```
- **Description**: 정적 스캔 결과와 소스 정보를 수신하여 `FactCodeGraph` 리스트 및 진단 결과 생성.

### 3.3 `OpenApiAssemblyService`
- **Package**: `io.atworks.specscan.analysis.application`
- **Method Signature**:
  ```java
  public void assemble(ScanAnalysisContext context, Path outputPath) throws IngestionException;
  ```
- **Description**: 스캔 및 검증 결과를 수신하여 다중 아티팩트(`openapi.yaml`, JSON 명세들)를 물리적 파일로 생성.
