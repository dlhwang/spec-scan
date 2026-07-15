# UoW 05 — 출력 및 Evidence 산출물 이전

## 목적

구조화 JSON, 실행 모델 및 evidence artifact가 신규 graph rule과 request constraint 결과만 사용하도록 변경함.

## 선행 조건

- UoW 04 완료

## 작업 범위

- `StructuredSpecExporter`의 `NormalizedResult.conditions()` 의존 제거
- execution spec에 graph rule과 request constraint 결과 투영
- OpenAPI 생성 입력을 신규 execution model로 통일
- `validation-evidence-graph.json` 외부 계약 여부에 따른 처리
- 계약 유지 시 `FactGraphBuildResult` 기반 호환 exporter 구현
- 계약 폐기 시 `fact-code-graph.json` 스키마와 전환 정책 문서화

## 구현 원칙

- 내부 `ValidationEvidenceGraph`를 유지한 채 직렬화하지 않음.
- 호환 산출물은 `FactCodeGraph`의 projection이어야 함.
- 여러 endpoint graph 병합 시 node ID 충돌이 없어야 함.
- 기존 파일명이나 스키마 변경은 소비처 확인 없이 수행하지 않음.

## 예상 영향 파일

- `analysis/support/StructuredSpecExporter.java`
- `analysis/support/ExecutionSpecExporter.java`
- `analysis/application/OpenApiAssemblyService.java`
- 신규 evidence artifact exporter

## 테스트

- structured JSON 스냅샷
- execution model 스냅샷
- OpenAPI YAML 스냅샷
- evidence artifact 직렬화 및 node/edge 무결성
- 반복 실행 결정성
- 기존 지원 조건과 business rule 동등성

## 완료 조건

- output/export 경로가 `NormalizedResult`를 요구하지 않음.
- 외부 evidence artifact 정책이 코드와 문서에 반영됨.
- 신규 산출물에 snippet 재해석으로 생성된 조건이 없음.
- 모든 golden 및 통합 테스트가 통과함.

