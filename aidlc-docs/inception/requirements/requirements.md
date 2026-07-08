# Requirements

## Intent Analysis Summary
- **User Request**: `auto-oas`를 참고하되 GitHub Repository URL을 입력으로 받아 Spring 코드 기반 API Spec과 Validation Condition을 추출하는 PoC 가능성을 검증한다.
- **Request Type**: New Project / Enhancement
- **Scope Estimate**: Multiple Components
- **Complexity Estimate**: Complex

## Summary of Confirmed Direction
- 입력은 **GitHub Repository URL**이다.
- 우선 범위는 **Spring 분석 기능**이다.
- 구현 언어는 **Java**다.
- 목표는 원본 `auto-oas`와의 CLI 호환성이 아니라 **코드 기반 API Contract 추출 가능성 검증**이다.
- 결과는 OpenAPI뿐 아니라 **내부 ApiCondition JSON**도 포함해야 한다.
- 다음 단계 산출물에는 **전체 분석 워크플로**와 **LLM 호출 플랜**이 반드시 포함되어야 한다.

## Functional Requirements

## Requirement R-001: GitHub Repository Source Ingestion

### Description
도구는 GitHub Repository URL을 입력으로 받아 대상 저장소를 임시 workspace로 clone하고, Spring 소스 정적 분석에 필요한 루트를 준비해야 한다.

### Acceptance Criteria
- 사용자가 GitHub Repository URL을 입력할 수 있다.
- 도구가 저장소를 임시 디렉터리로 clone하거나 동등한 방식으로 소스를 확보한다.
- 분석 단계는 애플리케이션 실행 없이 정적 분석만 수행한다.
- 수집된 source root와 분석 대상 파일 목록을 추적할 수 있다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 샘플 GitHub 저장소 URL 입력 후 source ingestion 성공, 분석 대상 Java 파일 목록 생성, 런타임 실행 미사용 검증
- **Manual Verification Rationale**: N/A

## Requirement R-002: Spring Endpoint Extraction

### Description
도구는 Spring MVC Controller에서 endpoint, HTTP method, path, request/response type, targetLocation을 추출해야 한다.

### Acceptance Criteria
- `@RestController`, `@Controller`, `@RequestMapping`, `@GetMapping`, `@PostMapping` 등 Spring MVC 애노테이션을 해석할 수 있다.
- endpoint별 HTTP method와 최종 path를 계산할 수 있다.
- request parameter를 HEADER/PATH/QUERY/BODY/RESPONSE 관점에서 구분할 수 있다.
- request/response type 후보를 endpoint와 연결할 수 있다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 샘플 Spring 저장소 분석 결과에서 endpoint 목록, HTTP method, path, request/response type, targetLocation 매핑 확인
- **Manual Verification Rationale**: N/A

## Requirement R-003: Annotation Based Validation Extraction

### Description
도구는 Bean Validation 애노테이션을 읽어 즉시 `ApiCondition` 후보를 생성해야 한다.

### Acceptance Criteria
- `@NotNull`, `@NotBlank`, `@Size`, `@Pattern`, `@Min`, `@Max` 등 표준 Bean Validation 애노테이션을 추출할 수 있다.
- 애노테이션 값을 `operator`, `expected`, `purpose`, `source` 등으로 정규화할 수 있다.
- 각 조건은 target path, source class, source method 또는 line number, evidence를 포함한다.
- 추출 결과는 endpoint와 연결 가능한 형태여야 한다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: annotation-to-condition mapping 단위 테스트와 DTO 샘플 통합 테스트
- **Manual Verification Rationale**: N/A

## Requirement R-004: Validator Bean and InitBinder Candidate Extraction

### Description
도구는 `ConstraintValidator#isValid`, Spring `Validator#validate`, `@InitBinder`, `errors.rejectValue`, `errors.reject` 흐름을 정적 분석으로 찾아 LLM 정규화 후보로 수집해야 한다.

### Acceptance Criteria
- `ConstraintValidator` 구현체와 해당 custom annotation 연결을 추적할 수 있다.
- Spring `Validator` Bean과 `@InitBinder` 연결을 탐색할 수 있다.
- `rejectValue`, `reject` 호출을 validation candidate로 추출할 수 있다.
- 후보는 endpoint 단위 또는 validator method 단위의 작은 chunk로 분리된다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 샘플 프로젝트의 custom validator, validator bean, `@InitBinder` 연결이 candidate JSON으로 추출되는 통합 테스트
- **Manual Verification Rationale**: N/A

## Requirement R-005: Service and Domain Validation Hint Extraction

### Description
도구는 서비스/도메인 로직 내부의 조건문, `BusinessException`, `ErrorCode` 사용 지점에서 validation hint를 추출해야 한다.

### Acceptance Criteria
- `throw new BusinessException(...)` 또는 동등한 예외 패턴을 탐지할 수 있다.
- 조건문 snippet, error code, 관련 field 후보, source trace를 evidence로 저장할 수 있다.
- endpoint에 직접 연결되지 않는 경우에도 관련 메서드 체인 기반으로 후보를 수집할 수 있다.
- annotation 기반, validator 기반과 구분되는 source 분류가 가능하다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 서비스/도메인 예제에서 조건문 evidence, exception evidence, source trace가 포함된 candidate JSON 생성
- **Manual Verification Rationale**: N/A

## Requirement R-006: Candidate Chunk Generation for LLM

### Description
정적 분석 결과는 LLM에 직접 보내지 않고, 작은 JSON chunk로 구성되어야 한다.

### Acceptance Criteria
- 모든 후보는 `candidateId`를 가진다.
- 각 chunk는 endpoint context, evidence, source trace, 관련 target field 후보를 포함한다.
- chunk는 endpoint 단위 또는 validator method 단위로 분할된다.
- evidence가 없는 후보는 LLM 정규화 대상으로 전송하지 않는다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: candidate chunk 생성기 단위 테스트와 샘플 저장소 분석 후 구조 검증
- **Manual Verification Rationale**: N/A

## Requirement R-007: LLM Normalization and Response Validation

### Description
LLM은 추출된 후보를 사람이 읽을 수 있는 validation rule로 정규화하고, 도구는 JSON only 응답을 검증해야 한다.

### Acceptance Criteria
- LLM 입력 schema와 출력 schema가 정의된다.
- 출력에는 `candidateId`, `operator`, `confidence`가 필수다.
- JSON schema validation, enum validation, candidateId validation을 수행한다.
- 실패 응답은 retry 또는 rejected 상태로 분기한다.
- evidence 없는 새로운 rule 생성은 허용하지 않는다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: LLM 응답 파서/validator 단위 테스트와 정상/실패/재시도 케이스 검증
- **Manual Verification Rationale**: N/A

## Requirement R-008: ApiCondition Assembly

### Description
정적 분석 결과와 LLM 정규화 결과를 합쳐 endpoint별 `ApiCondition` 모델을 조립해야 한다.

### Acceptance Criteria
- `ApiCondition`은 `targetLocation`, `targetPath`, `expected`, `operator`, `purpose`, `source`, `confidence`, `errorCode`, `sourceClass`, `sourceMethod`, `lineNumber`, `evidence`, `llmReason`, `llmGenerated`, `active`를 저장할 수 있다.
- evidence source와 normalized result를 구분해 저장할 수 있다.
- endpoint별 condition 조회가 가능하다.
- HEADER/PATH/QUERY/BODY/RESPONSE 위치 구분이 가능하다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: `ApiCondition` assembler 단위 테스트와 endpoint별 JSON 스냅샷 검증
- **Manual Verification Rationale**: N/A

## Requirement R-009: OpenAPI and Internal JSON Output

### Description
PoC 결과물은 OpenAPI와 ApiCondition JSON을 함께 제공해야 한다.

### Acceptance Criteria
- endpoint 기준 OpenAPI 결과를 확인할 수 있다.
- endpoint별 validation map 또는 동등한 ApiCondition 결과를 조회할 수 있다.
- 내부 JSON에는 evidence, confidence, source trace가 포함된다.
- 향후 JSON/DB 저장 확장이 가능한 구조여야 한다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 샘플 저장소 분석 후 OpenAPI 파일과 ApiCondition JSON 파일이 함께 생성되고 구조가 유효함을 검증
- **Manual Verification Rationale**: N/A

## Requirement R-010: Workflow Design and LLM Call Plan

### Description
구현에 앞서 `Source Ingestion → Static Scan → Candidate Extraction → LLM Normalization → JSON Validation → ApiCondition 저장`까지의 전체 워크플로와 LLM 호출 플랜을 문서화해야 한다.

### Acceptance Criteria
- 전체 처리 파이프라인이 단계별로 문서화된다.
- LLM 호출 단위, prompt input/output schema, 응답 파싱, 실패 처리, retry 정책, 캐싱 전략이 포함된다.
- 정적 분석기와 LLM의 책임 경계가 명확하다.
- 데모 단계에서 endpoint별 validation map과 테스트 데이터 후보 생성 가능성을 설명할 수 있다.

### Verification Expectations
- **Automation Required**: No
- **Expected Test Level**: integration
- **Required Test Evidence**: 워크플로 설계 문서와 LLM 호출 플랜 문서
- **Manual Verification Rationale**: 설계 산출물 검토가 필요하다.

## Non Functional Requirements
- **NFR-001 정확도 우선**: 원본과 동일한 CLI보다 Spring API/validation 추출 가능성 증명이 우선이다.
- **NFR-002 정적 분석 중심**: 외부 저장소를 가져와도 임의 코드 실행 없이 정적 분석만 수행한다.
- **NFR-003 확장 가능성**: Annotation 기반에서 validator bean, custom validator, service hint로 확장 가능한 구조를 가진다.
- **NFR-004 추적 가능성**: 모든 결과는 evidence, confidence, source trace를 유지해야 한다.
- **NFR-005 유지보수성**: Java 기반 모듈 구조로 작성하며 JavaParser/OpenRewrite 교체 가능성을 고려한다.
- **NFR-006 실패 격리**: Git clone 실패, parser 실패, LLM schema validation 실패를 독립 처리한다.
- **NFR-007 테스트 전략**: partial PBT를 적용하며 annotation mapping, operator conversion, JSON serialization/deserialization, OpenAPI transformation 같은 순수 함수 영역에 우선 적용한다.

## Sample Project Expectations
- 샘플 프로젝트는 GitHub Repository 형태로 준비한다.
- 다음 요소를 포함해야 한다:
  - Controller
  - Request DTO
  - Bean Validation annotation
  - Custom ConstraintValidator
  - Spring Validator Bean
  - `@InitBinder`
  - Service BusinessException

## PBT Compliance
- **PBT-02 Round-Trip Properties**: 적용 대상. JSON serialization/deserialization, OpenAPI 변환 결과 검증에 사용한다.
- **PBT-03 Invariant Properties**: 적용 대상. annotation-to-condition 매핑과 operator 변환 결과 불변식에 적용한다.
- **PBT-07 Generator Quality**: 적용 대상. candidate JSON, ApiCondition, 변환 결과용 도메인 generator를 정의한다.
- **PBT-08 Shrinking and Reproducibility**: 적용 대상. seed 기반 재현과 shrinking을 지원하는 프레임워크를 선택한다.
- **PBT-09 Framework Selection**: 적용 대상. Java 구현 기준 `jqwik`를 우선 검토 대상으로 둔다.
- **Other PBT Rules**: partial enforcement 모드에서는 advisory 또는 N/A로 처리한다.

## Extension Configuration Decision
| Extension | Enabled | Decided At |
|---|---|---|
| Security Baseline | No | Requirements Analysis |
| Property-Based Testing | Partial | Requirements Analysis |
