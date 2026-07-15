# UoW 00 조사 결과 — 현재 분석 파이프라인

## 현재 실행 흐름

`OpenApiAssemblyService`는 기본적으로 두 분석 경로를 사용한다.

```text
StaticScanResult + ValidationExtractionResult
→ ValidationEvidenceGraphBuilder
→ NormalizationService
→ legacy ApiCondition
```

migration mode가 `LEGACY_ONLY`가 아니면 별도의 신규 경로도 실행한다.

```text
StaticScanResult + RepositorySource
→ RuleOutputMigrationService
→ DefaultFactCodeGraphBuilder
→ DefaultGraphRuleEngine
→ CandidateToOutputAdapter
→ EndpointRuleOutput
```

두 그래프는 서로 이어지는 단일 그래프가 아니라 독립적으로 생성되는 병렬 분석 경로다. `ValidationEvidenceGraph`는 legacy normalization과 service hint 검증에 사용되고, `FactCodeGraph`는 migration output 생성과 비교에 사용된다.

## 주요 producer와 consumer

| 구조 | Producer | Consumer |
|---|---|---|
| `ValidationEvidenceGraph` | `ValidationEvidenceGraphBuilder` | `NormalizationService`, `RuleBasedConditionNormalizer`, graph JSON exporter |
| `FactCodeGraph` | `DefaultFactCodeGraphBuilder` | `DefaultGraphRuleEngine`, `RuleOutputMigrationService` |
| legacy condition | `ValidationExtractionService`, `NormalizationService` | `StructuredSpecExporter`, legacy `ExecutionSpecExporter` |
| 신규 endpoint output | `RuleOutputMigrationService` | new-only `ExecutionSpecExporter`, migration comparator |

## RealEstate 현재 baseline

2026-07-15 기준 scan 결과의 6개 operation은 다음과 같다.

| operationId | method/path | requestPreconditions | responseAssertions | excludedBusinessRules |
|---|---|---:|---:|---:|
| `login` | `POST /api/auth/login` | 0 | 0 | 0 |
| `getProperties` | `GET /api/estate/properties` | 0 | 0 | 0 |
| `getProperty` | `GET /api/estate/properties/{propertyId}` | 0 | 0 | 0 |
| `post` | `POST /api/estate/properties` | 0 | 0 | 0 |
| `put` | `PUT /api/estate/properties/{propertyId}` | 0 | 0 | 0 |
| `remove` | `DELETE /api/estate/properties/{propertyId}` | 0 | 0 | 0 |

관찰된 주요 단절은 다음과 같다.

- `getProperties → PropertyService.getAll` 호출 간선이 생성되지 않는다.
- 생성자와 `super(...)` 호출이 Fact 경로에 포함되지 않는다.
- lambda와 method reference 내부의 도메인 검증 호출이 완전하게 연결되지 않는다.
- `getProperty`의 repository service hint는 탐지됐지만 endpoint-scoped graph evidence 검증에서 거부된다.
- 응답 DTO로의 값 전달과 upstream invariant 전파가 response assertion으로 연결되지 않는다.
- `remove`의 `ResponseEntity.noContent()`가 204 assertion으로 출력되지 않는다.

## 기준 테스트 환경

샌드박스 내부 JDK는 작업 트리 class directory를 compiler classpath로 읽을 때 접근 제한이 발생한다. 동일 Gradle 명령을 승인된 샌드박스 외부 실행으로 수행하면 정상적으로 컴파일 및 테스트된다. 이 환경 차이는 제품 코드 결함으로 취급하지 않는다.

