# UoW 05 — 출력 및 Evidence 산출물 이전

## 목적

구조화 JSON, 실행 모델, OpenAPI YAML, evidence artifact가 legacy `NormalizedResult.conditions()`나 `ValidationEvidenceGraph`에 의존하지 않고 신규 graph rule 및 request constraint 결과만 사용하도록 변경한다.

## 선행 조건

- UoW 04 완료

## 작업 범위

- `StructuredSpecExporter`의 `NormalizedResult.conditions()` 의존 제거
- execution spec에 graph rule과 request constraint 결과 반영
- OpenAPI 생성 입력을 신규 execution model로 통일
- `validation-evidence-graph.json` 외부 파일명 계약 유지 여부 결정
- 계약 유지 시 `FactGraphBuildResult` 기반 호환 산출물 제공
- 계약 폐기 또는 신규 계약 전환 시 `fact-code-graph.json` 스키마와 전환 정책 문서화

## 구현 원칙

- 내부 `ValidationEvidenceGraph` 타입을 유지하지 않는다.
- 호환 산출물은 `FactCodeGraph`/`FactGraphBuildResult`의 projection이어야 한다.
- 여러 endpoint graph를 병합할 때 node ID 충돌이 없어야 한다.
- 기존 파일명이나 스키마를 바꿀 때는 외부 소비자 영향 확인 없이 진행하지 않는다.
- snippet 재해석으로 조건을 새로 만들지 않는다. 조건은 graph rule 결과와 request binding 결과에서만 나온다.

## 산출물 정책

현재 구현 정책은 “파일명 호환, 내용 모델 전환”이다.

- `validation-evidence-graph.json` 파일명은 당분간 유지한다.
- 단, 파일 내용은 legacy `ValidationEvidenceGraph`가 아니라 `FactGraphBuildResult` 직렬화 결과다.
- `scanWithArtifacts()` 응답의 `validationEvidenceGraph` 필드도 같은 파일을 읽어 반환한다.
- 이 단계에서는 외부 API 필드명과 파일명을 바꾸지 않는다.

## `fact-code-graph.json` 스키마 전환 정책

`fact-code-graph.json`은 즉시 교체가 아니라 후속 호환성 전환으로 진행한다.

1. 병행 산출 기간
   - 다음 호환성 변경에서 `validation-evidence-graph.json`과 `fact-code-graph.json`을 동시에 생성한다.
   - 두 파일 모두 같은 `FactGraphBuildResult`를 기반으로 한다.
   - `validation-evidence-graph.json`은 deprecated artifact로 표시한다.

2. 신규 스키마 기준
   - 최상위 필드는 `graphs`, `diagnostics`, `metadata`를 기준으로 한다.
   - `graphs[]`는 endpoint 단위 `FactCodeGraph`를 담는다.
   - 각 graph는 `graphId`, `apiMethodNodeId`, `nodes`, `edges`를 포함한다.
   - `nodes[]`는 `id`, `type`, `payload`, `source`, `attributes`를 포함한다.
   - `edges[]`는 `sourceId`, `targetId`, `type`, `evidence`, `attributes`를 포함한다.
   - `diagnostics[]`는 graph build 실패나 누락을 외부에서 추적할 수 있게 유지한다.
   - `metadata`는 schema name, schema version, generatedAt, compatibility note를 포함한다.

3. 버전 정책
   - 최초 신규 파일은 `schemaName = "fact-code-graph"`와 `schemaVersion = "1.0"`으로 시작한다.
   - 필드 추가는 minor 변경으로 허용한다.
   - 필드 삭제, 의미 변경, 타입 변경은 major 변경으로 처리한다.
   - major 변경 전에는 기존 소비자 영향 확인과 migration note가 필요하다.

4. 제거 정책
   - 외부 소비자가 `fact-code-graph.json`으로 전환한 뒤에만 `validation-evidence-graph.json` 생성을 제거한다.
   - 제거 시점에는 `scanWithArtifacts()` 응답 필드도 `validationEvidenceGraph`에서 `factCodeGraph`로 전환하거나, 한 릴리스 동안 두 필드를 병행한다.
   - 제거 커밋에는 문서, 샘플 artifact, regression fixture 갱신을 포함한다.

## 예상 영향 파일

- `analysis/support/StructuredSpecExporter.java`
- `analysis/support/ExecutionSpecExporter.java`
- `analysis/application/OpenApiAssemblyService.java`
- 신규 또는 후속 evidence artifact exporter

## 테스트

- structured JSON 스냅샷
- execution model 스냅샷
- OpenAPI YAML 스냅샷
- evidence artifact 직렬화 및 node/edge 무결성
- 반복 실행 결정성
- 기존 request 조건과 business rule 동등성

## 완료 조건

- output/export 경로가 `NormalizedResult`를 요구하지 않는다.
- 외부 evidence artifact 정책이 코드와 문서에 반영된다.
- `fact-code-graph.json` 전환 정책이 문서화된다.
- 신규 산출물에 snippet 재해석으로 생성된 조건이 없다.
- 모든 golden 및 통합 테스트가 통과한다.
