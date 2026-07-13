# AI-DLC 통합 에이전트 지침 가이드 (Integrated Agent Rules)

이 디렉토리는 거시적 프로세스 표준인 **AI-DLC 기본 워크플로우**와 미시적 실행 제어 도구인 **세션 수준 지침(인터뷰-합의기획-목표실행)**의 장점을 결합하여 설계한 **통합 에이전트 소프트웨어 수명주기(Integrated AI-DLC) 지침서**입니다.

두 지침 체계가 결합되어, 사용자의 직관적인 의도(Vibe)가 설계의 변형 없이 코드로 구현되고, 객관적인 테스트 하네스(Harness) 검증 증거(Evidence)를 통해서만 완료되도록 강제하는 통합 프레임워크를 제공합니다.

---

## 🏛️ 통합 AI-DLC 프레임워크 설계 구조

본 통합 지침은 거시적 수명주기 단계(Phases)별로 미시적 에이전트 제어 장치가 유기적으로 트리거되도록 매핑되어 있습니다.

```text
[Phase 1: Inception 페이즈]
   └─ ➔ Deep Interview Gating 작동 (모호성 차단 및 Specs 명세화)
              ▼
[Phase 2: Construction 설계 페이즈]
   └─ ➔ Consensus Planning 작동 (Planner-Critic-Architect 교차 합의 및 ADR 수립)
              ▼
[Phase 3: Construction 구현 및 검증 페이즈]
   └─ ➔ Goal-Driven Execution 작동 (Goal Tree 분해, Assertions 증빙 및 Blocker 동적 제어)
```

---

## 📂 통합 지침서 파일 구성

에이전트 구동 시 필요한 단계의 지침 마크다운 파일만 `@` 멘션하거나 시스템 콘텍스트로 인입하여 토큰을 최적화하십시오.

| 수명주기 단계 | 상세 지침서 링크 | 결합된 핵심 내용 및 역할 |
| :--- | :--- | :--- |
| **통합 개요** | [README.md](file:///d:/workspace/auto-oas/integrated-agent-rules/README.md) | 본 문서. 전체 결합 구조 및 가이드 구성 맵. |
| **종합 룰셋** | [INTEGRATED_AGENT_RULES.md](file:///d:/workspace/auto-oas/integrated-agent-rules/INTEGRATED_AGENT_RULES.md) | 에이전트 시스템 프롬프트(System Prompt)에 삽입하는 통합 규칙. |
| **01. 인셉션 & 게이팅** | [01_INCEPTION_AND_GATING.md](file:///d:/workspace/auto-oas/integrated-agent-rules/phases/01_INCEPTION_AND_GATING.md) | Workspace 감지 및 분석 단계와 모호성 해소 인터뷰의 결합 지침. |
| **02. 설계 & 합의기획** | [02_CONSTRUCTION_AND_PLANNING.md](file:///d:/workspace/auto-oas/integrated-agent-rules/phases/02_CONSTRUCTION_AND_PLANNING.md) | 기능/비기능 설계 단계와 다단계 기획(ADR 포함)의 결합 지침. |
| **03. 구현 & 증거검증** | [03_EXECUTION_AND_VERIFICATION.md](file:///d:/workspace/auto-oas/integrated-agent-rules/phases/03_EXECUTION_AND_VERIFICATION.md) | 코드 생성/테스트 단계와 Goal Tree, Blocker 자동 주입의 결합 지침. |
| **데이터 스펙 규격** | [DATA_SPECIFICATION.md](file:///d:/workspace/auto-oas/integrated-agent-rules/specs_and_schemas/DATA_SPECIFICATION.md) | `goals.json`, `ledger.jsonl`, `token-log.jsonl` 등의 엄격한 JSON 스키마. |

---

> [!IMPORTANT]
> **통합 지침의 주요 보완점**:
> 1. **의도의 영속성 보장**: 요구사항 수집에서 확정된 명세가 기획의 정합성 매핑(`Intent Reconciliation`)을 거쳐 구현 목표까지 모순 없이 관통하도록 설계되었습니다.
> 2. **피드백 제어 루프**: 로컬 테스트 검증 실패 시 에이전트가 주관적으로 구현을 끝마치지 못하게 하네스 락(Lock)을 걸고 하위 목표를 강제 수주하도록 구현 루프를 시스템화했습니다.
