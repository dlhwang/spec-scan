# Work Unit 05: 정규화 및 출력 마이그레이션

## 목적

신규 `BusinessRuleCandidate`를 API별 실행 명세의 `requestPreconditions`, `responseAssertions`, `excludedBusinessRules`로 안전하게 분류하고, 기존 `ApiCondition` 및 export 결과에 연결한다. 동시에 특정 예제에 고정된 정규화 코드와 근거 없는 기본값 생성을 제거한다.

이 Unit의 핵심은 비즈니스 category를 출력 영역에 일대일로 대응시키는 것이 아니다. 후보가 **언제 평가되는지**, **어떤 값으로 검증되는지**, **API 요청과 응답만으로 기계 실행 가능한지**를 기준으로 출력 위치를 결정하는 것이다.

## 선행 조건

- Unit 02의 `BusinessRuleCandidate`, 세 상태 축, `NormalizedConstraint`와 Evidence 계약을 사용할 수 있다.
- Unit 03의 Rule 실행 결과가 미매칭 후보와 diagnostic을 보존한다.
- Unit 04의 초기 Rule Pack이 `ConstraintKind`, target 상태, source Evidence를 생성한다.
- endpoint의 request binding과 response metadata를 조회할 수 있다.

## 출력 영역 정의

### `requestPreconditions`

API 호출 전에 요청 자체의 값만으로 만족 여부를 판단할 수 있는 실행 가능 조건이다.

대표 대상은 다음과 같다.

- request body 필드의 필수 여부, 형식, 범위와 허용값
- path, query, header, cookie parameter의 값 제약
- 요청값끼리의 관계 중 외부 상태 없이 계산 가능한 조건
- 요청값만으로 판정 가능한 비즈니스 제약

비즈니스 의미를 가진 조건이라는 이유만으로 제외하지 않는다. 요청값만으로 target, operator와 expected를 확정하고 실행할 수 있다면 `requestPreconditions`에 포함할 수 있다.

### `responseAssertions`

API 호출 이후 정상 응답에서 관찰 가능한 값으로 검증할 수 있는 조건이다.

초기 대상은 다음과 같다.

- source metadata에서 확인된 정상 response status
- response body의 값 또는 구조에 대한 조건
- response header에 대한 조건

초기 구현은 endpoint의 대표 정상 응답 시나리오를 대상으로 한다. 향후 여러 `2xx` 또는 오류 응답을 지원할 때는 assertion을 response status 또는 scenario별로 분리할 수 있어야 한다.

정적분석으로 확인하지 않은 status를 기본 assertion으로 생성하지 않는다. 특히 모든 API에 `200`을 자동 추가하지 않는다. OpenAPI response, framework annotation, resolved return metadata처럼 명시적 Evidence가 있는 경우에만 생성한다.

### `excludedBusinessRules`

비즈니스 의미와 근거는 확인됐지만 API 요청과 응답만으로 실행 가능한 condition으로 변환할 수 없는 Rule이다. 여기서 `excluded`는 분석에서 제외했다는 뜻이 아니라 **실행 가능한 request/response condition 승격에서 제외했다**는 뜻이다.

대표 대상은 다음과 같다.

- DB 또는 domain entity의 현재 상태에 의존하는 조건
- 현재 사용자, 권한 정책 또는 인증 저장값에 의존하는 조건
- 재고, 용량, 시간, 외부 시스템 결과처럼 런타임 상태가 필요한 조건
- `ConstraintKind.RUNTIME_DEPENDENT`
- 의미는 해석됐지만 target이나 expected를 실행 가능한 형태로 확정할 수 없는 조건

`excludedBusinessRules`에는 Rule의 의미, 제외 사유, 상태, confidence와 Evidence를 보존한다. 누락된 target이나 expected를 추측해서 채우지 않는다.

### 미해석 후보와의 구분

Rule 의미가 확정된 후보와 미해석 후보를 같은 목록에 섞지 않는다.

- `SemanticStatus.RESOLVED`이지만 실행 불가능하면 `excludedBusinessRules`로 보낸다.
- `SemanticStatus.UNRESOLVED`이면 별도 diagnostics 또는 unresolved 영역에 기록한다.
- `ExtractionStatus.UNSUPPORTED`이면 지원하지 못한 구조와 이유를 diagnostic으로 기록한다.
- 단순 Rule 미매칭 후보를 비즈니스 Rule인 것처럼 `excludedBusinessRules`에 넣지 않는다.

현재 출력 스키마가 unresolved 영역을 제공하지 않으면 비교 보고서나 endpoint diagnostics에 보존하고, 스키마 확장 필요성을 명시한다.

## 분류 결정 규칙

### 결정 순서

adapter는 각 후보를 다음 순서로 평가한다.

1. 후보의 extraction 및 semantic 상태를 확인한다.
2. constraint와 source Evidence가 일관된지 검증한다.
3. target 상태와 endpoint request/response binding을 확인한다.
4. 외부 상태 또는 런타임 값 의존 여부를 확인한다.
5. 호출 전 요청만으로 평가 가능한지 판단한다.
6. 정상 응답에서 관찰 가능한지 판단한다.
7. 실행 가능하지 않지만 의미가 확정된 Rule은 제외 사유와 함께 보존한다.

```text
SemanticStatus가 RESOLVED가 아닌가?
  -> diagnostics / unresolved

요청값만으로 호출 전에 평가 가능한가?
  -> requestPreconditions

정상 호출 후 response에서 관찰 가능한가?
  -> responseAssertions

Rule 의미는 확인됐지만 외부 상태나 런타임 값이 필요한가?
  -> excludedBusinessRules

어느 영역에도 안전하게 배치할 근거가 없는가?
  -> diagnostics / unresolved
```

### 상태 및 constraint 매핑

| 입력 상태 | 추가 조건 | 출력 |
|---|---|---|
| `RESOLVED`, target `RESOLVED`, `INPUT_LITERAL` | target이 request binding에 연결됨 | `requestPreconditions` |
| `RESOLVED`, target `RESOLVED`, `INPUT_TO_DOMAIN` | domain 값 없이 요청만으로 계산 가능함 | `requestPreconditions` |
| `RESOLVED`, target `RESOLVED` | target이 정상 response binding이고 expected에 Evidence가 있음 | `responseAssertions` |
| `RESOLVED`, `RUNTIME_DEPENDENT` | 외부 또는 저장 상태가 필요함 | `excludedBusinessRules` |
| `RESOLVED`, target `UNRESOLVED` | 의미는 확인됐으나 실행 target을 만들 수 없음 | `excludedBusinessRules`와 제외 사유 |
| `RESOLVED`, `CONTROL_FLOW_ONLY` | 실행 가능한 request/response constraint가 없음 | `excludedBusinessRules` |
| semantic `UNRESOLVED` | Rule 의미 미확정 | diagnostics / unresolved |
| extraction `UNSUPPORTED` | 지원 범위 밖 구조 | diagnostics / unsupported |

`ConstraintKind` 하나만으로 최종 영역을 결정하지 않는다. 예를 들어 `INPUT_TO_DOMAIN`은 domain 값이 런타임에만 존재한다면 `excludedBusinessRules`가 되어야 한다.

### 분류 예

```java
if (request.quantity() <= 0) throw failure;
```

request body 값만으로 평가할 수 있으므로 `requestPreconditions`다.

```java
if (request.quantity() > product.stock()) throw failure;
```

현재 재고가 필요하므로 `excludedBusinessRules`다.

```java
if (!passwordEncoder.matches(request.password(), user.password())) throw failure;
```

저장된 인증값과 런타임 계산이 필요하므로 `excludedBusinessRules`다.

```java
@ResponseStatus(HttpStatus.CREATED)
```

명시적 정상 응답 metadata와 Evidence가 있으면 status `201`을 `responseAssertions`로 변환한다.

## 출력 모델 계약

### 실행 가능한 condition

`requestPreconditions`와 `responseAssertions`의 각 항목은 최소 다음 정보를 가져야 한다.

- target location: `BODY`, `PATH`, `QUERY`, `HEADER`, `COOKIE`, `STATUS`, `RESPONSE_BODY`, `RESPONSE_HEADER`
- target path
- operator
- expected 또는 expected source
- source Evidence
- confidence
- 원본 `ruleId` 또는 annotation/source metadata 식별자

target, operator 또는 expected가 해당 연산에 필수인데 Evidence로 확정되지 않으면 실행 가능한 condition을 만들지 않는다.

### 제외된 비즈니스 Rule

`excludedBusinessRules`의 각 항목은 최소 다음 정보를 보존한다.

- `ruleId`와 category
- constraint kind
- semantic, extraction, target 상태
- 제외 사유 code
- 확인된 target 또는 expected source
- Evidence
- confidence

예상값이 없는 런타임 의존 Rule에 빈 문자열이나 임의의 literal을 넣지 않는다.

### 진단 정보

진단 code는 최소 다음 상황을 구분한다.

- `SEMANTIC_UNRESOLVED`
- `STRUCTURE_UNSUPPORTED`
- `TARGET_UNRESOLVED`
- `RUNTIME_DEPENDENT`
- `RESPONSE_METADATA_UNRESOLVED`
- `OUTPUT_SCHEMA_UNSUPPORTED`
- `LEGACY_NEW_CONFLICT`

## 마이그레이션 원칙

- 신규 분석 결과가 준비되기 전 legacy 경로를 삭제하지 않는다.
- 일정 기간 두 경로를 병렬 실행하고 차이를 기록한다.
- legacy와 신규 결과를 조용히 합쳐 중복 출력하지 않는다.
- 신규 모델에서 표현할 수 없는 기존 출력은 근거 없이 재생성하지 않는다.
- 분류 결과가 달라질 때 legacy 호환보다 Evidence 계약을 우선한다.
- 신규 경로는 feature flag 또는 명시적 설정으로 비활성화할 수 있어야 한다.

## 구현 단계

### 1. Candidate-to-output adapter 도입

신규 모델과 기존 `ApiCondition` 사이에 명시적인 adapter를 둔다.

adapter의 책임은 다음으로 제한한다.

- 후보 상태 검증
- constraint kind와 endpoint binding을 출력 location/operator/expected로 변환
- 세 출력 영역 분류
- target 미해석 및 runtime-dependent 후보의 제외 사유 생성
- Evidence와 confidence 전달

adapter가 snippet 문자열을 재분석하거나 `BusinessRuleCategory`만으로 출력 영역을 정해서는 안 된다.

### 2. 응답 metadata adapter 도입

response assertion은 비즈니스 Rule adapter와 별도로 source metadata에서 생성할 수 있다.

- OpenAPI response 정의
- framework status annotation
- resolved response header 및 body schema

metadata 간 충돌이 있으면 임의로 하나를 선택하지 않고 diagnostic을 남긴다.

### 3. 병렬 비교 모드

동일 endpoint에 대해 다음을 기록한다.

- `legacyOnly`
- `newOnly`
- `equivalent`
- `conflicting`
- `unresolvedByNewEngine`

비교 기준은 단순 문자열 전체 일치가 아니라 출력 영역, target location/path, operator, expected, rule ID와 source Evidence를 사용한다.

### 4. 하드코딩 제거

다음 값은 source Evidence 없이 production 결과로 생성되지 않아야 한다.

- `$.currentUser`
- `HAS_CANCELLATION_PERMISSION`
- `orderer or ROLE_ADMIN`
- `$.order.state`
- `PAYMENT_WAITING,PREPARING`
- 모든 endpoint에 적용되는 기본 status `200`

특정 출력 소비자가 이 값을 요구한다면 분석기가 아니라 별도 project extension 또는 명시적 mapping 설정의 책임으로 이동한다.

### 5. Legacy Rule 격리

- `classifyRule`을 legacy 전용 구현으로 격리
- `RuleClass` label parsing 의존 제거
- 신규 결과가 통합 게이트를 통과한 뒤 legacy 비활성화
- 회귀 비교 기간이 끝난 뒤 dead code 제거

legacy 구현의 실제 제거는 Unit 06 품질 게이트 이후 수행한다.

## 테스트 전략

### 분류 매트릭스

- request body, path, query와 header의 실행 가능 조건
- 요청값만 사용하는 비즈니스 제약
- request와 domain 값을 함께 사용하는 런타임 조건
- target 미해석이지만 semantic은 해석된 조건
- semantic 미해석 후보
- 정상 response status, body와 header assertion
- response metadata 미해석 및 충돌
- 같은 Rule의 request/excluded 분류 경계

### 오탐 방지

- 모든 endpoint에 status `200`이 자동 생성되지 않는다.
- runtime-dependent expected가 literal로 출력되지 않는다.
- `$.unknown`과 같은 가짜 target이 생성되지 않는다.
- category가 `AUTHENTICATION` 또는 `STATE_PRECONDITION`이라는 이유만으로 고정 영역에 배치되지 않는다.
- unresolved 후보가 `excludedBusinessRules`로 의미화되지 않는다.

### 회귀 비교

- 신규 경로 비활성화 시 기존 결과가 유지된다.
- 비교 모드가 legacy와 신규 결과를 중복 병합하지 않는다.
- 같은 입력과 설정에서 분류와 비교 보고서가 결정적이다.
- serialization round-trip에서 Evidence와 제외 사유가 손실되지 않는다.

## 주요 확인 대상

- `RuleBasedConditionNormalizer`
- `NormalizationService`
- `ExecutionSpecExporter`
- `OpenApiAssemblyService`
- `ApiCondition`, `ConditionType`, `ConditionLocation`
- request/response output DTO와 serialization
- 관련 regression 및 exporter test

## 변경 예상 파일

- 신규 candidate-to-output adapter와 분류 결과 모델
- response metadata adapter
- `NormalizationService` 신규/legacy 경로 선택과 비교 orchestration
- `ExecutionSpecExporter`의 세 영역 serialization
- legacy/new 비교 보고서
- feature flag 또는 migration configuration
- 분류 매트릭스와 exporter regression test

기존 public output schema 변경이 필요하면 조용히 필드를 바꾸지 않는다. 호환성 영향, migration 방법과 legacy fallback을 별도로 기록한다.

## 완료 기준

- 세 출력 영역의 의미와 결정 규칙이 코드 및 테스트에서 일치한다.
- adapter가 신규 candidate 필드만 사용하고 source snippet을 재분류하지 않는다.
- `ConstraintKind`, 세 상태 축, endpoint binding과 Evidence를 함께 사용해 분류한다.
- 요청값만으로 실행 가능한 비즈니스 조건이 `requestPreconditions`로 출력된다.
- 런타임 및 외부 상태 의존 Rule이 이유와 함께 `excludedBusinessRules`로 보존된다.
- semantic 미해석 후보는 `excludedBusinessRules`와 구분된다.
- response Evidence 없이 기본 status `200`을 생성하지 않는다.
- response body, status와 header assertion을 서로 다른 location으로 표현할 수 있다.
- 예제 도메인 고정값이 production 정규화 코드에서 제거된다.
- legacy/new 차이 보고서가 생성된다.
- 기존 출력 호환을 유지하지 못하는 항목이 명시적으로 문서화된다.
- 신규 경로 비활성화 시 기존 동작으로 안전하게 돌아갈 수 있다.
- legacy 제거는 Unit 06의 품질 게이트 통과 후 수행한다.

## 비목표

- Unit 06 품질 게이트 이전의 legacy 코드 완전 제거
- 모든 오류 응답 시나리오의 assertion 생성
- 외부 DB 또는 서비스 값을 조회해 expected를 채우는 기능
- 테스트 데이터와 사전 상태의 자동 생성
- 사용자 정의 Rule DSL 또는 관리 UI 구현

## 위험과 대응

- 기존 테스트가 하드코딩 출력 자체를 정답으로 요구할 수 있다. 해당 테스트는 삭제하지 말고 legacy regression과 신규 의도 테스트로 분리한다.
- `excludedBusinessRules`가 모든 비즈니스 category의 기본 목적지가 되지 않도록 상태 및 실행 가능성 테스트를 둔다.
- response metadata가 없을 때 호환성을 위해 `200`을 만들고 싶은 유혹을 피하고, 미해석 상태를 명시한다.
- 출력 소비자와 분석 내부 모델을 직접 결합하지 않는다.
