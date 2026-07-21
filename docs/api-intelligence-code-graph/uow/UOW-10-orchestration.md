# UOW-10 Analysis Orchestration과 부분 성공

## Goal

Source → Discovery → API별 Graph → API별 Evidence → API별 OpenAI → Artifact 전체 흐름을 실행하고 API 단위 실패를
격리한다.

## 선행 조건

UOW-03부터 UOW-09까지 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/application/StartAnalysisUseCase.java`
- `src/main/java/io/atworks/apiintelligence/application/GetAnalysisStatusUseCase.java`
- `src/main/java/io/atworks/apiintelligence/application/GetAnalysisResultUseCase.java`
- `src/main/java/io/atworks/apiintelligence/application/AnalysisOrchestrator.java`
- `src/main/java/io/atworks/apiintelligence/application/AnalyzeEndpointService.java`
- `src/main/java/io/atworks/apiintelligence/application/ApiIntelligenceRunExecutor.java`
- application fake/integration 테스트

## 실행 계약

1. active run admission과 run directory를 만든다.
2. workspace를 취득하고 API를 stable order로 발견한다.
3. `apis.json`과 초기 manifest를 저장한다.
4. bounded worker slot만큼 API를 lazy scheduling한다. 모든 task를 한 번에 submit하지 않는다.
5. 각 API에 대해 graph를 만들고 저장한다.
6. 같은 API Evidence를 만들고 저장한다.
7. 같은 API의 graph와 Evidence만 사용해 `IntelligenceModelPort`를 정확히 한 번 호출한다.
8. API가 10개면 LLM 호출을 총 10회 시도한다. 여러 API를 한 호출에 합치지 않는다.
9. 검증 성공 Intelligence 또는 실패 diagnostic을 저장한다.
10. 모든 API 결과를 stable order로 aggregate한다.
11. `result.json`, terminal `run.json` 순서로 최종화한 뒤 registry에 publish한다.
12. clone workspace는 `finally`에서 정리한다.

## 상태와 실패 계약

- active run 1, queued run 0
- 모든 발견 API를 개수 제한 없이 시도하되 동시 worker/OpenAI 호출은 bounded
- graph/evidence 저장이 끝나기 전 OpenAI 호출 금지
- API 하나의 graph/Evidence/OpenAI/저장 실패가 sibling을 취소하지 않음
- 모두 성공 `COMPLETED`, 성공과 실패 혼합 `PARTIAL`
- 취득/발견 불가, API 없음, aggregate/terminal finalization 실패 `FAILED`
- terminal manifest 실패 시 registry가 성공 상태를 노출하지 않음

## 구현 절차

1. port fake로 phase와 artifact 호출 순서를 먼저 테스트한다.
2. API 하나의 `AnalyzeEndpointService`를 구현한다.
3. bounded lazy scheduling run executor를 구현한다.
4. run orchestrator와 start/status/result use case를 연결한다.
5. cleanup과 finalization 실패 처리를 완성한다.

## 테스트와 완료 조건

- 10개 API는 단일-API payload로 model port 10회 호출
- mixed success/timeout/invalid Evidence/write failure
- 모든 API 시도, stable order, concurrency 상한
- graph/evidence write가 LLM보다 먼저 호출됨
- COMPLETED/PARTIAL/FAILED의 counters/artifact/status 일치
- finalization 실패 시 거짓 terminal success 없음

