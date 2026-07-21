# API Intelligence Code Graph PoC Application Design

## 1. 설계 목적

이 문서는 [requirements.md](requirements.md)를 구현 가능한 애플리케이션 구조로 구체화한다. 신규 기능은 기존 Spec Scan과 실행 경로 및 모델을
분리하되, JavaParser, JGit, Jackson과 정적 파일 제공 방식 같은 기술 기반은 재사용한다.

## 2. 핵심 설계 결정

1. 신규 bounded context를 `io.atworks.apiintelligence` 아래에 둔다.
2. 기존 `/api/scan` 서비스, 요청·응답 모델, `FactCodeGraph` 모델은 직접 사용하지 않는다.
3. 기존 그래프 모델은 요구된 TYPE, FIELD, VALIDATION 노드와 일부 간선을 표현하지 못하므로 신규 코드 그래프 모델을 정의한다.
4. 분석 실행은 비동기 job으로 수행하고 UI는 상태 API를 polling한다.
5. 발견 API 수에는 제한을 두지 않지만 OpenAI 동시 호출 수는 설정으로 제한한다.
6. OpenAI에는 API 하나의 코드 그래프와 Evidence만 전달한다.
7. OpenAI 출력은 strict JSON Schema와 로컬 Evidence 참조 검증을 모두 통과해야 유효하다.
8. 코드 그래프, Evidence, Intelligence 및 진단을 API별 JSON으로 먼저 저장하고 aggregate 결과를 만든다.
9. UI는 기존 화면과 분리된 `/intelligence.html`로 제공한다.
10. PoC 서버는 기본적으로 loopback에만 bind하며 원격 사용을 지원하지 않는다.

## 3. 시스템 경계

```text
Browser
  ├─ Existing Spec Scan UI ── /api/scan ── Existing Spec Scan Pipeline
  └─ API Intelligence UI
       ├─ POST /api/intelligence/runs
       ├─ GET  /api/intelligence/runs/{runId}
       └─ GET  /api/intelligence/runs/{runId}/result
                              │
                              ▼
                   API Intelligence Pipeline
       Source → Discovery → Graph → Evidence → OpenAI → Validation → Artifacts
```

`SpecScanWebServer`는 신규 HTTP context와 bootstrap만 등록한다. 기존 `/api/scan` handler의 동작은 변경하지 않는다. 실제 분석은
HTTP executor와 분리된 executor에서 수행한다.

## 3.1 PoC 보안 경계

- `runWeb` 서버는 기본값으로 `127.0.0.1`에만 bind한다.
- 인증과 원격 다중 사용자 사용은 PoC 범위가 아니다. loopback 외 주소로 bind하는 설정은 제공하지 않는다.
- 모든 API는 same-origin 요청만 전제로 하며 CORS header를 제공하지 않는다.
- state-changing POST는 `Content-Type: application/json`만 허용하고 Origin이 존재하면 서버 origin과 일치해야 한다.
- request body는 최대 64 KiB로 제한하고 초과 시 `REQUEST_TOO_LARGE`를 반환한다.
- Git source는 HTTP/HTTPS만 허용한다. redirect 이후 URI도 같은 protocol 정책을 다시 검증한다.
- 로컬 경로와 Git으로 취득한 repository source가 OpenAI로 전송된다는 사실을 실행 전 UI에 명시한다.
- 로컬 파일 읽기, Git clone 및 유료 OpenAI 호출 권한은 loopback을 사용하는 로컬 사용자에게만 귀속된다.

## 4. 패키지 구조

```text
io.atworks.apiintelligence
├─ web
│  ├─ ApiIntelligenceHttpHandler
│  ├─ ApiIntelligenceRequest
│  ├─ ApiIntelligenceResponse
│  └─ ApiIntelligenceErrorMapper
├─ application
│  ├─ StartAnalysisUseCase
│  ├─ GetAnalysisStatusUseCase
│  ├─ GetAnalysisResultUseCase
│  ├─ AnalysisOrchestrator
│  └─ AnalyzeEndpointService
├─ domain
│  ├─ source
│  ├─ api
│  ├─ graph
│  ├─ evidence
│  ├─ intelligence
│  ├─ diagnostic
│  └─ run
├─ port
│  └─ out
│     ├─ SourceWorkspacePort
│     ├─ ApiDiscoveryPort
│     ├─ CodeGraphPort
│     ├─ EvidencePort
│     ├─ IntelligenceModelPort
│     ├─ RunArtifactPort
│     └─ RunProgressPort
├─ adapter
│  ├─ source
│  ├─ javaparser
│  ├─ openai
│  └─ file
└─ config
   ├─ ApiIntelligenceConfiguration
   └─ ApiIntelligenceBootstrap
```

도메인과 application 계층은 JavaParser, JGit, OpenAI HTTP, 파일 시스템 타입에 의존하지 않는다.

## 5. 도메인 모델

### 5.1 입력 소스

```text
AnalysisSource
├─ GitSource(url, revisionType?, revision?)
└─ LocalSource(path)

RevisionType = BRANCH | TAG | COMMIT
```

- Git은 HTTP/HTTPS URL을 지원한다. SSH, `file://` 및 로컬 Git URL은 PoC에서 제외한다.
- revision이 없으면 remote default branch의 HEAD를 사용한다.
- 로컬 소스는 원본을 변경하지 않고 제자리에서 read-only로 분석한다.
- clone한 임시 workspace만 실행 종료 시 정리한다. 사용자 로컬 경로는 절대 정리하지 않는다.

### 5.2 발견 API

```text
DiscoveredApi
- apiId
- httpMethod
- path
- controllerType
- handlerSignature
- requestBindings
- responseType
- sourceLocation
```

`apiId`는 HTTP method, path, controller type 및 handler signature의 정규화 문자열로부터 SHA-256으로 생성한다. JSON에는
전체 ID를 저장하고 폴더명에는 충돌을 확인한 안전한 축약 ID를 사용할 수 있다.

### 5.3 코드 그래프

```text
CodeGraph(apiId, nodes, edges, diagnostics)
CodeNode(id, kind, name, sourceLocation?, attributes)
CodeEdge(id, kind, sourceNodeId, targetNodeId, sourceLocation?)
```

Node kind:

- API, METHOD, TYPE, FIELD, VALIDATION, CONDITION, EXCEPTION

Edge kind:

- DECLARES, CALLS, USES_TYPE, BINDS_REQUEST, VALIDATES, CHECKS, RETURNS, THROWS

소스에서 파생된 노드는 가능한 경우 저장소 상대 경로, 시작·종료 라인과 열을 가진다. 해석하지 못한 호출이나 타입에 대해 간선을 만들어내지 않고 진단을 남긴다.

### 5.4 Evidence

```text
Evidence
- evidenceId
- apiId
- kind
- sourceLocation
- snippet
- graphNodeIds
```

Evidence ID는 API ID, 상대 파일 경로, 라인 범위 및 kind로부터 결정적으로 생성한다. snippet은 선언된 라인 범위에서만 읽는다.

### 5.5 Intelligence

```text
ApiIntelligence
- apiId
- preConditions
- responseAssertions
- businessRules
- exceptions
- additionalAnalysis

IntelligenceItem
- description
- category
- evidenceIds
- confidence
```

`confidence`는 0 이상 1 이하의 유한한 수다. 모든 항목은 하나 이상의 Evidence ID를 가져야 한다.

### 5.6 실행과 진단

```text
AnalysisRunStatus
QUEUED → ACQUIRING → DISCOVERING → GRAPHING → ANALYZING → SAVING
       → COMPLETED | PARTIAL | FAILED

ApiAnalysisStatus
PENDING | ANALYZING | SUCCEEDED | FAILED | UNSUPPORTED
```

실행 상태에는 전체, 완료, 성공, 실패 API 수와 안전한 사용자 메시지를 포함한다. 진단은 stable error code, 단계, 안전한 메시지, retryable 여부를
가진다.

실행 registry는 다음 규칙을 따른다.

- 실행 record는 thread-safe한 단일 상태 객체가 소유한다.
- worker는 atomic transition을 통해서만 상태를 변경한다.
- HTTP 조회에는 mutable collection을 노출하지 않고 immutable snapshot을 반환한다.
- terminal result는 artifact 최종화 후 한 번만 publish한다.
- 동시 active run은 1개로 제한한다. 추가 실행은 `RUN_CAPACITY_EXCEEDED`로 거부한다.
- terminal run snapshot은 최대 20개 또는 24시간 중 먼저 도달한 조건으로 registry에서 제거한다.
- registry 제거는 JSON artifact를 삭제하지 않는다. artifact 정리는 PoC에서 수동이다.
- 서버 종료 hook에서 신규 실행을 막고 executor를 제한 시간 내 종료한다.

## 6. 출력 포트

### 6.1 SourceWorkspacePort

- Git source를 임시 디렉터리에 clone하고 revision을 resolve한다.
- 로컬 source의 경로와 Java source root를 검증한다.
- resolved revision, source roots, cleanup 책임을 포함하는 workspace handle을 반환한다.

### 6.2 ApiDiscoveryPort

- Spring MVC Mapping annotation을 분석한다.
- 클래스와 메서드 수준 path 및 HTTP method를 조합한다.
- 요청 binding, DTO, response type과 handler 위치를 반환한다.
- Maven/Gradle을 실행하지 않고 일반 source root 패턴과 선언된 source root를 탐색한다.
- mapping 발견은 syntax-only 분석으로 수행할 수 있어야 한다.
- symbol resolution이 실패해도 발견 가능한 API는 유지하고 ambiguity 진단을 남긴다.

### 6.3 CodeGraphPort

- API 하나를 root로 shallow static graph를 만든다.
- Controller에서 Service 직접 호출, DTO, validation, 조건, throw 및 return을 연결한다.
- 탐색 한도를 초과하면 그래프를 조용히 잘라내지 않고 truncation 진단을 남긴다.
- resolved symbol을 우선 사용하되, 해석 불가 시 같은 compilation unit과 import/type name 기반의 제한된 syntax fallback을
  사용한다.
- fallback으로 유일하게 식별할 수 없는 service 호출이나 type 관계는 연결하지 않고 `SYMBOL_RESOLUTION_AMBIGUOUS` 진단을 남긴다.
- Mapping API 추출은 보장 범위지만 service call graph의 완전성은 symbol resolution 가능 여부에 따른 best-effort다.

### 6.4 EvidencePort

- 그래프 노드와 source location을 Evidence catalog로 변환한다.
- Evidence ID, snippet, graph node reference의 일관성을 검증한다.

### 6.5 IntelligenceModelPort

- API별 context를 OpenAI에 전달한다.
- provider transport 모델을 도메인 모델로 변환한다.
- provider 오류 본문이나 secret을 외부로 전달하지 않는다.

### 6.6 RunArtifactPort

- 실행 폴더와 API별 artifact를 UTF-8 pretty JSON으로 저장한다.
- 임시 파일에 기록한 뒤 가능한 경우 atomic move한다.
- 출력 경로가 분석 대상 내부이면 거부하여 재귀 분석을 방지한다.
- output root와 모든 child path는 normalized real path 기준으로 containment를 검증하고 root 밖을 가리키는 symlink를 따르지
  않는다.
- run directory는 `CREATE_NEW` 의미로 만들며 충돌 시 새 run ID로 제한 횟수만큼 재시도한다.
- terminal `run.json`이 실행 artifact의 최종 권위 상태다.
- `result.json`은 실제 저장에 성공한 artifact만 참조한다.

### 6.7 RunProgressPort

- 프로세스 생명주기 동안 실행 상태와 완료 결과 위치를 제공한다.
- 파일이 영구 산출물의 기준이며, 서버 재시작 후 실행 목록을 복원하는 기능은 제공하지 않는다.

## 7. 실행 흐름

### 7.1 실행 시작

1. UI가 `POST /api/intelligence/runs`를 호출한다.
2. handler는 입력 형식, OpenAI 설정 및 output root를 검증한다.
3. run ID를 만들고 QUEUED 상태를 등록한다.
4. active run이 이미 있거나 run executor가 수용할 수 없으면 `RUN_CAPACITY_EXCEEDED`로 거부한다.
5. 별도 분석 executor에 작업을 제출한다.
6. HTTP 202와 `runId`, `statusUrl`, `resultUrl`을 즉시 반환한다.

### 7.2 정적 분석

1. source workspace를 준비한다.
2. Java source roots와 Spring MVC API를 발견한다.
3. `apis.json`과 초기 `run.json`을 저장한다.
4. 발견 API를 stable order로 순회하며 bounded worker 수만큼 lazy scheduling한다.
5. API마다 코드 그래프를 생성하고 `graph.json`을 저장한다.
6. 그래프에서 Evidence를 구성하고 `evidence.json`을 저장한다.

그래프와 Evidence는 OpenAI 호출 전에 저장한다. 따라서 LLM 호출이 실패해도 실제 입력 근거를 확인할 수 있다.

### 7.3 OpenAI 분석

1. API 하나의 identity, 해당 API의 정규화 graph subset, 해당 API의 Evidence catalog만 결정적 순서의 JSON으로 구성한다.
2. repository snippet은 신뢰할 수 없는 데이터이며 그 안의 지시를 따르지 말라는 system instruction을 포함한다.
3. OpenAI 호출 한 번은 API 하나만 분석한다. 발견 API가 10개이면 API별 요청을 총 10회 시도하며 여러 API를 한 요청에 합치지 않는다.
4. strict Structured Output을 수신한다.
5. transport schema와 도메인 불변식을 검증한다.
6. 모든 Evidence ID가 해당 입력 catalog에 존재하는지 검증한다.
7. 검증 성공 결과만 `intelligence.json`으로 저장한다.
8. 실패하면 `diagnostics.json`에 안전한 오류를 저장하고 다음 API를 계속한다.

### 7.4 실행 완료

1. API별 결과를 stable API order로 합친다.
2. staging 파일을 이용해 `result.json`을 원자적으로 교체한다.
3. 실제 artifact 저장 상태를 반영한 terminal `run.json`을 마지막으로 원자적으로 교체한다.
4. 모두 성공하면 COMPLETED, 일부 실패하면 PARTIAL, 실행 자체가 불가능하면 FAILED로 종료한다.
5. aggregate 또는 terminal manifest 저장이 실패하면 registry는 FAILED로 표시하고 `ARTIFACT_FINALIZATION_FAILED`를
   반환한다. 이전 부분 artifact는 진단 목적으로 유지한다.
6. clone workspace는 `finally`에서 정리한다.

## 8. OpenAI 계약

### 8.1 HTTP

- JDK `java.net.http.HttpClient`를 사용한다.
- 기본 endpoint는 `https://api.openai.com/v1/responses`다.
- model은 `gpt-4o-mini`, `store`는 `false`다.
- endpoint는 fake server 기반 contract test를 위해 설정 가능해야 한다.
- 공식 OpenAI 문서 기준으로 `gpt-4o-mini`의 Responses API 및 Structured Outputs 지원 여부를 contract 구현 시 다시 검증한다.

### 8.2 입력

```text
api: { apiId, httpMethod, path, handler }
graph: { nodes[], edges[] }
evidence: { evidenceId, file, startLine, endLine, kind, snippet }[]
```

- 전체 저장소를 보내지 않는다.
- 다른 API의 graph나 Evidence를 함께 보내지 않는다.
- 요청 하나와 응답 하나는 동일한 단일 `apiId`에 귀속된다.
- 그래프와 Evidence는 stable ID와 stable order를 사용한다.
- 입력이 `max-input-characters`를 넘으면 조용히 자르지 않고 해당 API를 `CONTEXT_LIMIT_EXCEEDED`로 실패 처리한다.

### 8.3 Structured Output

모든 object는 `additionalProperties: false`를 사용한다. 다섯 결과 배열은 항상 존재하며 결과가 없으면 빈 배열이다.

```json
{
  "apiId": "string",
  "preConditions": [],
  "responseAssertions": [],
  "businessRules": [],
  "exceptions": [],
  "additionalAnalysis": []
}
```

각 배열 항목은 다음 schema를 따른다.

```json
{
  "description": "string",
  "category": "string",
  "evidenceIds": ["string"],
  "confidence": 0.0
}
```

배열 항목은 최소 하나의 중복 없는 Evidence ID를 가져야 한다. refusal 또는 incomplete response는 빈 정상 결과가 아니라 API 실패다.

Responses API request는 `model`, `instructions`, JSON `input`, `store: false` 및 `text.format`의 strict
`json_schema`를 사용한다. response adapter는 `output` 항목 중 assistant의 `output_text` content만 허용된
structured payload로 추출한다. `status`, `incomplete_details`, refusal content 및 provider request ID를 별도
검사한다. 이 wire shape는 공식 OpenAI API 문서와 동일한 JSON fixture로 contract test에 고정한다.

### 8.4 로컬 검증

- 응답 apiId가 요청 apiId와 같아야 한다.
- description과 category는 비어 있지 않아야 한다.
- confidence는 0 이상 1 이하의 유한한 값이어야 한다.
- 모든 evidenceIds는 해당 API 입력 Evidence catalog에 존재해야 한다.
- 하나라도 위반하면 해당 API의 전체 Intelligence를 거부한다.

## 9. 동시성, timeout 및 재시도

- HTTP server executor와 분석 executor를 분리한다.
- API 개수는 제한하지 않지만 API 작업은 bounded executor queue에서 처리한다.
- API별 task를 한 번에 전부 submit하지 않는다. 완료되는 worker slot만큼 다음 API를 lazy scheduling한다.
- active run은 1개이며 run executor queue에는 대기 run을 두지 않는다.
- OpenAI 최대 동시 호출 기본값은 2다.
- 요청별 timeout 기본값은 60초다.
- 최대 재시도 횟수 기본값은 2다.
- I/O 오류, timeout, HTTP 408, 429 및 5xx만 재시도한다.
- 인증·입력 관련 4xx, refusal, incomplete, JSON/schema/Evidence 검증 실패는 재시도하지 않는다.
- `Retry-After`가 안전한 범위이면 우선 적용하고, 아니면 jitter가 있는 제한된 지수 backoff를 사용한다.
- connect timeout은 10초, `Retry-After` 상한은 30초로 한다.
- 한 API의 전체 attempt deadline은 `(request timeout + 최대 backoff) × 허용 attempt` 범위를 넘지 않는다.
- API task의 예외는 typed diagnostic으로 변환하여 sibling task로 전파하지 않는다.

## 10. 설정

설정 우선순위는 환경 변수, 작업 디렉터리의 `application-local.yml`, classpath의 `application.yml` 순이다. UTF-8 YAML로 읽고 알
수 없는 key와 유효하지 않은 값은 설정 진단으로 거부한다.

```yaml
api-intelligence:
  output-root: build/api-intelligence-runs
  graph:
    max-depth: 8
    max-methods: 100
    max-edges: 2000
  openai:
    api-key: ""
    endpoint: https://api.openai.com/v1/responses
    model: gpt-4o-mini
    request-timeout-seconds: 60
    connect-timeout-seconds: 10
    max-retries: 2
    max-concurrency: 2
    retry-after-cap-seconds: 30
    max-input-characters: 120000
```

- 환경 변수 `OPENAI_API_KEY`가 있으면 YAML key를 덮어쓴다.
- 실제 `application-local.yml`은 repository 작업 디렉터리에서만 읽고 Git에서 제외한다.
- repository root의 `application-local.example.yml`은 빈 key 예시를 제공한다.
- key 누락은 서버 시작을 막지 않으며 run 시작 시 `OPENAI_NOT_CONFIGURED`를 반환한다.
- intelligence 설정 오류나 bootstrap 실패는 degraded handler 상태로 격리하며 기존 `/api/scan` 서버 시작을 막지 않는다.

## 11. JSON 산출물

```text
<output-root>/<yyyyMMdd-HHmmss>-<runId>/
├─ run.json
├─ apis.json
├─ result.json
└─ api/
   └─ <safe-api-id>/
      ├─ graph.json
      ├─ evidence.json
      ├─ intelligence.json
      └─ diagnostics.json
```

- `intelligence.json`은 검증 성공 시에만 존재한다.
- `diagnostics.json`은 실패 또는 truncation 등 진단이 있을 때 저장한다.
- `result.json`은 UI와 Raw JSON의 aggregate 계약이다.
- terminal `run.json`이 artifact set의 권위 있는 완료 상태이며 항상 마지막에 저장한다.
- source path는 가능한 경우 workspace 상대 경로와 `/` 구분자를 사용한다.
- run config snapshot에는 모델 및 비민감 설정만 기록한다.
- OpenAI Authorization header, API key, 원본 provider 오류 body 및 전체 request/response envelope는 저장하지
  않는다.
- 진단은 allowlist field만 새 객체로 구성한다. 임의 exception message, HTTP header/body, prompt 또는 snippet을 그대로
  기록하지 않는다.
- output 폴더 보존과 삭제는 사용자가 수동으로 관리한다.

## 12. HTTP API

### 12.1 실행 시작

```http
POST /api/intelligence/runs
Content-Type: application/json
```

Git 입력:

```json
{
  "source": {
    "type": "GIT",
    "url": "https://example.com/org/repository.git",
    "revisionType": "BRANCH",
    "revision": "main"
  }
}
```

Local 입력:

```json
{
  "source": {
    "type": "LOCAL",
    "path": "D:/workspace/project"
  }
}
```

응답은 HTTP 202다.

```json
{
  "runId": "string",
  "statusUrl": "/api/intelligence/runs/{runId}",
  "resultUrl": "/api/intelligence/runs/{runId}/result"
}
```

### 12.2 상태 조회

```http
GET /api/intelligence/runs/{runId}
```

```json
{
  "runId": "string",
  "status": "ANALYZING",
  "phase": "ANALYZING",
  "totalApis": 10,
  "completedApis": 4,
  "succeededApis": 3,
  "failedApis": 1,
  "outputDirectory": null,
  "message": "string"
}
```

### 12.3 결과 조회

```http
GET /api/intelligence/runs/{runId}/result
```

COMPLETED 또는 PARTIAL이면 aggregate result를 반환한다. 아직 완료되지 않았으면 `RUN_NOT_FINISHED`, 존재하지 않으면
`RUN_NOT_FOUND`를 반환한다.

### 12.4 오류 계약

```json
{
  "code": "OPENAI_NOT_CONFIGURED",
  "message": "OpenAI API key is not configured.",
  "field": null,
  "runId": null
}
```

stack trace, provider body, credential 및 source snippet은 HTTP 오류에 포함하지 않는다.

HTTP mapping:

- 400: 입력, Content-Type, Origin 또는 설정 형식 오류
- 404: run ID 없음
- 409: run이 아직 완료되지 않아 result를 제공할 수 없음
- 413: request body 제한 초과
- 429: active run 또는 executor capacity 초과
- 500: 예상하지 못한 handler 오류 또는 artifact 최종화 실패

## 13. UI 설계

### 13.1 화면 분리

- 공통 상단 메뉴: `Spec Scan`과 `API Intelligence`
- 기존 화면: `/`
- 신규 화면: `/intelligence.html`
- 기존 `app.js`와 신규 `intelligence.js`의 상태 및 DOM root를 공유하지 않는다.
- 공통 CSS token과 navigation 스타일만 공유한다.

### 13.2 상태 모델

```text
view: form | running | result | fatalError
form: sourceType, localPath, repositoryUrl, revisionType, revision
job: id, status, phase, counts, outputDirectory, message
filters: query, method, status
selectedApiId
result
error
```

새 실행 시 이전 결과를 비우고 중복 제출을 막는다. API 선택은 배열 index가 아닌 stable apiId로 관리한다.

### 13.3 polling

- POST 성공 후 1초 간격으로 상태를 조회한다.
- 네트워크 오류는 2초, 4초, 8초 backoff 후 재시도한다.
- terminal status에서 polling을 중지하고 result를 조회한다.
- API 발견 전에는 indeterminate progress, 발견 후에는 완료/전체 progress를 표시한다.

### 13.4 결과 표현

- 실행 요약: 전체, 성공, 실패, elapsed time, 저장 폴더
- API 목록: method, path, handler, 상태, category별 개수
- 검색: method, path, controller
- 필터: HTTP method, 분석 상태
- API 상세: Evidence, PreCondition, ResponseAssertion, BusinessRule, Exception, Additional Analysis
- Intelligence의 Evidence ID를 선택하면 같은 API의 source Evidence를 강조한다.
- 실패 API는 code, safe message 및 retryable 여부를 표시한다.
- 빈 category는 `발견된 항목 없음`으로 표시한다.
- Raw JSON은 기본 닫힘 상태의 `<details>`로 제공하고 copy 기능을 둔다.

### 13.5 안전성과 접근성

- source path, snippet, LLM description 및 오류는 신뢰할 수 없는 문자열로 취급한다.
- 동적 문자열은 `textContent` 또는 안전한 DOM API로 렌더링한다.
- 현재 메뉴에 `aria-current="page"`를 제공한다.
- field 오류는 label과 `aria-describedby`로 연결한다.
- 진행 영역은 `role="status"`, 치명 오류는 `role="alert"`를 사용한다.
- 상태는 색상만으로 구분하지 않는다.
- 키보드 focus와 reduced-motion을 지원한다.

## 14. 오류 분류

Run-level fatal error:

- INVALID_REQUEST
- LOCAL_PATH_NOT_FOUND
- LOCAL_PATH_NOT_READABLE
- GIT_CLONE_FAILED
- GIT_REVISION_NOT_FOUND
- REQUEST_TOO_LARGE
- ORIGIN_NOT_ALLOWED
- RUN_CAPACITY_EXCEEDED
- OPENAI_NOT_CONFIGURED
- OUTPUT_ROOT_INVALID
- NO_SUPPORTED_API

API-level error:

- API_DISCOVERY_INCOMPLETE
- GRAPH_BUILD_FAILED
- GRAPH_TRUNCATED
- EVIDENCE_BUILD_FAILED
- CONTEXT_LIMIT_EXCEEDED
- OPENAI_TIMEOUT
- OPENAI_RATE_LIMITED
- OPENAI_REQUEST_FAILED
- OPENAI_REFUSED
- OPENAI_INCOMPLETE
- OUTPUT_SCHEMA_INVALID
- EVIDENCE_REFERENCE_INVALID
- ARTIFACT_WRITE_FAILED
- ARTIFACT_FINALIZATION_FAILED
- SYMBOL_RESOLUTION_AMBIGUOUS

오류는 단계와 범위에 따라 run 또는 API에 귀속한다. provider의 원본 오류 payload는 폐기하고 status, request ID, stable code 및 안전한
요약만 유지한다.

## 15. 테스트 설계

### 15.1 단위 테스트

- API ID와 Evidence ID의 결정성
- Mapping annotation path 조합
- DTO/Bean Validation, if/switch/throw/return graph 생성
- graph integrity와 dangling edge 거부
- Evidence source range와 snippet 일치
- context stable ordering과 size limit
- Structured Output 역직렬화와 모든 불변식
- 존재하지 않거나 빈 Evidence 참조 거부
- 설정 우선순위와 API key 누락
- retry 분류와 backoff
- secret redaction
- 안전한 artifact path와 atomic writer

### 15.2 OpenAI adapter contract test

로컬 fake `HttpServer`를 사용하여 다음을 검증한다.

- endpoint, Authorization, model, `store: false`, JSON Schema 전송
- 정상 200, refusal, incomplete, 401, 408, 429, 5xx, timeout
- malformed response와 Evidence 참조 오류
- Authorization 값이 진단과 artifact에 나타나지 않음

### 15.3 application 통합 테스트

- 여러 API의 성공, timeout, invalid reference 혼합 실행
- 모든 API 시도와 부분 성공
- 동시 호출 상한
- stable result ordering
- graph와 Evidence가 LLM 호출 전에 저장됨
- API 하나의 저장 실패가 sibling을 취소하지 않음

### 15.4 프로젝트 검증

- 지원 annotation, validation, 조건 및 예외를 포함한 독립 fixture
- `D:\workspace\real-estate\RealEstate` smoke test
- RealEstate 전용 assertion이나 하드코딩 금지
- live OpenAI test는 API key가 있을 때만 opt-in으로 실행하며 기본 테스트에서 제외
- LLM 문구 자체가 아니라 schema와 Evidence 무결성을 검증

## 16. 구현 영향 파일 범위

예상 신규 범위:

- `src/main/java/io/atworks/apiintelligence/**`
- `src/test/java/io/atworks/apiintelligence/**`
- `src/main/resources/application.yml`
- `application-local.example.yml`
- `src/main/resources/static/intelligence.html`
- `src/main/resources/static/intelligence.js`

예상 최소 수정 범위:

- `SpecScanWebServer.java`: 신규 bootstrap/context 등록
- `index.html`: API Intelligence 메뉴 링크
- `style.css`: 공통 navigation 및 신규 화면 스타일
- `build.gradle`: 필요한 설정/테스트 지원 의존성이 생기는 경우만 수정
- `.gitignore`: `application-local.yml`과 실행 산출물 제외

## 17. 설계상 비목표

- 기존 Spec Scan architecture 리팩터링
- 기존 `FactCodeGraph` 확장 또는 migration
- 실행 취소 API
- 실행 이력 목록과 서버 재시작 후 registry 복원
- SSE 또는 WebSocket
- OpenAI 외 provider 추상화 구현
- oversized context chunking
- 분석 API 수 제한 또는 사용자의 API 선택

`IntelligenceModelPort`는 테스트와 경계 분리를 위한 포트이며, 이번 PoC에서 여러 provider 구현을 만들기 위한 것이 아니다.
