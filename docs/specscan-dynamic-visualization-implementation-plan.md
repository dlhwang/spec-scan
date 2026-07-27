# SpecScan 정적 분석 결과 시각화 패키지 구현 계획

## 1. 목적

이 기능은 운영형 분석 시스템이 아니라 SpecScan이 실제 프로젝트를 어떻게 분석하는지 사람들에게
보여주기 위한 시연 도구다. Git URL 분석이 끝날 때 HTML이 사용할 JSON 파일을 함께 생성하고,
HTML은 생성된 정적 JSON만 읽어 기존 6개 탭을 렌더링한다.

## 2. 최종 사용 흐름

```text
Git URL로 SpecScan 실행
  -> 기존 OpenAPI/실행 스펙 산출물 생성
  -> visualization 폴더에 탭별 JSON 생성
  -> visualization/index.html 열기
  -> HTML이 manifest.json과 tabs/*.json을 읽어 렌더링
```

화면은 분석 API를 호출하지 않는다. DB, Job Store, polling, 별도 결과 조회 API도 만들지 않는다.

## 3. 생성 파일

```text
visualization/
  index.html
  manifest.json
  tabs/
    01-source.json
    02-pipeline.json
    03-graph.json
    04-candidates.json
    05-rules.json
    06-final-spec.json
```

- `manifest.json`: 저장소 정보, 생성 시각, endpoint 목록, 탭 파일 경로
- `01-source.json`: endpoint와 관련 소스 파일, source range, highlight ID
- `02-pipeline.json`: 스캔/그래프/후보/출력 단계별 수량과 진단
- `03-graph.json`: endpoint별 FactCodeGraph node/edge
- `04-candidates.json`: direct condition과 validation candidate
- `05-rules.json`: endpoint별 최종 rule output, 제외 rule, diagnostics
- `06-final-spec.json`: 기존 `api-execution-model.json`과 동일한 최종 실행 스펙

모든 파일은 UTF-8 JSON이며 빈 데이터는 `null`이 아닌 빈 배열을 사용한다.

## 4. 구현 방향

### 4.1 Artifact exporter

`OpenApiAssemblyService`가 이미 분석의 모든 핵심 입력과 최종 rule output을 보유하는 시점에
`PipelineVisualizationArtifactExporter`를 호출한다. 분석을 다시 실행하지 않고 기존 객체를 재사용한다.

### 4.2 엔드포인트 연결

각 JSON은 `OperationKey`를 공통 키로 사용한다. HTML에서 endpoint를 선택하면 동일한 operation key의
source, graph, candidate, rule, final spec만 표시한다.

### 4.3 HTML

현재 `code_pipeline_visualization.html`의 디자인과 D3 그래프 코드는 유지한다. 하드코딩된 DOM과
`nodesData`/`edgesData`만 JSON renderer로 교체한다. HTML에는 분석 실행 폼을 넣지 않는다.

### 4.4 실행 방법

브라우저는 보안상 `file://` HTML에서 로컬 JSON `fetch()`를 차단할 수 있으므로 생성 폴더에서 단순
정적 파일 서버만 실행한다. 예: `python -m http.server`. 이 서버는 분석 기능이나 API를 제공하지 않는다.

## 5. 구현 순서

1. 탭 JSON 스키마와 exporter 작성
2. `OpenApiAssemblyService` 산출 과정에 exporter 연결
3. exporter 단위 테스트 및 기존 테스트 실행
4. 기존 HTML을 `manifest.json` 기반 renderer로 전환
5. 실제 Git 저장소 분석 결과로 0/1/N endpoint와 큰 graph 검증

## 6. 이번 구현의 완료 기준

- 기존 scan 실행 한 번으로 `visualization/manifest.json`과 탭 JSON 6개가 생성된다.
- 기존 `openapi.yaml`, `api-spec-analysis.json`, `api-execution-model.json`,
  `validation-evidence-graph.json` 생성 동작은 바뀌지 않는다.
- 탭 JSON은 endpoint별 실제 분석 데이터와 diagnostics를 포함한다.
- exporter 테스트가 파일 존재, UTF-8 JSON 파싱, operation key 연결을 검증한다.
- 이후 HTML은 분석 엔진과 무관하게 생성 JSON만으로 화면을 렌더링할 수 있다.

## 7. 후속 보강

현재 엔진이 보존하지 않는 classifier별 미매칭 사유까지 보여줘야 할 때만 선택적 trace를 추가한다.
시연에 필요한 정보가 기존 graph, candidate, diagnostics, final output으로 충분하면 trace 시스템은 만들지 않는다.
