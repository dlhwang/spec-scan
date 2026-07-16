# UoW 01 — 정규화 책임 및 기준선 고정

## 목적

레거시 정규화 파이프라인이 실제로 생성하고 소비하는 결과를 고정하여 후속 제거 작업의 회귀 기준을 만듦.

## 작업 범위

- `RuleBasedConditionNormalizer`의 모든 분기를 책임별로 분류
- `CUSTOM_ANNOTATION`, `VALIDATOR`, `SERVICE_HINT` fixture 작성
- `NormalizedResult.conditions()`의 실제 소비처 확인
- `StructuralConditionAdapter`가 지원하는 operator 목록 확인
- `CandidateToOutputAdapter`의 request/excluded 간접 분류 조건 특성화
- `ResponseMetadataAdapter`와 `ResponseInvariantAdapter`의 response 생성 조건 특성화
- 기존 `BusinessRuleCandidate`가 복수 출력 경로에서 재사용되는 사례 확인
- rejected candidate, invalid chunk, warning의 외부 노출 여부 확인
- `validation-evidence-graph.json`의 외부 계약 여부 기록

## 산출물

- 기존 입력별 `ApiCondition` 및 경고 특성화 테스트
- 기존 결과와 신규 결과를 비교할 fixture 목록
- 각 분기의 이전 대상 표
- 기존 rule별 `BusinessRuleCategory`, 목표 `RuleEffect`, 최종 출력 표

## 현재 소비처

| 대상 | 현재 소비 방식 | 이전 방향 |
|---|---|---|
| `GitExecutionSpecScanService` | `NormalizationService.normalize(...)` 결과를 `RuleOutputService.generate(...)`와 OpenAPI 조립에 전달 | 상위 pipeline에서 `FactGraphBuildResult`와 request constraint를 생성해 전달 |
| `OpenApiAssemblyService` | `NormalizedResult.conditions()`를 OpenAPI 조립 입력으로 사용 | graph rule output과 request binding output만 사용 |
| `RuleOutputService` | `RuleOutputService` 내부에서 `FactCodeGraph`를 다시 생성하고 `NormalizedResult.conditions()`를 `StructuralConditionAdapter`에 전달 | 이미 생성된 `FactGraphBuildResult` 재사용 |
| `StructuralConditionAdapter` | `ApiCondition` 중 `NOT_NULL`, `NOT_EMPTY`만 request precondition으로 승격 | request binding adapter가 직접 생성 |

## 현재 정규화 분기

| sourceType | 현재 분기 | 생성 조건 | 이전 대상 |
|---|---|---|---|
| `CUSTOM_ANNOTATION` | evidence 문자열에 `Email`, `NotNull`, `NotBlank`, `NotEmpty` 포함 여부 확인 | `EMAIL`, `NOT_NULL`, `NOT_BLANK`, `NOT_EMPTY` `ApiCondition` | request binding adapter |
| `VALIDATOR` | evidence 문자열에 validator API, null check, false return 포함 여부 확인 | `EMAIL`, `GREATER_THAN`, `REQUIRED`, `VALIDATION_LOGIC` `ApiCondition` | request binding adapter 또는 개별 `GraphRule` |
| `SERVICE_HINT` existence | reachable graph rule + `findById`, `orElseThrow` 계열 문자열 | `EXISTS_IN_REPOSITORY` | `GraphRule` |
| `SERVICE_HINT` version | reachable `VERSION_CHECK` + query `version` binding | `OPTIMISTIC_LOCK_MATCH` | `GraphRule` |
| `SERVICE_HINT` permission | reachable `PERMISSION_CHECK` + permission 문자열 | `HAS_CANCELLATION_PERMISSION` | `GraphRule` |
| `SERVICE_HINT` state | reachable guard + `isNotYetShipped` 또는 상태 enum 문자열 | `STATE_IN` | `GraphRule` |
| graph-derived state | `SERVICE_HINT` chunk이고 endpoint에서 state guard node 도달 가능 | `STATE_IN` 추가 생성 | `GraphRule` |

## 현재 출력 분류

| producer | 현재 출력 | 분류 기준 | 후속 변경 |
|---|---|---|---|
| `CandidateToOutputAdapter` | `requestPreconditions` | resolved candidate + executable constraint + request binding target | `RuleEffect.REQUEST_REQUIREMENT`만 허용 |
| `CandidateToOutputAdapter` | `excludedBusinessRules` | runtime/control-flow/input-domain/null constraint 또는 request target 미해결 | `RuleEffect.BUSINESS_RESTRICTION` + allowlist/evidence gate |
| `CandidateToOutputAdapter` | `responseAssertions` | 항상 빈 배열 | `RuleEffect.RESPONSE_GUARANTEE` 전용 adapter |
| `ResponseMetadataAdapter` | `responseAssertions` | explicit status, noContent, explicit header | 유지하되 graph evidence 사용 |
| `ResponseInvariantAdapter` | `responseAssertions` | response field로 흐르는 `NOT_NULL`, `NOT_EMPTY` candidate | response effect candidate로 통합 |

| 기존 책임 | 이전 대상 |
|---|---|
| SERVICE_HINT 존재성·버전·권한·상태 | 개별 `GraphRule` |
| 실행 흐름 기반 custom validator | 개별 `GraphRule` |
| DTO 및 Bean Validation 제약 | request binding adapter |
| 구조화 JSON 조건 출력 | 신규 output projection |
| rejected candidate 경고 | graph diagnostic 또는 명시적 폐기 |

`RuleEffect` 기준은 아래와 같이 고정함.

| RuleEffect | 최종 출력 |
|---|---|
| `REQUEST_REQUIREMENT` | `requestPreconditions` |
| `RESPONSE_GUARANTEE` | `responseAssertions` |
| `BUSINESS_RESTRICTION` | evidence gate 통과 시 `excludedBusinessRules`, 아니면 diagnostic |

`BusinessRuleCategory`에서 `RuleEffect`를 자동 추론하지 않음.

## 기존 rule별 목표 effect

| rule | 현재 category | 목표 effect | 최종 출력 |
|---|---|---|---|
| `SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW` | `EXISTENCE` | `BUSINESS_RESTRICTION` | `excludedBusinessRules` |
| `JDK_OPTIONAL_LOOKUP_FAILURE` | `EXISTENCE` | `BUSINESS_RESTRICTION` | `excludedBusinessRules` |
| `SPRING_SECURITY_PASSWORD_MATCH_FAILURE` | `AUTHENTICATION` | `BUSINESS_RESTRICTION` | `excludedBusinessRules` |
| `JAVA_AUTHORIZATION_GUARD_CALL` | `AUTHORIZATION` | `BUSINESS_RESTRICTION` | `excludedBusinessRules` |
| `STATE_PRECONDITION` 계열 guard | `STATE_PRECONDITION` | `REQUEST_REQUIREMENT` 또는 `BUSINESS_RESTRICTION`를 rule별 명시 | request 또는 excluded |
| response metadata | 해당 없음 | `RESPONSE_GUARANTEE` | `responseAssertions` |
| response invariant | `INVARIANT` | `RESPONSE_GUARANTEE` | `responseAssertions` |

## 외부 노출 기준

| 항목 | 현재 노출 |
|---|---|
| rejected candidate | `NormalizedResult.rejected()`에 남고 일부 pipeline에서 warning 근거로 사용 |
| invalid chunk | `NormalizedResult.invalidChunks()`에 남음 |
| service hint warning | `NormalizedResult.warnings()`로 노출 |
| `validation-evidence-graph.json` | artifact 경로에서 생성될 수 있으므로 UoW 05에서 계약 유지 여부 확정 필요 |

## 비범위

- production 경로 변경
- 레거시 클래스 삭제
- 신규 규칙 구현

## 테스트

- source type별 후보 grouping 특성화
- endpoint operation key 매칭 특성화
- 주요 operator와 target path 스냅샷
- 서비스 힌트의 reachable, ambiguous, rejected 사례
- 기존 rule별 request, response, excluded 실제 분류 결과
- 동일 candidate의 의도하지 않은 복수 출력 여부
- 반복 실행 결과 결정성

## 완료 조건

- `RuleBasedConditionNormalizer`의 모든 분기에 이전 대상이 지정됨.
- 정규화 결과를 사용하는 모든 production 소비처가 문서화됨.
- 모든 기존 `GraphRule`에 목표 `RuleEffect`가 지정됨.
- 세 출력 배열의 기존 producer와 분류 기준이 문서화됨.
- 후속 UoW에서 비교 가능한 fixture와 assertion이 존재함.
- production 동작 변화가 없음.
