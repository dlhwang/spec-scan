# FactCodeGraph 통합 구현 계획

## 1. 목적

현재 정적 분석 파이프라인에서 `ValidationEvidenceGraph`와 `FactCodeGraph`가 각각 소스 코드 관계를 표현하고 있음.
두 그래프의 생성 비용과 유지보수 부담을 줄이기 위해 `FactCodeGraph`를 단일 내부 그래프 모델로 사용하도록 통합함.

최종 구조는 아래와 같음.

```text
RepositorySource
    ├─ DefaultFactCodeGraphBuilder
    │   └─ FactGraphBuildResult
    │       └─ GraphRuleEngine
    │           └─ GraphRule
    │               └─ BusinessRuleCandidate + RuleEffect
    │                   ├─ REQUEST_REQUIREMENT → requestPreconditions
    │                   ├─ RESPONSE_GUARANTEE → responseAssertions
    │                   └─ BUSINESS_RESTRICTION → excludedBusinessRules 또는 diagnostic
    ├─ ApiEndpoint.requestBindings
    │   └─ RequestBindingConditionAdapter
    │       └─ 요청 DTO 및 애너테이션 제약
    └─ RuleOutputService
        └─ EndpointRuleOutput
```

`ValidationEvidenceGraph`, `NormalizationService`, `CandidateChunk`, `RuleBasedConditionNormalizer`는 최종 구조에서 제거함.
`validation-evidence-graph.json`이 외부 산출물로 필요하면 내부 도메인 모델을 유지하지 않고 `FactCodeGraph`에서 변환하여 출력함.

## 2. 현재 구조

### ValidationEvidenceGraph 경로

```text
SpringStaticScanService
    └─ ValidationExtractionService
        └─ ValidationEvidenceGraphBuilder
            └─ NormalizationService
                └─ RuleBasedConditionNormalizer
```

주요 역할은 다음과 같음.

- 엔드포인트와 요청 DTO 필드 연결
- 컨트롤러에서 서비스 메서드로 이어지는 호출 관계 표현
- 검증 후보와 조건문, 예외 발생 지점 연결
- 후보 정규화 과정의 도달 가능성 판단
- `validation-evidence-graph.json` 생성

### FactCodeGraph 경로

```text
RuleOutputService
    └─ DefaultFactCodeGraphBuilder
        └─ FactCodeGraph
            └─ GraphRuleEngine
                └─ BusinessRuleCandidate
```

주요 역할은 다음과 같음.

- API 메서드를 루트로 하는 엔드포인트별 코드 그래프 생성
- 메서드 호출, 조건식, 실패 결과 및 반환 흐름 표현
- 그래프 무결성 검증
- 탐색 예산 및 진단 정보 관리
- Java, JDK, Spring 및 Spring Data JPA 규칙 실행

## 3. 통합 원칙

1. `FactCodeGraph`를 유일한 내부 코드 그래프 모델로 사용함.
2. 비즈니스 규칙과 실행 흐름 검증은 `GraphRuleEngine`과 개별 `GraphRule`에서 처리함.
3. DTO 및 애너테이션 기반 입력 제약은 `ApiEndpoint.requestBindings`와 전용 adapter에서 처리함.
4. 외부 JSON 형식은 exporter 또는 adapter에서 변환함.
5. 마이그레이션 중 기존 결과와 신규 결과를 비교할 수 있도록 단계적으로 전환함.
6. 통합 과정에서 세 번째 범용 그래프 모델을 만들지 않음.
7. `ValidationCandidate → CandidateChunk → ApiCondition` 경로를 신규 구조에 그대로 이식하지 않음.
8. `BusinessRuleCategory`는 규칙의 비즈니스 의미, `RuleEffect`는 출력 방향으로 구분함.
9. 모든 `GraphRule`은 생성 candidate의 `RuleEffect`를 명시함.
10. `BUSINESS_RESTRICTION`은 evidence gate와 등록된 template을 통과한 경우에만 `excludedBusinessRules`로 출력함.

## 4. 선행 분석

구현 전 두 그래프의 정보 대응표를 작성해야 함.

| ValidationEvidenceGraph 정보 | FactCodeGraph 대응 | 조치 |
|---|---|---|
| API 엔드포인트 | `API_METHOD` 루트 노드 | 기존 모델 사용 |
| 일반 메서드 | `METHOD` 노드 | 기존 모델 사용 |
| 메서드 호출 | 호출 노드 및 엣지 | 의미와 방향 검증 |
| 조건문 | 조건 관련 `FactNode` | 기존 모델 사용 |
| 예외 및 실패 결과 | 실패 결과 노드 | 기존 모델 사용 |
| DTO 필드 | 그래프 필수 정보 아님 | `RequestBindingConditionAdapter`에서 처리 |
| 요청 바인딩 | `ApiEndpoint.requestBindings` | 기존 endpoint 모델 사용 |
| Bean Validation 애너테이션 | 그래프 필수 정보 아님 | direct condition 또는 전용 adapter에서 처리 |
| 소스 위치 | `FactNode` payload 확인 필요 | 공통 source trace 보장 |
| 서비스 비즈니스 규칙 | 조건 및 실패 노드 | 개별 `GraphRule`로 이전 |

선행 분석 완료 기준은 `GraphNodeType`과 `FactNodeType`, `GraphEdgeType`과 `FactEdgeType`의 모든 값이 대응표에 포함되는 것임.

## 5. 단계별 구현 계획

### 1단계: 기존 정규화 책임 분류

`RuleBasedConditionNormalizer`가 처리하는 모든 분기를 신규 책임 위치로 분류함.

구현 항목:

- `SERVICE_HINT`의 존재성, 버전, 권한 및 상태 규칙을 개별 `GraphRule`로 매핑
- `VALIDATOR` 중 실행 흐름 기반 검증을 개별 `GraphRule`로 매핑
- `CUSTOM_ANNOTATION`과 Bean Validation 제약을 request binding 처리로 매핑
- `NormalizedResult.conditions()`의 실제 소비처와 지원 operator 확인
- rejected candidate 및 warning 중 유지할 진단 정책 확정
- 구조화 분석 JSON에서 `ApiCondition` 계약을 유지할지 결정

예상 영향 파일:

- `analysis/support/RuleBasedConditionNormalizer.java`
- `analysis/application/NormalizationService.java`
- `analysis/support/output/StructuralConditionAdapter.java`
- `analysis/support/StructuredSpecExporter.java`
- `analysis/support/rule/pack/*Rule.java`

### 2단계: 그래프 생성 위치 상향 및 재사용

현재 `RuleOutputService` 내부에서 생성하는 `FactGraphBuildResult`를 분석 파이프라인 상위에서 한 번만 생성함.

구현 항목:

- 저장소 정적 스캔 직후 `FactGraphBuildResult` 생성
- 규칙 평가와 출력 서비스에 동일 인스턴스 전달
- `RuleOutputService` 내부의 `DefaultFactCodeGraphBuilder` 직접 호출 제거
- 그래프 생성 진단을 공통 경고 또는 별도 진단 출력으로 전달

예상 영향 파일:

- `GitExecutionSpecScanService.java`
- `analysis/application/OpenApiAssemblyService.java`
- `analysis/application/RuleOutputService.java`
- 필요 시 그래프 생성을 담당하는 신규 application service

그래프 생성 책임을 별도 서비스로 분리할 경우 단순 위임만 담당하게 하며 신규 그래프 도메인은 만들지 않음.

### 3단계: RuleBasedConditionNormalizer 책임 이전

`RuleBasedConditionNormalizer`를 새 그래프에 연결하지 않고 각 책임을 적절한 확장 지점으로 이전함.

구현 항목:

- repository 존재성 검사를 `SpringDataFindByIdOrElseThrowRule` 또는 신규 existence `GraphRule`로 이전
- 버전 일치 검사를 프로젝트 확장 `GraphRule`로 이전
- 권한 검사를 authorization `GraphRule`로 이전
- 주문 상태 검사를 state precondition `GraphRule`로 이전
- custom validator의 조건과 실패 결과가 `FactCodeGraph`에 표현되는지 보강
- 문자열 `contains()` 기반 판정을 구조적 fact 및 payload 판정으로 교체
- 신규 규칙 결과를 `BusinessRuleCandidate`와 `EndpointRuleOutput`으로 직접 변환
- `RuleEffect`에 `REQUEST_REQUIREMENT`, `RESPONSE_GUARANTEE`, `BUSINESS_RESTRICTION` 정의
- `BusinessRuleCandidate`에 `RuleEffect`를 추가하고 rule engine부터 adapter까지 보존
- 기존 모든 `GraphRule`에 명시적인 effect 지정
- `CandidateToOutputAdapter`의 간접 추론을 effect 기반 분기로 교체
- request, response, excluded 출력 변환 책임을 각각의 adapter로 분리
- `BusinessRuleCategory`로 effect를 자동 추론하지 않도록 차단

예상 영향 파일:

- `analysis/support/rule/pack/*Rule.java`
- `analysis/support/rule/InitialRulePacks.java`
- `analysis/support/fact/DefaultFactCodeGraphBuilder.java`
- `analysis/support/output/CandidateToOutputAdapter.java`
- 신규 `analysis/domain/candidate/RuleEffect.java`
- `analysis/domain/candidate/BusinessRuleCandidate.java`
- 신규 request/response/excluded output adapter
- 관련 candidate 및 evidence 도메인 파일

전환 기간에는 기존 `ApiCondition` 결과와 신규 `EndpointRuleOutput` 결과를 fixture 기반 동등성 테스트로 비교함.

### 4단계: 요청 제약과 출력 소비처 이전

그래프 규칙이 담당하지 않는 요청 스키마 제약을 전용 adapter로 이전함.

구현 항목:

- `CUSTOM_ANNOTATION`과 Bean Validation 제약을 `ApiEndpoint.requestBindings` 또는 direct condition에서 읽음
- `RequestBindingConditionAdapter`가 `NOT_NULL`, `NOT_EMPTY`, `NOT_BLANK`, `EMAIL` 등 지원 정책을 명시함
- `StructuralConditionAdapter`의 `NormalizedResult.conditions()` 의존 제거
- `StructuredSpecExporter`가 graph rule 결과와 request binding 제약을 사용하도록 변경
- 사용되지 않는 operator를 지원하거나 명시적으로 폐기함
- `OpenApiAssemblyService`와 `GitExecutionSpecScanService`에서 `NormalizationService.normalize()` 호출 제거

예상 영향 파일:

- `analysis/support/output/RequestBindingConditionAdapter.java`
- `analysis/support/output/StructuralConditionAdapter.java`
- `analysis/support/StructuredSpecExporter.java`
- `analysis/application/OpenApiAssemblyService.java`
- `GitExecutionSpecScanService.java`

### 5단계: 외부 evidence 산출물 변환

`validation-evidence-graph.json` 형식이 사용자 또는 도구와의 계약이면 호환 exporter를 제공함.

구현 항목:

- `FactGraphBuildResult`를 기존 JSON 스키마로 변환하는 exporter 작성
- 여러 엔드포인트별 `FactCodeGraph`를 단일 산출물로 병합
- 병합 시 node ID 충돌 방지
- 기존 필드명과 edge 방향 유지
- 내부 `ValidationEvidenceGraph` 타입 없이도 동일 JSON을 생성하는 직렬화 테스트 추가

예상 영향 파일:

- `analysis/support/StructuredSpecExporter.java`
- `analysis/application/OpenApiAssemblyService.java`
- 신규 `ValidationEvidenceGraphExporter.java`

외부 호환성이 필요하지 않다면 산출물 이름과 스키마를 `fact-code-graph.json`으로 변경하는 별도 결정이 필요함.

### 6단계: 기존 정규화 및 그래프 제거

모든 소비처 이전과 회귀 검증이 끝난 후 기존 정규화 파이프라인과 그래프를 제거함.

제거 대상:

- `analysis/application/NormalizationService.java`
- `analysis/support/RuleBasedConditionNormalizer.java`
- `analysis/support/CandidateChunkGenerator.java`
- `analysis/support/CandidateChunkValidator.java`
- `analysis/support/GraphContextSelector.java`
- `analysis/domain/CandidateChunk.java`
- `analysis/domain/NormalizedResult.java`
- `analysis/domain/ValidationEvidenceGraph.java`
- `analysis/domain/GraphNode.java`
- `analysis/domain/GraphNodeType.java`
- `analysis/domain/GraphEdge.java`
- `analysis/domain/GraphEdgeType.java`
- `analysis/support/ValidationEvidenceGraphBuilder.java`

단, 다른 기능이 위 타입을 참조하는지 확인한 후 실제 제거 범위를 확정함.

## 6. 테스트 계획

### 단위 테스트

- 엔드포인트 루트 노드 생성 확인
- 메서드 호출과 조건, 실패 결과의 도달 가능성 확인
- 중복 node ID 및 dangling edge 거부 확인
- 탐색 예산 초과 시 진단 생성 확인
- endpoint와 fact graph 매칭 확인
- request binding adapter의 Bean Validation 제약 변환 확인
- 각 기존 `SERVICE_HINT`에 대응하는 `GraphRule` 결과 확인
- 문자열 이름이 비슷하지만 구조적으로 무관한 코드의 오탐 방지 확인
- 기존 모든 rule이 하나의 `RuleEffect`를 명시하는지 확인
- request effect가 `responseAssertions` 또는 `excludedBusinessRules`에 들어가지 않는지 확인
- response effect가 `requestPreconditions`에 들어가지 않는지 확인
- business restriction이 allowlist 및 evidence gate 없이 출력되지 않는지 확인

### 동등성 테스트

대표 샘플별 기존 정규화 결과와 신규 graph rule 및 request binding 결과를 비교함.

- null 및 빈 문자열 거부
- enum 허용값 검증
- `Optional.orElseThrow()` 기반 존재성 검증
- `PasswordEncoder.matches()` 기반 인증 실패
- 위임된 guard 메서드
- DTO Bean Validation 애너테이션
- 전역 예외 처리기와 HTTP 상태 매핑

비교 대상:

- request precondition과 business rule 수
- target location과 target path
- operator, category 및 expected 값
- source evidence
- 엔드포인트별 business rule 출력
- 기존 rejected candidate에 대응하는 신규 diagnostic

### 통합 테스트

- `GitExecutionSpecScanService.scan()` 결과 회귀 테스트
- `scanWithArtifacts()`의 JSON 구조 회귀 테스트
- OpenAPI 및 실행 모델 산출물 스냅샷 테스트
- 동일 저장소 분석 시 그래프를 한 번만 생성하는지 검증
- 임시 작업공간 정리 동작 검증

## 7. 위험 요소 및 대응

### DTO와 애너테이션 정보 손실

DTO와 애너테이션을 `FactCodeGraph`에 억지로 포함하지 않으므로 request binding 경로가 이를 완전히 처리해야 함.
기존 정규화 경로 제거 전에 DTO 필드, 바인딩 위치 및 애너테이션 제약의 동등성 테스트를 통과해야 함.

### 그래프 크기 증가

호출 및 실패 매핑을 모두 포함하면 엔드포인트별 그래프 크기가 커질 수 있음.
탐색 예산, 방문 집합 및 공통 메서드 처리 정책을 유지하고 build diagnostics에 잘린 경로를 기록함.

### 기존 JSON 계약 변경

`validation-evidence-graph.json`을 외부에서 소비 중일 수 있음.
호환 exporter를 먼저 제공하고 소비처 확인 전에는 파일명과 스키마를 변경하지 않음.

### 출력 모델 변화

`ApiCondition`에서 `BusinessRuleCandidate` 또는 `ExecutableCondition`으로 출력 경로가 바뀌면서 조건 누락이 생길 수 있음.
fixture 기반 동등성 테스트와 대표 저장소 회귀 테스트를 통과한 뒤 기존 경로를 제거함.

### 프로젝트 전용 휴리스틱 손실

주문 상태나 권한처럼 특정 도메인에 종속된 판정이 기존 클래스 안에 하드코딩되어 있음.
범용 규칙으로 오인하지 않고 `PROJECT_EXTENSION` 계층의 명시적인 `GraphRule`로 이전함.

### RuleEffect 오분류

`BusinessRuleCategory`만으로 출력 방향을 추론하면 `INVARIANT`나 `EXISTENCE`처럼 문맥에 따라 request와 response가 달라지는 규칙이 오분류될 수 있음.
각 `GraphRule`이 effect를 명시하고 adapter는 effect를 재추론하지 않도록 함.

## 8. 완료 기준

다음 조건을 모두 만족하면 통합 완료로 판단함.

- 분석 요청 한 번당 `FactGraphBuildResult`를 한 번만 생성함
- `RuleOutputService`가 외부에서 전달받은 `FactGraphBuildResult`를 사용함
- 운영 코드에서 `NormalizationService`를 호출하지 않음
- 운영 코드에서 `RuleBasedConditionNormalizer`를 참조하지 않음
- 운영 코드에서 `CandidateChunk`를 생성하지 않음
- 운영 코드에서 `ValidationEvidenceGraphBuilder`를 호출하지 않음
- 운영 코드에서 `ValidationEvidenceGraph`를 참조하지 않음
- 서비스 비즈니스 규칙이 모두 개별 `GraphRule`로 이전됨
- 모든 `BusinessRuleCandidate`가 명시적인 `RuleEffect`를 가짐
- request, response, business restriction이 effect에 따라 상호 배타적으로 분기됨
- `responseAssertions`가 response effect candidate를 수용함
- DTO 및 애너테이션 입력 제약이 request binding 경로에서 생성됨
- 기존 검증 조건 및 business rule 회귀 테스트가 통과함
- `validation-evidence-graph.json` 계약 유지 또는 변경 승인이 완료됨
- 기존 정규화 서비스, chunk 타입, 그래프 도메인 및 builder가 삭제됨
- 전체 컴파일 및 테스트가 통과함

## 9. 권장 작업 순서

각 단계는 독립 커밋 또는 PR로 분리함.

1. 두 그래프 노드 및 edge 대응표 확정
2. `RuleBasedConditionNormalizer`의 모든 분기를 신규 책임 위치에 매핑
3. 서비스 및 validator 실행 흐름 규칙을 개별 `GraphRule`로 이전
4. DTO와 애너테이션 제약을 request binding adapter로 이전
5. 신규 출력과 기존 `ApiCondition` 결과의 동등성 테스트 추가
6. `FactGraphBuildResult` 생성 위치를 상위 파이프라인으로 이동
7. `RuleOutputService`가 전달받은 그래프를 재사용하도록 변경
8. `StructuredSpecExporter`와 OpenAPI 조립 경로를 신규 출력에 연결
9. evidence JSON 호환 exporter 구현
10. `NormalizationService`, `RuleBasedConditionNormalizer` 및 chunk 소비처 제거
11. 기존 `ValidationEvidenceGraph` 모델과 builder 삭제
12. 전체 회귀 테스트 및 성능 비교

한 번에 전체를 교체하기보다 책임 분류, 신규 규칙 구현, 출력 소비처 이전, 레거시 제거 순서로 진행하는 것이 안전함.
