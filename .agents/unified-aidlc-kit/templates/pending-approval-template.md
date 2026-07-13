# [Feature Name] 최종 개발 및 검증 계획서

> **문서 유형**: 합의 기반 최종 기획서 (Pending Approval)
> **파일 경로**: `plans/ralplan/[UUID]/pending-approval.md`
> **생성 시점**: AI-DLC 워크플로우 02단계 (Consensus Planning) 교차 검증 완료 후
> **선행 조건**: Deep Interview 명세 확정 (`specs/deep-interview-[slug].md` 존재)
> **관련 문서**: [02_CONSENSUS_PLANNING.md](../../agent-docs/workflow-plans/02_CONSENSUS_PLANNING.md) · [인터뷰 명세 템플릿](./deep-interview-spec-template.md) · [목표 스키마](../schemas/goals-schema.md)

---

## 사용 안내

이 템플릿은 Planner → Critic → Architect → Intent Reconciliation 4단계 교차 검증을 마친 최종 기획안을 기록하기 위한 Fill-in 템플릿입니다.

- `[placeholder]` 표기된 부분을 실제 기획 내용으로 대체하십시오.
- 이 문서가 확정(승인)되기 전에는 어떠한 코드 수정도 허용되지 않습니다.
- 승인된 기획서는 Goal-Driven Execution 단계에서 Goal Tree로 변환됩니다.

---

## 아키텍처 의사결정 기록 (ADR)

### 결정사항 (Decision)

- [선택한 설계 사양 요약 작성. 무엇을 어떻게 구현할 것인지 한 문장으로 기술.]

### 결정 동기 (Drivers)

1. [해당 설계가 강제되는 배경 기술 요건 1]
2. [해당 설계가 강제되는 배경 기술 요건 2]
3. [해당 설계가 강제되는 배경 기술 요건 3]

### 기각된 대안 분석 (Alternatives Considered)

- **Option A (선택됨):** [선택된 설계 내용과 장점]
  - 장점: [구체적 장점]
  - 단점: [수용 가능한 트레이드오프]

- **Option B (기각됨):** [기각된 설계 내용]
  - 기각 사유: [구체적 기각 사유 기술]

- **Option C (기각됨):** [기각된 설계 내용]
  - 기각 사유: [구체적 기각 사유 기술]

> [!NOTE]
> 기각된 대안은 최소 1개 이상 기록되어야 합니다. 대안 분석이 없는 기획서는 Critic 단계에서 반려됩니다.

---

## Final Plan

### 수정 대상 파일별 상세 구현 사양 (File Plan)

1. **`[src/main/java/path/to/TargetFile1.java]`**
   - [구현해야 할 구체적인 로직 변경 사항 기술]
   - [변경의 전/후 비교 (Intent Diff) 요약]

2. **`[src/main/java/path/to/TargetFile2.java]`**
   - [구현해야 할 구체적인 로직 변경 사항 기술]
   - [변경의 전/후 비교 (Intent Diff) 요약]

3. **`[src/test/java/path/to/TestFile.java]`**
   - [추가/수정해야 할 테스트 케이스 기술]

> [!IMPORTANT]
> 파일 계획에 기술되지 않은 파일의 수정은 Goal-Driven Execution 단계에서 허용되지 않습니다. 추가 파일 수정이 필요한 경우 기획서를 재승인받아야 합니다.

---

## 검증 계획 (Verification Plan)

### 단위 테스트 (Unit Test)

| 대상 테스트 클래스 | Assertion 사양 | 기대 결과 |
| :--- | :--- | :--- |
| `[TestClassName1]` | [검증할 조건과 어설션 내용] | [PASS 조건 기술] |
| `[TestClassName2]` | [검증할 조건과 어설션 내용] | [PASS 조건 기술] |

### 어셈블리 테스트 (Assembly Test)

- **빌드 정합성**: [전체 프로젝트 빌드 성공 여부 검증 방식 기술]
- **통합 검증**: [컴포넌트 간 연동 정합성 검증 방식 기술]

### 회귀 테스트 (Regression Test)

- **대상 시나리오**: [기존 acceptance 시나리오 목록]
- **통과 기준**: [회귀 테스트 전체 통과 여부 판정 기준]

### Evidence 기록 규칙

목표를 `complete`로 전이하기 위해 `goals.json`의 `evidence` 필드에 기록해야 하는 항목:

1. 단위 테스트 통과 로그 (테스트 클래스명, 통과 건수/전체 건수)
2. 어셈블리 빌드 성공 로그 (빌드 도구, 소요 시간)
3. 회귀 테스트 결과 요약 (통과 시나리오 수/전체 시나리오 수)
4. 실패 항목이 있을 경우, 실패 원인과 `review_blocked` 전이 사유

> [!CAUTION]
> Evidence가 불충분하거나 누락된 상태에서 목표를 `complete`로 전이하는 것은 AI-DLC 규약 위반입니다.

---

## 교차 검증 이력 (Cross-Review Trail)

| 단계 | 파일명 | 결과 | 잔존 리스크 |
| :--- | :--- | :--- | :--- |
| Stage 1: Planner | `stage-01-planner.md` | [초안 수립 완료] | [식별된 리스크] |
| Stage 2: Critic | `stage-03-critic.md` | [OKAY / REJECT] | [잔존 리스크 평가] |
| Stage 3: Architect | `stage-03-architect.md` | [APPROVE / REJECT] | [아키텍처 리스크] |
| Stage 4: Reconciliation | `stage-03-post-interview.md` | [정합성 100% / 불일치 건수] | [미해결 불일치] |

---

## 승인 메타데이터

| 항목 | 값 |
| :--- | :--- |
| 기획 UUID | `[UUID]` |
| 대상 Feature | `[Feature Name]` |
| 선행 인터뷰 명세 | `specs/deep-interview-[slug].md` |
| 승인 일시 | `[YYYY-MM-DDTHH:MM:SSZ]` |
| 승인자 | [개발자 이름 / 역할] |
| 생성될 Goal ID 범위 | `[G001 ~ G00N]` |

---

## 작성 완료 체크리스트

- [ ] 모든 `[placeholder]`가 실제 값으로 대체되었는가?
- [ ] ADR에 기각된 대안이 최소 1개 이상 기록되었는가?
- [ ] File Plan에 수정 대상 파일이 모두 나열되었는가?
- [ ] 검증 계획에 단위/어셈블리/회귀 테스트가 모두 포함되었는가?
- [ ] Evidence 기록 규칙이 구체적으로 정의되었는가?
- [ ] 교차 검증 4단계가 모두 수행되었는가?
- [ ] 선행 인터뷰 명세(`specs/`)와의 정합성이 확인되었는가?
