# User Stories

## Epic E-01: GitHub Repository에서 분석 가능한 소스를 확보한다

### Story S-01: Repository 입력과 Source Ingestion
As a PoC Developer, I want GitHub Repository URL을 입력해 분석 대상 Spring 프로젝트를 가져오고 싶다, so that 로컬 사전 준비 없이 분석 파이프라인을 시작할 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given 유효한 GitHub Repository URL이 있을 때, when 사용자가 분석을 시작하면, then 도구는 저장소를 임시 workspace로 가져오고 분석 대상 source root를 식별한다.
- Given 저장소 구조가 일반적인 Maven/Gradle Spring 프로젝트일 때, when source ingestion이 완료되면, then 분석 대상 Java 파일 목록과 source root metadata가 기록된다.
- Given clone 또는 source ingestion 단계가 실패할 때, when 실패가 발생하면, then 실패 원인과 실패 단계가 구조화된 오류로 기록된다.
- Given runtime execution이 금지된 PoC 정책이 있을 때, when ingestion이 수행되면, then 애플리케이션 실행 없이 정적 분석만을 위한 파일 수집으로 끝난다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: GitHub URL 입력 후 source root 식별 성공 테스트, clone 실패 테스트, 비실행 정적 분석 정책 검증
- **N/A Rationale**: N/A
- **Demo Checkpoint**: 저장소 URL 입력 후 임시 workspace 경로와 파일 목록을 화면 또는 로그로 확인한다.

## Epic E-02: Spring 코드에서 API endpoint와 schema를 추출한다

### Story S-02: Spring Endpoint 및 타입 추출
As a PoC Developer, I want Spring Controller에서 endpoint, HTTP method, request type, response type을 추출하고 싶다, so that 이후 validation 조건과 연결 가능한 API skeleton을 만들 수 있다.

### Personas
- P-01 PoC Developer

### Acceptance Criteria
- Given Spring MVC 애노테이션이 있는 Controller가 있을 때, when 정적 분석을 수행하면, then endpoint별 HTTP method와 최종 path가 계산된다.
- Given request parameter와 request body가 섞여 있을 때, when 분석을 수행하면, then request parameter는 HEADER/PATH/QUERY/BODY로 구분된다.
- Given response type이 존재할 때, when endpoint metadata를 조립하면, then response schema는 RESPONSE targetLocation 후보와 연결된다.
- Given request DTO와 response type이 존재할 때, when endpoint metadata를 조립하면, then endpoint와 request/response 타입 정보가 각각 연결된다.
- Given 파싱 실패 또는 타입 해석 실패가 있을 때, when 해당 endpoint를 분석하면, then 실패한 endpoint와 원인이 분리되어 기록된다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: 샘플 Controller 분석 통합 테스트, path/method/type 추출 검증, parser failure 처리 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: endpoint 목록, request targetLocation 분류, response schema 연결 결과를 예시 JSON 또는 로그로 보여준다.

### Story S-03: Annotation 기반 Validation 추출
As a PoC Developer, I want DTO와 파라미터의 Bean Validation 애노테이션을 정형 규칙으로 분류하고 싶다, so that 지원되는 규칙은 바로 `ApiCondition`으로 만들고 미지원 규칙은 후속 정규화 대상으로 보낼 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given 표준 Bean Validation 애노테이션이 DTO 필드에 있을 때, when annotation extractor가 실행되면, then `operator`, `expected`, `targetPath`, `evidence`를 가진 `ApiCondition` 후보가 생성된다.
- Given endpoint와 DTO가 연결돼 있을 때, when annotation 기반 조건을 조립하면, then endpoint별 validation map에 반영된다.
- Given 지원하지 않는 annotation 또는 custom annotation이 있을 때, when 분석을 수행하면, then 해당 결과는 `ApiCondition`으로 바로 승격되지 않고 raw evidence를 보존한 `ValidationCandidate`로 기록된다.
- Given annotation parameter를 해석할 수 없을 때, when 변환에 실패하면, then 실패 이유와 source trace가 남는다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: annotation-to-condition mapping 단위 테스트, unsupported/custom annotation을 ValidationCandidate로 보내는 테스트, endpoint 연결 검증 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: `@NotNull`, `@Size`, `@Pattern` 예시는 `ApiCondition`으로, custom annotation 예시는 `ValidationCandidate`로 분리된 결과를 보여준다.

## Epic E-03: 다단계 validation 후보를 추출한다

### Story S-04: Validator 계층 Candidate 추출
As a PoC Developer, I want custom validator 계층의 validation 근거를 candidate로 추출하고 싶다, so that annotation만으로 설명되지 않는 규칙을 validator 흐름에서 보완할 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given `ConstraintValidator`, Spring `Validator`, `@InitBinder`가 존재할 때, when validator candidate extractor가 실행되면, then endpoint 또는 validator method 단위 candidate가 생성된다.
- Given `errors.rejectValue`, `reject` 호출이 validator 계층에 존재할 때, when extractor가 실행되면, then 관련 field 후보와 evidence가 candidate로 저장된다.
- Given validator가 endpoint에 직접 연결되지 않을 때, when `@InitBinder` 또는 validator binding을 따라 분석하면, then 연결 여부가 기록된다.
- Given custom annotation과 `ConstraintValidator`가 연결될 때, when 분석을 수행하면, then annotation source와 validator source가 함께 trace된다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: custom validator 추출 테스트, `@InitBinder` 연결 테스트, validator bean 추출 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: validator candidate가 evidence와 binding trace와 함께 생성된 JSON을 확인한다.

### Story S-05: Service/Domain Validation Hint 추출 및 증적 보존
As a PoC Developer, I want 서비스/도메인 내부 비즈니스 규칙 힌트를 정적 분석 단계에서 evidence로 수집하고 보존하고 싶다, so that 최종 API Contract에서 AUTH/RESOURCE는 배제하더라도 분석의 완전성을 위해 증적으로 활용할 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given `if` 조건문, `throw new BusinessException`, `ErrorCode` 사용 지점이 있을 때, when service hint extractor가 실행되면, then 관련 snippet, source trace, evidence가 candidate로 저장된다.
- Given endpoint에 직접 연결되지 않는 validation hint가 있을 때, when 메서드 체인을 따라 분석하면, then 연결 여부와 관련 confidence candidate가 함께 기록된다.
- Given 서비스/도메인 hint가 request field와 간접적으로만 연결될 때, when 분석을 수행하면, then 추론 근거가 evidence와 함께 남는다.
- Given 최종 계약 조립 단계가 수행될 때, when 결과 맵을 병합하면, then 수집된 서비스/도메인 힌트 중 AUTH, RESOURCE, DB 존재 여부, optimistic lock 등과 결부되는 조건들은 최종 계약 조건(requestPreconditions, responseAssertions)으로 승격되지 않고 필터링 배제된다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: `BusinessException` hint 추출 테스트, method chain 기반 연결 테스트, raw snippet 보존 검증 및 최종 조립 시 AUTH/RESOURCE 승격 방지 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: service/domain hint candidate가 수집되나, 최종 output인 ApiCondition JSON의 requestPreconditions 및 responseAssertions에서는 배제되는 과정을 검증한다.

### Story S-06: Candidate Chunk Generation과 Invalid 처리
As a PoC Developer, I want 정적 분석 결과를 작은 candidate chunk로 정리하고 invalid 케이스를 초기에 걸러내고 싶다, so that LLM 이전 단계의 책임 경계를 명확히 하고 안전하게 입력을 제어할 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given annotation, validator, service hint candidate가 존재할 때, when chunk generator가 실행되면, then candidate는 endpoint 단위 또는 validator method 단위의 작은 JSON chunk로 분할된다.
- Given chunk가 생성될 때, when metadata를 조립하면, then 모든 chunk는 `candidateId`, source trace, evidence, 관련 field 후보를 가진다.
- Given `candidateId` 충돌, source trace 누락, evidence 누락이 발생할 때, when chunk를 검증하면, then 해당 candidate는 invalid 상태로 분리되고 LLM 대상에서 제외된다.
- Given 여러 분석 source가 하나의 endpoint에 연결될 때, when chunk를 생성하면, then source 종류별 경계가 유지된다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: chunk 생성기 테스트, invalid candidate 분기 테스트, candidateId uniqueness 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: LLM 입력 직전 chunk JSON과 invalid candidate 분리 결과를 함께 보여준다.

## Epic E-04: 후보를 정규화하고 결과를 조립한다

### Story S-07: LLM Normalization과 결과 검증
As a PoC Developer, I want candidate chunk를 LLM에 전달해 validation rule로 정규화하고 싶다, so that 코드 snippet을 구조화된 `ApiCondition`으로 변환할 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given candidate chunk JSON이 있을 때, when LLM normalization을 요청하면, then 응답은 JSON only 형식과 정의된 schema를 따라야 한다.
- Given LLM 응답에 `candidateId`, `operator`, `confidence`가 모두 있을 때, when validator가 실행되면, then 응답은 accepted 상태가 된다.
- Given candidateId 불일치, enum 오류, schema 위반이 발생할 때, when 응답을 검증하면, then rejected 또는 retry 상태로 분기된다.
- Given evidence가 없는 새 규칙을 LLM이 생성할 때, when 결과를 검증하면, then 해당 결과는 수용되지 않는다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit
- **Required Test Evidence**: LLM response parser 테스트, schema validation 테스트, retry/rejected 분기 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: 정상 응답 1건과 schema 실패 응답 1건을 비교해 보여준다.

### Story S-08: ApiCondition/OpenAPI 결과 조립과 테스트 실행 계약서 생성
As a Technical Lead / Architect, I want 요청 전제조건(`requestPreconditions`)과 응답 검증 어설션(`responseAssertions`)으로 개념적으로 분리된 계약 스키마를 OpenAPI와 함께 조립하고 싶다, so that PoC 산출물이 실제 실행 가능한 API 테스트의 오라클로 활용될 수 있는지 검토할 수 있다.

### Personas
- P-01 PoC Developer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given endpoint metadata와 normalized validation 결과가 있을 때, when result assembler가 실행되면, then endpoint별 OpenAPI와 요청/응답 사전/사후 조건이 구조적으로 정렬된 JSON이 함께 생성된다.
- Given `ApiCondition` 결과가 생성될 때, when 결과를 저장하면, then `conditionType` (PRECONDITION | ASSERTION)이 올바르게 구분되고 evidence, confidence, source trace가 보존된다.
- Given 응답 검증 어설션(`responseAssertions`)이 조립될 때, when 결과를 검토하면, then `STATUS`, `HEADER`, `BODY` 레벨의 세부 위치가 엄격히 구분되어 실행 가능한 검증 형태로 정의된다.
- Given 데모 시나리오를 실행할 때, when 결과를 검토하면, then endpoint별 실행 가능한 계약 맵과 테스트 데이터 후보 생성을 설명할 수 있다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: integration
- **Required Test Evidence**: OpenAPI + conditionType 분리 저장 및 STATUS/HEADER/BODY 구조화 동시 생성 통합 테스트, 데모 산출물 점검 체크리스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: 샘플 endpoint 하나를 선택해 OpenAPI path, conditionType이 PRECONDITION 및 ASSERTION(STATUS/HEADER/BODY 포함)으로 구분된 계약 맵, evidence trace를 함께 보여준다.

### Story S-09: API 테스트 실행 및 사후 검증 (오라클 판정)
As an API Test Engineer, I want 요청 실행 조건(`requestPreconditions`)을 사전에 준비하고 API 실행 후 응답 결과(STATUS/HEADER/BODY)를 사전에 정의된 응답 어설션(`responseAssertions`)과 비교해 자동 판정하고 싶다, so that API 테스트 실행의 성공/실패 여부를 신뢰성 있게 판정할 수 있다.

### Personas
- P-03 API Test Engineer
- P-02 Technical Lead / Architect

### Acceptance Criteria
- Given 추출된 `RequestPrecondition` 목록이 있을 때, when 테스트 요청 데이터를 구성하면, then 필수 전제조건에 부합하는 입력값이 식별된다.
- Given API를 실행하고 HTTP 응답이 반환되었을 때, when 응답 검증기(`ResponseAssertionEvaluator`)가 실행되면, then STATUS 코드 일치 여부, HEADER의 필수 값 포함 여부, BODY의 특정 필드 검증 연산(NOT_BLANK 등)이 수행된다.
- Given 실행 불가능한 내부 비즈니스 규칙(AUTH/RESOURCE 등)이 있을 때, when 테스트 오라클을 판정하면, then 해당 불가능한 규칙에 의해 오판정이 발생하지 않도록 이들이 차단 배제된 상태여야 한다.

### Verification Expectations
- **Automation Required**: Yes
- **Expected Test Level**: unit/integration
- **Required Test Evidence**: `ResponseAssertionEvaluator` 단위 테스트, 모의 HTTP 응답(STATUS 200, BODY JSON)을 기반으로 한 어설션 통과/실패 자동 판정 테스트
- **N/A Rationale**: N/A
- **Demo Checkpoint**: 모의 HTTP 응답에 대해 STATUS=200, BODY의 특정 필드가 성공 및 실패로 각각 자동 판정되는 테스트 결과를 시연한다.

## INVEST Review
- **Independent**: ingestion, endpoint 추출, annotation 추출, validator 추출, service hint 추출, chunk 생성, LLM 정규화, 결과 조립, 그리고 응답 어설션 자동 판정 기능을 완전히 분리해 story 경계를 명확히 했다.
- **Negotiable**: JavaParser/OpenRewrite 선택, conditionType 저장 포맷, 검증 엔진 상세 연산자 등 구현 세부는 개방해두었다.
- **Valuable**: 각 story는 개발자, 아키텍트, 테스트 엔지니어가 직접 검토하고 API 실행 테스트에 활용할 수 있는 가치 흐름을 가진다.
- **Estimable**: 각 story는 명확한 입력/출력/검증 포인트를 가져 추정 가능하다.
- **Small**: 기존 큰 단위의 Spec 조립 스토리를 쪼개어 validator 추출, service hint 추출, chunk generation, 이원화 계약 조립, 테스트 검증 판정으로 분리하여 작은 실행 단위로 만들었다.
- **Testable**: 모든 story에 구체적인 acceptance criteria와 실행 가능한 verification expectations를 포함했다.
