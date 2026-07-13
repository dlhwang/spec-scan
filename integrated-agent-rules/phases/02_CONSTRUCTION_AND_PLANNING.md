# AI-DLC Phase 02: 설계 & 합의기획 (Design & Planning)

이 지침서는 AI-DLC의 두 번째 단계인 **설계(기능/비기능/인프라 설계)** 단계와 **합의기획(Consensus Planning)** 단계의 통합 실행 매뉴얼입니다.

---

## 🔄 1. 프로세스 맵 및 연동 방식

```text
[1. 거시적 설계 단계]
  - Functional Design: 데이터 모델링, 비즈니스 검증 규칙 설계
  - NFR Design / Infrastructure Design: 성능, 보안, 인프라 매핑 설계
         ▼
[2. 미시적 합의기획 단계 (Consensus Planning)]
  - Planner: stage-01-planner.md 초안 작성
  - Critic / Architect: stage-03 교차 감사 및 Verdict 발행
  - Intent Reconciliation: Specs 명세서와의 합치성 교차 검증
         ▼
[3. 최종 결재 제출] ➔ pending-approval.md 수립 (ADR 포함) 및 승인 대기
```

---

## 🛠️ 2. 단계별 실행 지침 (Activities)

### ① 다단계 교차 감사 룰 (Multi-Persona Review)
기획 단계의 결함을 조기 차단하기 위해 에이전트는 독립된 역할 관점에서 기획서를 교차 감사해야 합니다.

1. **Planner (초안 수립)**:
   - `stage-01-planner.md`를 작성하여 변경 사항의 변동 사양(Intent Diff), 기본 원칙(Principles), 파일별 상세 수정 사양(File-level change plan)을 명세하십시오.
2. **Critic (리스크 감사)**:
   - `stage-03-critic.md`를 작성하여 계획이 실질적인지(Actionable), 엣지 케이스 및 부작용 검토가 누락되었는지 확인하고 반려/보강 요구서와 Verdict(`OKAY` or `REJECT`)를 발행합니다.
3. **Architect (구조 적합성 검증)**:
   - `stage-03-architect.md`를 작성하여 도메인 계층 격리 여부, 패키지 간 의존 정합성을 분석하고 최종 승인 결정(`Decision: APPROVE` or `REJECT`)을 내립니다.
4. **Intent Reconciliation (의도 정합성 매핑)**:
   - `stage-03-post-interview.md`를 작성하여, Planner가 수립한 최종 합의안이 Phase 01에서 자산화한 `specs/deep-interview-[slug].md` 파일의 모든 하드 제약사항과 모순 없이 일치하는지 입증하십시오.

---

## 📄 3. 최종 기획서 및 ADR 명세 (`pending-approval.md`)

모든 단계의 교차 감사를 마친 기획서는 반드시 아래 포맷의 `pending-approval.md`로 산출되어 개발자의 최종 확인을 받아야 합니다.

* **저장 경로**: `plans/ralplan/[UUID]/pending-approval.md`
* **아키텍처 결정 기록(ADR) 작성 룰**:
  - 해당 설계를 결정한 주요 동기(Drivers)를 구체적으로 나열하십시오.
  - 선택된 설계 외에 고려되었던 **대안(Alternatives considered)**들을 명시하고, 왜 해당 대안들이 기각되었는지 기각 사유를 명시하십시오.

* **작성 템플릿**:
```markdown
# [Feature Name] 최종 개발 및 검증 계획서

## 1. 아키텍처 의사결정 기록 (ADR)
### 결정사항 (Decision)
- [선택한 설계 및 리팩토링 방식 기술]

### 기각된 대안 분석 (Alternatives considered)
- **Option A (선택됨):** [선택된 설계 장점]
- **Option B (기각됨):** [기각 설계] - 사유: [구체적 기각 사유 기술]

## 2. 세부 개발 계획 (Final Plan)
### 파일별 상세 구현 사양 (File Plan)
* `src/main/java/path/to/File.java`
  - [클래스/메서드 수준 로직 변경 명세]

## 3. 검증 계획 (Verification Plan)
* **단위 테스트 (Unit)**: [대상 테스트 클래스 및 Assertion 사양]
* **어셈블리 테스트 (Assembly)**: [빌드 정합성 검증]
* **회귀 테스트 (Regression)**: [대표 회귀 시나리오 통과 여부 검증]
```

---

## 📋 4. 에이전트 적용용 룰 (Copy & Paste Rule)
> ```text
> # RULE: DESIGN & PLANNING
> - Define business logic schemas and infrastructure mappings.
> - Run Planner (stage-01) ➔ Critic (stage-03) ➔ Architect (stage-03) cross-audits.
> - Verify plan consistency against specs/ specs in stage-03-post-interview.md.
> - Generate pending-approval.md including ADR with Alternatives Considered.
> - Block code modifications until developer confirms approval.
> ```
