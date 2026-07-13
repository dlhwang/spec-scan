# 검증 및 어설션 아키텍처 정렬 구현 계획 (Validation & Assertion separation)

이 계획은 기존 `auto-oas` 프로젝트의 설계 산출물과 최신 제품 방향("실행 가능한 API 테스트 계약 모델 및 결과 판정") 간의 불일치를 해소하기 위한 구현 계획서입니다. 기존의 "서비스·도메인 비즈니스 규칙을 최종 validationConditions로 승격"하는 설계 방향을 폐기하고, 요청 전제조건(`requestPreconditions`)과 응답 검증 어설션(`responseAssertions`)으로 개념적으로 분리 및 정렬하기 위한 코드 수정 방안을 기술합니다.

## 범위 및 목표

- **핵심 목표**:
  - API 테스트 실행 흐름에 직접 적용할 수 있는 요청 사전 조건과 응답 사후 검증(Assertion) 체계 구축.
  - 리소스 상태(`RESOURCE`)나 권한 가드(`AUTH`), 내부 데이터 존재 여부 등 실행 불가능한 내부 비즈니스 규칙은 최종 계약서에서 완벽히 제외하여 테스트 오라클 오염 방지.
  - 기존 클래스 자산을 최대한 재활용하여 인터페이스 및 데이터 스키마의 하위 호환성 보장.
- **직접 타깃 파일**:
  - `io.atworks.specscan.analysis.domain.ApiCondition`
  - `io.atworks.specscan.analysis.application.ContractAssemblyService`
  - `io.atworks.specscan.analysis.support.ExecutionSpecExporter`
  - `io.atworks.specscan.analysis.application.ValidationExtractionService`

## 핵심 설계 결정 (심층 인터뷰 결과 반영)

### 1. 계약 모델의 개념적 분리 및 클래스 재활용
- `RequestPrecondition`과 `ResponseAssertion`을 위해 새로운 클래스 구조를 신설하지 않고, 기존의 `ApiCondition` 클래스의 필드 스키마를 최대한 재활용합니다.
- `ApiCondition`에 `conditionType` 필드(값: `PRECONDITION`, `ASSERTION`)를 추가하여, 추출 및 조립 런타임에 이들을 논리적으로 분류할 수 있도록 합니다.

### 2. AUTH, RESOURCE 및 내부 도메인 규칙의 최종 배제 및 증적 보존
- `ValidationExtractionService` 및 정적 분석(evidence graph) 수준에서는 리소스 가드(`order.state` 등), 권한 정보(`currentUser` 등), 존재성 체크 및 낙관적 락 관련 Java AST 분석 흔적(evidence)을 기존과 같이 수집하여 기록으로 유지합니다.
- 단, 최종 JSON 및 OpenAPI 산출물을 조립하는 `ContractAssemblyService` 단계에서 이 조건들을 최종 계약 조건(`requestPreconditions`, `responseAssertions`)으로 승격하지 않고 완벽하게 필터링하여 배제합니다.

### 3. 응답 검증(Assertion) 구조 구체화
- HTTP 응답 검증 대상인 `ASSERTION` 타입의 조건들은 `STATUS`, `HEADER`, `BODY` 레벨로 엄격하게 세분화합니다.
- 이를 통해 테스트 실행기가 API 호출 후 반환된 실제 응답의 상태 코드, 헤더 값, 바디 내 특정 필드 검증(예: NOT_BLANK 등)을 자동으로 평가할 수 있는 구체적인 실행 모델을 제시합니다.

## 핵심 갭 분석 및 수정 방안

### 1. `ApiCondition` 도메인 모델에 분류 체계 부재
- 기존에는 모든 검증 규칙이 `ApiCondition` 단일 타입으로만 묶이고 구분이 없었습니다.
- **수정**: `conditionType` (Enum: `PRECONDITION`, `ASSERTION`) 필드를 추가하고, 빌더 및 변환 로직에 이를 반영합니다.

### 2. `ContractAssemblyService`의 조립 시 필터링 부재
- 기존 분석 파이프라인은 정형/비정형 불문하고 수집된 모든 규칙을 하나의 목록으로 내보냈습니다.
- **수정**: 조립 단계에서 수집된 후보(candidate)를 `conditionType`으로 분류하는 동시에, `AUTH`, `RESOURCE`, 존재성 체크 및 낙관적 락 등과 결부되는 조건들을 제거하는 필터링 로직을 추가합니다.

### 3. `ExecutionSpecExporter`가 단일 포맷으로만 출력
- 이전에는 JSON 출력 시 단일 `validationConditions` 배열 형태로만 결과를 내보냈습니다.
- **수정**: 출력 JSON 포맷을 분리하여 `requestPreconditions`와 `responseAssertions` 객체 구조로 매핑하고, `responseAssertions` 하위의 타깃 위치를 `STATUS`, `HEADER`, `BODY`로 정규화하여 출력하도록 개정합니다.

## 우선순위별 실행 계획

### Phase 1. 도메인 모델 개정 및 conditionType 추가
- **목표**: `ApiCondition` 클래스에 `conditionType` 필드를 안전하게 추가하고 빌드 정합성 확보.
- **작업**:
  - `ApiCondition` 도메인 내에 `conditionType` 추가 및 JPA/JSON 매핑 정의.
  - Bean Validation 애노테이션 추출 시 기본 타입을 `PRECONDITION`으로 매핑.
  - MVC 응답 반환 추출 시 기본 타입을 `ASSERTION`으로 매핑.

### Phase 2. ContractAssemblyService 필터링 및 분류 체계 도입
- **목표**: 조립 단계에서 비즈니스/도메인 규칙을 제외하고 사전/사후 조건을 분류하는 로직 신설.
- **작업**:
  - 수집된 `ValidationCandidate`로부터 조립 시 `AUTH`, `RESOURCE` 등의 위치로 판별되는 노드는 최종 API 계약서에 포함하지 않고 내부 evidence 로그에만 보존.
  - 최종 Contract 모델 빌드 시 `conditionType`에 따라 두 개의 다른 컬렉션(`requestPreconditions`, `responseAssertions`)으로 데이터를 분할 적재.

### Phase 3. Exporter 포맷 개편 및 응답 어설션 세분화
- **목표**: 출력 스키마를 최신 계약 모델에 맞춰 변경하고, 응답 어설션 위치를 STATUS, HEADER, BODY로 정형화.
- **작업**:
  - `ExecutionSpecExporter` 개정: 출력 JSON에 `requestPreconditions` 및 `responseAssertions` 키가 나타나도록 개선.
  - 응답 검증 항목에 대해 `STATUS` (예: 200), `HEADER` (예: Content-Type), `BODY` (예: $.orderNo NOT_BLANK) 등 테스트 실행을 위한 규격을 생성하도록 포맷터 수정.

### Phase 4. 자동 검증 및 회귀 테스트 확장
- **목표**: 구현 결과가 최신 테스트 실행 계약 모델에 정확히 부합하는지 검증.
- **작업**:
  - `ContractAssemblyServiceTest`를 생성하여 필터링 규칙이 제대로 동작하는지 단위 테스트.
  - 샘플 endpoint(예: `/admin/orders/{orderNo}/shipping`)를 분석하여 리소스 상태 검증 규칙(`RESOURCE`)이 최종 어설션 결과에서 완전히 배제되는지 테스트 스냅샷 검증.
  - 모의 HTTP 응답 검증기(`ResponseAssertionEvaluator` 또는 동등 컴포넌트)에 대한 테스트 작성.

## 검증 및 롤백 전략

### 자동 검증
```powershell
./gradlew test
```

### 수동 검증
- 생성된 스냅샷 JSON(`ddd-start2-demp-auto-oas.json` 등)에서 다음 조건이 배제되었는지 검사:
  - `RESOURCE $.order.state` 관련 조건이 사라졌는가?
  - `AUTH $.currentUser` 관련 조건이 사라졌는가?
  - `STATUS`, `HEADER`, `BODY`에 적합한 응답 검증 어설션이 구조화되었는가?
