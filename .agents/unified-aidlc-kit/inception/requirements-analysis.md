# Requirements Analysis (Adaptive)

**역할 가정**: 프로덕트 오너

**적응형 단계**: 항상 실행됨. 상세 수준은 문제 복잡도에 따라 적응적으로 조절.

**적응형 깊이 설명은 [depth-levels.md](../common/depth-levels.md) 참조**

## 사전 조건
- 워크스페이스 탐지 완료 필수
- 리버스 엔지니어링 완료 필수 (브라운필드 프로젝트인 경우)

## 실행 단계

### Step 1: 리버스 엔지니어링 컨텍스트 로드 (해당 시)

**브라운필드 프로젝트인 경우**:
- `aidlc-docs/inception/reverse-engineering/architecture.md` 로드
- `aidlc-docs/inception/reverse-engineering/component-inventory.md` 로드
- `aidlc-docs/inception/reverse-engineering/technology-stack.md` 로드
- 요청 분석 시 기존 시스템 이해를 위해 위 문서들 활용

### Step 2: 사용자 요청 분석 (의도 분석)

#### 2.1 요청 명확도
- **명확**: 구체적이고, 잘 정의되어 있으며, 실행 가능
- **모호**: 일반적이고, 중의적이며, 명확화 필요
- **불완전**: 핵심 정보 누락

#### 2.2 요청 유형
- **신규 기능**: 새로운 기능 추가
- **버그 수정**: 기존 이슈 수정
- **리팩토링**: 코드 구조 개선
- **업그레이드**: 의존성 또는 프레임워크 업데이트
- **마이그레이션**: 다른 기술로 이전
- **개선**: 기존 기능 향상
- **신규 프로젝트**: 처음부터 시작

#### 2.3 초기 범위 추정
- **단일 파일**: 하나의 파일 변경
- **단일 컴포넌트**: 하나의 컴포넌트/패키지 변경
- **다중 컴포넌트**: 여러 컴포넌트에 걸친 변경
- **시스템 전체**: 전체 시스템에 영향을 미치는 변경
- **교차 시스템**: 여러 시스템에 영향을 미치는 변경

#### 2.4 초기 복잡도 추정
- **사소**: 단순하고 직관적인 변경
- **간단**: 명확한 구현 경로
- **보통**: 약간의 복잡성, 여러 고려 사항
- **복잡**: 상당한 복잡성, 많은 고려 사항

### Step 3: 요구사항 깊이 결정

**요청 분석 결과를 기반으로 깊이 결정:**

**최소 깊이** - 다음 경우 사용:
- 요청이 명확하고 간단할 때
- 상세한 요구사항이 불필요할 때
- 기본적인 이해만 문서화하면 될 때

**표준 깊이** - 다음 경우 사용:
- 요청에 명확화가 필요할 때
- 기능적/비기능적 요구사항이 필요할 때
- 일반적인 복잡도일 때

**종합 깊이** - 다음 경우 사용:
- 여러 이해관계자가 있는 복잡한 프로젝트일 때
- 고위험 또는 핵심 시스템일 때
- 추적 가능성이 있는 상세 요구사항이 필요할 때

### Step 4: 현재 요구사항 평가

사용자가 제공한 내용 분석:
   - 의도 선언문 또는 설명 (이미 audit.md에 기록된 내용)
   - 기존 요구사항 문서 (언급된 경우 워크스페이스 검색)
   - 붙여넣기한 내용 또는 파일 참조
   - 비-마크다운 문서를 마크다운 형식으로 변환

### Step 5: 철저한 완전성 분석

**중요**: 포괄적 분석을 사용하여 요구사항 완전성을 평가. 모호하거나 누락된 세부사항이 있을 때는 기본적으로 질문하도록 한다.

**필수**: 아래 모든 영역을 평가하고 불명확한 항목에 대해 질문:
- **기능적 요구사항**: 핵심 기능, 사용자 상호작용, 시스템 동작
- **비기능적 요구사항**: 성능, 보안, 확장성, 사용성
- **사용자 시나리오**: 유스케이스, 사용자 여정, 엣지 케이스, 오류 시나리오
- **비즈니스 컨텍스트**: 목표, 제약 조건, 성공 기준, 이해관계자 요구
- **기술적 컨텍스트**: 통합 포인트, 데이터 요구사항, 시스템 경계
- **품질 속성**: 신뢰성, 유지보수성, 테스트 가능성, 접근성

**의심스러울 때는 질문하라** - 불완전한 요구사항은 잘못된 구현으로 이어진다.

### Step 5.1: 확장 기능 옵트인 프롬프트

**필수**: 워크플로우 시작 시 `extensions/` 하위 디렉토리에서 로드된 모든 `*.opt-in.md` 파일을 스캔하여 `## Opt-In Prompt` 섹션을 확인. 선언된 각 확장 기능에 대해 해당 질문을 Step 6에서 생성하는 명확화 질문 파일에 포함. 각 옵트인 질문은 사용자의 대화 언어로 제시.

답변 수신 후:
1. 각 확장 기능의 활성화 상태를 `aidlc-docs/aidlc-state.md`의 `## Extension Configuration`에 기록:

```markdown
## Extension Configuration
| Extension | Enabled | Decided At |
|---|---|---|
| [Extension Name] | [Yes/No] | Requirements Analysis |
```

2. **지연 규칙 로딩**: 사용자가 옵트인한 각 확장 기능에 대해 전체 규칙 파일을 지금 로드. 규칙 파일은 명명 규칙으로 파생: 옵트인 파일명에서 `.opt-in.md`를 제거하고 `.md`를 추가 (예: `security-baseline.opt-in.md` → `security-baseline.md`). 사용자가 옵트아웃한 확장 기능의 전체 규칙 파일은 로드하지 않음.

### Step 6: 명확화 질문 생성 (선제적 접근)
   - **항상** `aidlc-docs/inception/requirements/requirement-verification-questions.md` 생성 (요구사항이 예외적으로 명확하고 완전한 경우 제외)
   - 누락, 불명확, 모호한 모든 영역에 대해 질문
   - 기능적 요구사항, 비기능적 요구사항, 사용자 시나리오, 비즈니스 컨텍스트에 집중
   - 사용자에게 질문 문서에서 모든 [Answer]: 태그에 직접 답변 요청
   - 객관식 옵션 제시 시:
     - 옵션을 A, B, C, D 등으로 라벨링
     - 옵션이 상호 배타적이며 겹치지 않도록 보장
     - **항상** 사용자 지정 응답 옵션 포함: "X) 기타 (아래 [Answer]: 태그 뒤에 설명해 주세요)"
   - 문서에서 사용자 답변 대기
   - **필수**: 모든 답변에서 모호함을 분석하고 필요시 후속 질문 생성
   - **필수**: 모든 모호함이 해결되거나 사용자가 명시적으로 진행을 요청할 때까지 계속 질문

### ⛔ GATE: 사용자 답변 대기
requirement-verification-questions.md의 모든 질문에 대한 답변이 수신되고 검증될 때까지 Step 7로 진행하지 않음.
질문 파일을 사용자에게 제시하고 정지.

### Step 7: 요구사항 문서 생성
   - **전제 조건**: Step 6 게이트 통과 — 모든 답변 수신 및 분석 완료
   - `aidlc-docs/inception/requirements/requirements.md` 생성
   - 상단에 의도 분석 요약 포함:
     - 사용자 요청
     - 요청 유형
     - 범위 추정
     - 복잡도 추정
   - 기능적 및 비기능적 요구사항 모두 포함
   - 명확화 질문에 대한 사용자 답변 반영
   - 핵심 요구사항의 간략 요약 제공
   - 각 기능 요구사항에는 가능한 경우 `Acceptance Criteria`와
     `Verification Expectations`를 포함한다.
   - `Verification Expectations`에는 자동화 테스트 필요 여부, 예상 테스트
     레벨(unit, integration, contract, e2e, security, performance), 수동
     검증만 가능한 경우의 사유를 기록한다.
   - 요구사항이 API 계약, 데이터 의미, 상태 전이, 보안 정책을 포함하면
     해당 계약 또는 정책을 검증할 테스트 기대값을 명시한다.

### Step 7.1: 의도 영속화 (Intent Permanence)

> **출처**: B체계 Deep Interview Gating 방법론 통합  
> **목적**: 후속 에이전트의 컨텍스트 오염 방지 및 의사결정의 불변 자산화

### Step 7.1: Intent Permanence (specs/ Generation)

After generating the requirements document (Step 7), resolve key decisions into persistent specification files to solidify architectural intent and prevent subsequent context corruption.

#### Enforcement Rules

1. **Spec File Generation**: Create separate spec files for major architectural decisions, technical choices, or policy resolutions.
   - File Path: `specs/deep-interview-[slug].md`
   - `[slug]` is the kebab-case identifier (e.g. `specs/deep-interview-order-validation.md`)

2. **Spec File Structure**: Each spec file must include two mandatory sections:
   - **Decisions**: List of finalized choices and logic selections.
   - **Hard Constraints**: Strict rules that must not be bypassed under any circumstances.

3. **Template Reference**: Use the standard template under `../templates/deep-interview-spec-template.md`.

4. **Permanence Principles**:
   - Hard constraints are immutable rules enforced throughout construction and operations.
   - Subsequent agent sessions must load these files to maintain state alignment.
   - Spec files are treated as immutable assets; modifications require a new deep interview session.

#### Spec File Structure Example

```markdown
# Deep Interview Specification: [Feature Name]

## Decisions
- [Decision 1] e.g., Maintain backward compatibility with the existing validationConditions schema.
- [Decision 2] e.g., Validation logic must be determined by endpoint-scoped graph-backed data.

## Hard Constraints
- [Constraint 1] e.g., ValidationExtractionService must filter Spring MVC default parameters.
- [Constraint 2] e.g., No changes to the existing JSON Path layout when adding new fields.

## Related Requirements
- R-[ID]: [Requirement Title]

## Metadata
- **Date Finalized**: [ISO 8601 UTC Timestamp]
- **Rationale**: [Brief summary of the decision rationale]
```

### Requirement Verification Template

The requirements document can use the following structure based on the required depth.

```markdown
## Requirement R-[ID]: [Name]

### Description
[Description of the requirement]

### Acceptance Criteria
- [Observable condition that must be true]
- [Observable condition that must be true]

### Verification Expectations
- **Automation Required**: [Yes/No]
- **Expected Test Level**: [unit/integration/contract/e2e/security/performance]
- **Required Test Evidence**: [Expected test class, scenario, or assertion outcomes]
- **Manual Verification Rationale**: [Only when automation is not feasible]
- **Goal-Driven Evidence Tracking**: [Target Goal ID in goals.json mapping to this validation]
```

> **Enhancement B — Goal-Driven Evidence Tracking**
>
> A `Goal-Driven Evidence Tracking` field is added to the `Verification Expectations` section in the Requirement Verification Template.
>
> #### Purpose
> Map verification expectations directly to goal IDs in `goals.json` to ensure bi-directional traceability (Requirement ➔ Test Evidence ➔ Goal Status).
>
> #### Enforcement Rules
> 1. Specify the target Goal ID (e.g., `G001`) resolving the requirement under expectations.
> 2. The corresponding goal's `evidence` field in `goals.json` must contain the verified outcome log.
> 3. Schema specifications must refer to `../schemas/goals-schema.md`.
> 4. Generate warnings during Construction if any requirement-to-goal mapping is missing.

### Step 8: 상태 추적 업데이트

`aidlc-docs/aidlc-state.md` 업데이트:

```markdown
## Stage Progress
### 🔵 INCEPTION PHASE
- [x] Workspace Detection
- [x] Reverse Engineering (if applicable)
- [x] Requirements Analysis
```

### Step 9: 기록 및 진행
   - `aidlc-docs/audit.md`에 타임스탬프와 함께 승인 프롬프트 기록
   - 다음 구조의 완료 메시지 제시:
     1. **완료 공지** (필수): 항상 이것으로 시작:

```markdown
# 🔍 Requirements Analysis Complete
```

     2. **AI 요약** (선택): 요구사항의 구조화된 불릿 포인트 요약 제공
        - 형식: "요구사항 분석 결과 [프로젝트 유형/복잡도] 식별:"
        - 핵심 기능 요구사항 나열 (불릿 포인트)
        - 핵심 비기능 요구사항 나열 (불릿 포인트)
        - 관련 시 아키텍처 고려사항 또는 기술 결정 언급
        - 워크플로우 지시 포함 금지 ("검토해 주세요", "알려주세요", "다음 단계로 진행", "진행하기 전에")
        - 사실적이고 내용 중심으로 유지
     3. **포맷된 워크플로우 메시지** (필수): 항상 다음 정확한 형식으로 종료:

```markdown
> **📋 <u>**REVIEW REQUIRED:**</u>**  
> 요구사항 문서를 검토하세요: `aidlc-docs/inception/requirements/requirements.md`



> **🚀 <u>**WHAT'S NEXT?**</u>**
>
> **선택 가능:**
>
> 🔧 **변경 요청** - 검토 결과에 따라 요구사항 수정 요청  
> [사용자 스토리가 생략될 경우 이 옵션 추가:]
> 📝 **사용자 스토리 추가** - **User Stories** 단계 포함 선택 (현재 프로젝트 단순성으로 생략 예정)  
> ✅ **승인 및 계속** - 요구사항 승인 후 **[User Stories/Workflow Planning]**으로 진행

---
```

**참고**: "사용자 스토리 추가" 옵션은 User Stories 단계가 생략될 때만 포함. [User Stories/Workflow Planning]을 실제 다음 단계 이름으로 대체.

   - 진행 전 명시적 사용자 승인 대기
   - 타임스탬프와 함께 승인 응답 기록
   - aidlc-state.md에서 Requirements Analysis 단계 완료 업데이트
