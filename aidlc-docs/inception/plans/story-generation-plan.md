# Story Generation Plan

## Goal
GitHub Repository URL 기반 Spring API Contract 추출 PoC를 사용자 중심 story로 정리하고, 이후 구현과 검증 단계에서 추적 가능한 산출물을 만든다.

## Execution Checklist
- [x] User story breakdown 접근 방식을 확정한다.
- [x] persona 범위를 확정한다.
- [x] story granularity와 grouping 원칙을 확정한다.
- [x] acceptance criteria와 verification expectations 형식을 확정한다.
- [x] `stories.md` 초안을 생성한다.
- [x] `personas.md` 초안을 생성한다.
- [x] story별 INVEST 기준 충족 여부를 검토한다.
- [x] generated stories와 personas를 검토용으로 정리한다.

## Story Breakdown Options

### Option 1: User Journey-Based
- GitHub URL 입력 → 소스 수집 → API 스캔 → validation 추출 → LLM 정규화 → 결과 검토 흐름으로 stories를 나눈다.
- 장점: 데모 흐름과 동일해서 PoC 발표에 유리하다.
- 단점: 내부 설계 관심사와 역할별 책임이 섞일 수 있다.

### Option 2: Feature-Based
- Source Ingestion, Endpoint Extraction, Validation Extraction, LLM Normalization, Result Persistence로 나눈다.
- 장점: 구현 모듈과 연결이 쉽다.
- 단점: 최종 사용자 가치 흐름이 덜 보일 수 있다.

### Option 3: Persona-Based
- PoC Developer, Contract Reviewer, Platform Integrator 같은 persona별 needs로 나눈다.
- 장점: 각 이해관계자의 기대가 선명하다.
- 단점: 기술 모듈 경계와 story 연결이 분산될 수 있다.

### Option 4: Epic-Based
- 상위 epic 아래에 ingestion, extraction, normalization, verification stories를 배치한다.
- 장점: 복잡한 PoC를 계층적으로 관리하기 쉽다.
- 단점: 과도하게 크면 작은 실행 단위를 가리기 쉽다.

### Recommended Approach
- **추천**: Epic-Based + Feature-Based hybrid
- 이유: PoC가 복합적이어서 상위 epic이 필요하고, 실제 구현은 기능 단위로 나누는 편이 계획·검증에 유리하다.

## Planning Questions

## Question 1
어떤 story breakdown 방식을 기본으로 사용할까요?

A) User Journey-Based

B) Feature-Based

C) Persona-Based

D) Epic-Based + Feature-Based hybrid

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: D) Epic-Based + Feature-Based hybrid

상위 epic으로 사용자 가치 흐름을 잡고, 하위 story는 구현과 검증이 가능한 feature 단위로 나눈다. GitHub Repository URL 입력, source ingestion, Spring endpoint extraction, validation extraction, LLM normalization, ApiCondition/OpenAPI output처럼 여러 컴포넌트가 연결되는 PoC에 적합하다.

## Question 2
이번 단계에서 반드시 포함할 persona 범위는 어디까지입니까?

A) PoC를 구현하는 개발자 persona만 포함한다

B) 개발자와 결과를 검토하는 기술 리드/아키텍트까지 포함한다

C) 개발자, 기술 리드/아키텍트, 결과 소비자까지 포함한다

D) 개발자, 기술 리드/아키텍트, 결과 소비자, LLM 검증 담당자까지 포함한다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) 개발자와 결과를 검토하는 기술 리드/아키텍트까지 포함한다

현재 PoC의 핵심 사용자는 구현 개발자이고, 성공 여부를 판단할 사람은 기술 리드/아키텍트다. 결과 소비자나 LLM 검증 담당자까지 확장할 수 있지만, 지금 단계에서는 persona 범위를 과도하게 넓히지 않는다.

## Question 3
story granularity는 어느 정도가 적절합니까?

A) 큰 epic 위주로 적고 세부 구현은 나중에 푼다

B) 구현 모듈별로 5~8개 정도의 중간 크기 story로 나눈다

C) endpoint 추출, validator 추출, LLM 정규화까지 세부 단위로 잘게 나눈다

D) 데모 흐름 기준 큰 story와 기술 기능 기준 작은 story를 함께 둔다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: D) 데모 흐름 기준 큰 story와 기술 기능 기준 작은 story를 함께 둔다

데모 관점에서는 GitHub URL 입력부터 결과 검토까지 큰 흐름이 필요하고, 구현 관점에서는 source ingestion, endpoint extraction, validation extraction, LLM normalization, ApiCondition assembly 같은 작은 실행 단위가 필요하다.

## Question 4
acceptance criteria는 어떤 수준을 원합니까?

A) 핵심 성공 조건만 간단히 적는다

B) Given/When/Then 중심으로 테스트 가능하게 적는다

C) Given/When/Then과 함께 실패 시나리오도 포함한다

D) Given/When/Then, 실패 시나리오, 검증 산출물까지 모두 적는다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: D) Given/When/Then, 실패 시나리오, 검증 산출물까지 모두 적는다

PoC에서도 GitHub clone 실패, parser 실패, LLM 응답 검증 실패, candidateId 불일치 같은 실패 케이스가 중요하다. 이후 workflow-plan과 llm-invocation-plan으로 추적되어야 하므로 acceptance criteria에는 실패 시나리오와 검증 산출물도 포함한다.

## Question 5
verification expectations는 어느 수준까지 구체화할까요?

A) 자동화 필요 여부만 적는다

B) 테스트 레벨(unit/integration 등)까지 적는다

C) 예상 테스트 evidence까지 적는다

D) 테스트 evidence와 데모 확인 포인트까지 적는다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: D) 테스트 evidence와 데모 확인 포인트까지 적는다

요구사항에서 automation 여부, expected test level, required test evidence를 이미 요구하고 있으므로, story에서도 테스트 evidence와 데모 확인 포인트까지 연결해 추적성을 유지한다.

## Answer Analysis
- 답변 간 모순은 없다.
- hybrid breakdown, persona 범위, granularity, acceptance criteria, verification expectations 수준이 모두 일관된다.
- 추가 clarification question은 필요하지 않다.

## Proposed Story Generation Shape
- 상위 Epic은 사용자 가치 흐름 기준으로 구성한다.
- 하위 Story는 Source Ingestion, Endpoint Extraction, Validation Extraction, LLM Normalization, Result Assembly, Demo Verification 같은 feature 단위로 구성한다.
- 각 story에는 Given/When/Then, 실패 시나리오, required test evidence, demo 확인 포인트를 포함한다.

## Mandatory Artifacts
- [x] Generate `aidlc-docs/inception/user-stories/stories.md`
- [x] Generate `aidlc-docs/inception/user-stories/personas.md`
- [x] Ensure stories are INVEST-aligned
- [x] Include acceptance criteria for each story
- [x] Include verification expectations for each story when behavior or outputs change
- [x] Map personas to relevant stories

## Approval Notes
- 모든 `[Answer]:` 항목이 채워져야 generation으로 진행할 수 있다.
- 답변에 모호성이 있으면 clarification 질문 파일을 추가로 만든다.
- 계획 승인이 완료되면 이 문서를 기준으로 `stories.md`와 `personas.md`를 생성한다.
