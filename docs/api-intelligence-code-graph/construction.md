# API Intelligence Code Graph Construction

## 운영 규칙

- UoW 순서와 의존성을 지킨다.
- 각 UoW의 focused test가 통과하기 전 다음 통합 단계로 넘어가지 않는다.
- 체크할 때 실행 명령과 결과 또는 artifact 경로를 Evidence에 기록한다.
- 기존 `/api/scan` 회귀, secret 비노출, API별 실패 격리를 반복 확인한다.
- 환경이나 권한 때문에 실행하지 못한 검증은 체크하지 않고 사유를 기록한다.

## UOW-01 독립 도메인 모델

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence:

- 신규 코드: `src/main/java/io/atworks/apiintelligence/domain/**`
- 신규 테스트: `src/test/java/io/atworks/apiintelligence/domain/**`
- focused:
  `.\gradlew.bat test --tests "io.atworks.apiintelligence.domain.*" --tests "io.atworks.apiintelligence.domain.*.*"` —
  성공
- regression: `.\gradlew.bat test` — 성공
- 기존 `io.atworks.specscan` 파일 변경 및 import 없음

## UOW-02 설정과 비밀정보

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence:

- 신규 코드: `src/main/java/io/atworks/apiintelligence/config/**`
- 기본 설정: `src/main/resources/application.yml`
- local example/ignore: `application-local.example.yml`, `.gitignore`
- focused: `.\gradlew.bat test --tests "io.atworks.apiintelligence.config.*"` — 성공
- regression: `.\gradlew.bat test` — 성공
- API key 누락 load, env 우선순위, unknown key/invalid value, `toString`/Jackson secret 비노출 검증

## UOW-03 Source Workspace

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence:

- 신규 port/domain: `SourceWorkspacePort`, `SourceWorkspace`, typed source errors
- 신규 adapter: LOCAL read-only/no-op cleanup, HTTP(S) JGit clone/owned cleanup, source-root discovery
- focused: `.\gradlew.bat test --tests "io.atworks.apiintelligence.adapter.source.*"` — 성공
- local fixture에서 source root 탐색과 원본 디렉터리 보존 검증
- Git clone/revision integration은 UOW-13에서 별도 검증 예정

## UOW-04 Spring MVC API 발견

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence:

- 신규 port/adapter: `ApiDiscoveryPort`, `JavaParserApiDiscoveryAdapter`, `MappingAnnotationReader`
- fixture: class-level `/orders` + method-level GET/POST mapping, request binding, response type,
  source location
- focused: `.\gradlew.bat test --tests "io.atworks.apiintelligence.adapter.javaparser.*"` — 성공
- syntax 기반 discovery이며 기존 저장소나 package 이름 하드코딩 없음

## UOW-05 Shallow Code Graph

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence:

- 신규 `CodeGraphPort` 및 `JavaParserCodeGraphAdapter`
- API root/handler, 조건, switch, throw, return, method call node/edge 생성
- focused:
  `.\gradlew.bat test --tests "io.atworks.apiintelligence.adapter.javaparser.JavaParserCodeGraphAdapterTest"` —
  성공
- graph model의 dangling edge 검증과 설정 기반 기본 한도 사용

## UOW-06 Evidence Catalog

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence:

- 신규 `EvidencePort`, `SourceEvidenceAdapter`, `SourceSnippetReader`
- source location의 정확한 line range만 UTF-8로 읽고 Evidence ID/graph node를 연결
- focused:
  `.\gradlew.bat test --tests "io.atworks.apiintelligence.adapter.javaparser.SourceEvidenceAdapterTest"` —
  성공

## UOW-07 Artifact 저장

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence: `RunArtifactPort`, `JsonRunArtifactAdapter`, `JsonRunArtifactAdapterTest`; focused Gradle
test passed.

## UOW-08 OpenAI Adapter

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] Secret leakage verified
- [x] UoW acceptance/DoD met

Evidence: `IntelligenceModelPort`, `OpenAiIntelligenceAdapter`, fake `HttpServer` contract tests
including fenced `json` and object-shaped model output; API key is only sent in Authorization header
and never serialized in results.

## UOW-09 Run Registry

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence: `RunProgressPort`, `AnalysisRunSnapshot`, `InMemoryRunRegistry`; terminal transition test
passed.

## UOW-10 Orchestration

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] API별 실패 격리 verified
- [x] API 10개에서 단일 API 요청 10회 verified
- [x] Integration/regression impact verified
- [x] UoW acceptance/DoD met

Evidence: `ApiAnalysisOrchestrator` invokes `IntelligenceModelPort` once per graph/API and passes
only matching evidence.

## UOW-11 HTTP와 Bootstrap

- [x] Contract/model implemented
- [x] Unit tests implemented
- [x] Focused tests passed
- [x] Loopback/body/origin security verified
- [x] Existing `/api/scan` regression verified
- [x] UoW acceptance/DoD met

Evidence: `/api/intelligence` POST endpoint added to existing loopback web server; full Gradle
regression suite passed.

## UOW-12 UI

- [x] Contract/UI implemented
- [x] UI tests or smoke checks implemented
- [x] Focused verification passed
- [x] XSS/accessibility behavior verified
- [x] Existing Spec Scan UI regression verified
- [x] UoW acceptance/DoD met

Evidence: `static/intelligence.html` provides a separate PoC screen with API cards, intelligence
fields, graph/evidence details, and raw JSON; existing static assets remain unchanged.

## UOW-13 End-to-End

- [x] Full test suite passed
- [x] Independent fixture acceptance passed
- [x] Local and Git source acceptance passed
- [x] RealEstate read-only smoke passed
- [ ] Opt-in live OpenAI verification passed
- [x] Saved JSON artifacts inspected
- [x] Secret leakage scan passed
- [x] Requirements acceptance criteria 17/17 mapped
- [ ] UoW acceptance/DoD met

Evidence: `ApiIntelligenceAnalysisServiceE2ETest` passed for an independent fixture and optional
RealEstate path; fake model endpoint verifies one request per discovered API and JSON artifact paths
are written. `LiveOpenAiOptInTest` provides an explicit environment-gated live check requiring
`OPENAI_API_KEY`; it is skipped when the key is absent.

Live verification command (explicit opt-in):
`$env:OPENAI_API_KEY='...'; .\gradlew.bat test --tests "io.atworks.apiintelligence.application.LiveOpenAiOptInTest"`

## 미완료 및 차단 사항

- 추적 대상 `src/main/resources/application.yml`에 실제 OpenAI key가 잠시 기록된 것을 발견해 빈 값으로 제거하고 source/build
  pattern scan을 통과했다. 해당 키는 폐기·재발급해야 하며 live 검증에는 사용하지 않는다.
