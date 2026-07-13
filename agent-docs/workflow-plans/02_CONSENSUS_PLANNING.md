# AI-DLC Workflow Plan 02: Consensus Planning

이 지침서는 AI-DLC 방법론의 두 번째 단계인 **Consensus Planning(합의 기반 다단계 기획)** 단계를 완수하기 위한 에이전트 실행 지침 및 계획서 템플릿입니다.

---

## 🎯 1. 실행 목표 (Goal)
인터뷰를 통해 확정된 스펙을 바탕으로, 여러 에이전트 페르소나(Planner, Critic, Architect)의 상호 비판 및 교차 검토를 거쳐 결함이 없고 최적화된 아키텍처 구현 계획안(`pending-approval.md`)을 수립하는 것입니다.

---

## 🛠️ 2. 다단계 교차 검토 프로세스 (Multi-Stage Review)

에이전트는 기획 단계에서 다음 4가지 단계를 순차적으로 수행하고 각 단계 마크다운 문서를 작성하여 합의를 도출해야 합니다.

### [Stage 1] Planner: 계획 초안 수립
* **파일명**: `stage-01-planner.md`
* **지침**: 변경 사항의 전/후 비교(Intent Diff), 기본 원칙(Principles), 파일별 상세 수정 사양(File-level change plan)을 작성합니다.

### [Stage 2] Critic: 리스크 및 구현 구체성 비판
* **파일명**: `stage-03-critic.md`
* **지침**: 초안 중 모호하거나 추상적인 계획을 지적하여 반려하거나 보강을 요구합니다. Verdict(`OKAY` or `REJECT`)를 명시하고 잔존 리스크(`Residual risk`)를 평가합니다.

### [Stage 3] Architect: 아키텍처 정합성 검증
* **파일명**: `stage-03-architect.md`
* **지침**: 코드 레이어 격리가 유지되는지, 불필요한 의존성이 엉키지 않는지, 기술적 완성도가 높은지 아키텍처 수준에서 확인하여 결정(`Decision: APPROVE` or `REJECT`)을 발행합니다.

### [Stage 4] Intent Reconciliation: 정합성 교차 매핑
* **파일명**: `stage-03-post-interview.md`
* **지침**: 최종 기획이 `specs/` 내에 지정된 심층 인터뷰 의사결정 사항들과 100% 매칭되고 모순되는 부분이 없는지 교차 정합성을 증명합니다.

---

## 📄 3. 최종 기획서 및 ADR 템플릿 (`pending-approval.md`)

모든 교차 검증을 마친 최종 기획안은 아래 형식에 맞추어 `pending-approval.md`로 산출되어야 합니다.

### 파일명: `plans/ralplan/[UUID]/pending-approval.md`
```markdown
# [Feature Name] 최종 개발 및 검증 계획서

## 아키텍처 의사결정 기록 (ADR)
### 결정사항 (Decision)
- [선택한 설계 사양 요약 작성]

### 결정 동기 (Drivers)
1. [해당 설계가 강제되는 배경 기술 요건]

### 기각된 대안 분석 (Alternatives considered)
- **Option A (선택됨):** [선택된 설계 내용과 장점]
- **Option B (기각됨):** [기각 설계] - 사유: [구체적 기각 사유 기술]

## Final Plan
### 수정 대상 파일별 상세 구현 사양 (File Plan)
1. `src/main/java/path/to/TargetFile.java`
   - [구현해야 할 구체적인 로직 변경 사항 기술]

### 검증 계획 (Verification Plan)
- **단위 테스트 (Unit)**: [대상 테스트 클래스 및 Assertion 사양]
- **어셈블리 테스트 (Assembly)**: [전체 빌드 정합성 검증 방식]
- **회귀 테스트 (Regression)**: [acceptance 시나리오 통과 여부 검증]
```

---

## 📋 4. 에이전트 적용용 룰 (Copy & Paste Rule)
> ```text
> # RULE: AI-DLC CONSENSUS PLANNING
> - Create detailed step-by-step change plans before coding.
> - Run cross-review stages: Planner (stage-01) ➔ Critic (stage-03 review) ➔ Architect (stage-03 approve).
> - Reconcile intent with Specs in stage-03-post-interview.md.
> - Draft pending-approval.md with ADR (Alternatives considered, Drivers, Consequences).
> - Never execute code modifications until pending-approval.md is finalized.
> ```
