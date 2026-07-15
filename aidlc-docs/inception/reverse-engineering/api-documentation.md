# API 문서

## REST API

### `GET /` 및 정적 리소스

- classpath의 `/static/index.html`, CSS, JavaScript, JSON, PNG를 제공한다.
- 미존재 리소스는 404, GET 외 method는 405다.

### `POST /api/scan`

- 목적: GitHub 저장소를 clone하여 분석 산출물을 JSON으로 반환.
- 필수 필드: `repositoryUrl`, `projectName`, `baseUrl`.
- 선택 필드: `revisionType`(`branch`, `tag`, `commit`), `revision`.
- 응답: `api-execution-model.json` 최상위 필드와 `validationEvidenceGraph`.
- 오류: 입력 오류 400, 분석 실패 500.
- `projectName`과 `baseUrl`은 현재 필수 검증만 하고 결과에는 사용하지 않는다.

## CLI 계약

- `GitSpecScanMain`: `--projectName=<name> --repositoryUrl=<url> --baseUrl=<url> [--revisionType=branch|tag|commit] [--revision=<value>]`, 실행 JSON을 stdout에 출력. 현재 `projectName`과 `baseUrl`은 필수 검증되지만 `scan(...)` 호출에는 전달되지 않는다.
- `SpecScanDemoRunner`: `[repositoryUrl] [outputPath]`, URL이 없으면 내장 Spring 샘플 분석.
- `SpecScanWebServer`: `[--port=8088]`, 기본 포트 8088.

## 핵심 내부 API

- `GitExecutionSpecScanService.scan(...)`: 실행 명세 JSON 반환.
- `scanWithArtifacts(...)`: assembly 산출물에서 Web 응답 구성.
- `RepositoryIngestionService.ingest(...)`: `RepositorySource` 반환.
- `SpringStaticScanService.scan(...)`: endpoint와 warning 반환.
- `ValidationExtractionService.extract(...)`: condition, candidate, warning 반환.
- `NormalizationService.normalize(...)`: candidate 정규화.
- `RuleOutputMigrationService.migrate(...)`: mode별 migration 결과 반환.
- `OpenApiAssemblyService.assemble(...)`: OpenAPI/분석 파일 생성.
- `RuleEvaluationService`, `RuleQualityGate`, `DeliveryReadinessService`: 품질과 전달 준비 상태 판정.

## 파일 출력 계약

- `openapi.yaml`
- `api-execution-model.json`
- `validation-evidence-graph.json`
- `api-spec-analysis.json`
- migration mode별 비교 및 신규 규칙 출력 artifact
