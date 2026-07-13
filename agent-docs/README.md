# AI-DLC (AI-assisted Software Development Lifecycle) 개발 방법론 가이드

AI-DLC 개발 방법론은 자율 AI 에이전트의 오작동을 차단하고 복잡한 구현의 완성도를 높이기 위해 설계된 **3단계 에이전트 제어 프레임워크**입니다.

이 가이드 세트는 에이전트 참조 시 불필요한 토큰 소비를 막고, 각 개발 플랜에 알맞은 규칙만을 정밀하게 적용할 수 있도록 **단계별 독립 지침 파일**로 구성되어 있습니다. 에이전트 작동 시 해당 시퀀스에 맞는 마크다운 파일만 `@` 멘션하거나 지침으로 제공하십시오.

---

## 🏛️ AI-DLC 방법론의 3대 핵심 기둥 및 지침 매핑

```text
  [1. Deep Interview Gating] ➔ 요구사항 모호성 최소화 & 명세 자산화 (인간-에이전트 협업)
              ▼
  [2. Consensus Planning] ➔ Planner-Critic-Architect의 상호 비판 및 최적 계획 도출
              ▼
  [3. Goal-Driven Execution] ➔ 목표 쪼개기 & 테스트 증거 기반 실행 및 동적 블로커 제어
```

| 개발 시퀀스 (플랜) | 지침서 링크 | 주요 적용 규칙 및 역할 |
| :--- | :--- | :--- |
| **01. 요구사항 모호성 해소** | [01_DEEP_INTERVIEW.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/01_DEEP_INTERVIEW.md) | 인터뷰 트리거 조건, 대안식 문답 포맷, Specs 명세 자산화 규칙 |
| **02. 합의 기반 기획 수립** | [02_CONSENSUS_PLANNING.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/02_CONSENSUS_PLANNING.md) | Planner-Critic-Architect 다단계 기획서 검토 및 ADR 작성법 |
| **03. 증거 기반 실행/검증** | [03_GOAL_DRIVEN_EXECUTION.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/03_GOAL_DRIVEN_EXECUTION.md) | goals/ledger/token-logs 스키마, 상태 머신 전이 및 턴 제한 규칙 |

---

## 📂 기타 참고 문서
* **[DIRECTORY_STRUCTURE.md](file:///d:/workspace/auto-oas/agent-docs/DIRECTORY_STRUCTURE.md)**: 에이전트 세션 상태 기록 폴더(`.gjc` 등)의 레이아웃과 파일 설명.
* **[AGENT_INSTRUCTIONS.md](file:///d:/workspace/auto-oas/agent-docs/AGENT_INSTRUCTIONS.md)**: 에이전트의 시스템 룰에 직접 삽입하기 위한 요약 실행 룰셋.

---

> [!TIP]
> **토큰 최적화 사용법**:
> - 현재 작업이 기획 단계라면, 전체 가이드를 다 읽히지 마시고 [02_CONSENSUS_PLANNING.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/02_CONSENSUS_PLANNING.md) 지침만 에이전트의 컨텍스트로 제공하십시오. 이를 통해 컨텍스트 크기를 획기적으로 줄이고 오작동을 예방할 수 있습니다.
