# Unified AI-DLC Kit v1.0.0

사용자의 추상적 의도(바이브)를 정밀한 코드 구현으로 변환하기 위한 **통합 에이전트 개발 수명주기(AI-DLC) 키트**입니다.

거시적 프로젝트 거버넌스(표준 AI-DLC)와 미시적 실행 정밀도(세션 수준 제어)를 결합하여, 모호성 보완과 하네스 엔지니어링을 동시에 달성합니다.

---

## 🚀 빠른 시작 (Quick Start)

### 1. 키트 설치

프로젝트 루트에 이 폴더를 복사합니다:

```bash
cp -r unified-aidlc-kit/ <YOUR_PROJECT>/.agents/unified-aidlc-kit/
```

### 2. 에이전트 룰 설정

프로젝트의 에이전트 룰 파일(`.agents/AGENTS.md`, `.clinerules` 등)에 다음을 추가합니다:

```text
# AI-DLC Agent Kit Instructions
For software development work, follow `.agents/unified-aidlc-kit/core-workflow.md`.
```

### 3. 에이전트 시스템 프롬프트 설정 (선택)

빠른 설정이 필요하면 `templates/agent-instructions.md`의 Copy & Paste 템플릿을 에이전트의 System Prompt에 주입합니다.

---

## 📂 키트 구조 (Kit Structure)

```text
unified-aidlc-kit/
├── core-workflow.md              # 마스터 워크플로우 (에이전트 진입점)
├── VERSION                       # 키트 버전
├── README.md                     # 이 파일
│
├── common/                       # 공통 규칙 (워크플로우 시작 시 로드)
│   ├── process-overview.md       # 전체 프로세스 개요 + 다이어그램
│   ├── session-continuity.md     # 세션 재개 절차 + Micro-Loop 상태 복원
│   ├── content-validation.md     # 콘텐츠 검증 규칙
│   ├── question-format-guide.md  # 질문 포맷 + 트레이드오프 인지 질문
│   ├── nudge-budget.md           # 비용 통제 및 턴 예산 규칙 ★신규
│   ├── overconfidence-prevention.md  # 과잉 확신 방지
│   ├── depth-levels.md           # 적응적 깊이 조절
│   ├── error-handling.md         # 에러 핸들링 및 복구
│   ├── terminology.md            # 용어 정의
│   ├── welcome-message.md        # 웰컴 메시지
│   ├── workflow-changes.md       # 워크플로우 변경 관리
│   └── ascii-diagram-standards.md # ASCII 다이어그램 규격
│
├── inception/                    # 🔵 INCEPTION 단계 규칙
│   ├── workspace-detection.md    # 워크스페이스 탐지
│   ├── reverse-engineering.md    # 역공학 (브라운필드)
│   ├── requirements-analysis.md  # 요구사항 분석 ★강화: 의도 영속화 추가
│   ├── user-stories.md           # 사용자 스토리
│   ├── workflow-planning.md      # 워크플로우 기획
│   ├── application-design.md     # 애플리케이션 설계
│   └── units-generation.md       # 작업 단위 생성
│
├── construction/                 # 🟢 CONSTRUCTION 단계 규칙
│   ├── functional-design.md      # 기능 설계
│   ├── nfr-requirements.md       # NFR 요구사항
│   ├── nfr-design.md             # NFR 설계
│   ├── infrastructure-design.md  # 인프라 설계
│   ├── code-generation.md        # 코드 생성 (Micro-Loop에서 대체됨)
│   └── build-and-test.md         # 빌드 및 테스트 ★강화: 증거 강제 연동
│
├── micro-loop/                   # 🔁 MICRO-LOOP 정밀 실행 제어 ★신규 모듈
│   ├── deep-interview-gating.md  # 심층 인터뷰 게이팅
│   ├── consensus-planning.md     # 합의 기반 다단계 기획
│   ├── goal-driven-execution.md  # 증거 기반 목표 지향 실행
│   └── state-machine-spec.md     # 상태 머신 공식 명세
│
├── schemas/                      # 📋 JSON 스키마 명세 ★신규 모듈
│   ├── goals-schema.md           # goals.json 스키마
│   ├── ledger-schema.md          # ledger.jsonl 스키마
│   └── token-log-schema.md       # token-log.jsonl 스키마
│
├── templates/                    # 📝 템플릿 ★신규 모듈
│   ├── deep-interview-spec-template.md  # 인터뷰 명세 템플릿
│   ├── pending-approval-template.md     # 최종 기획서 + ADR 템플릿
│   └── agent-instructions.md            # 에이전트 시스템 프롬프트 룰셋
│
├── extensions/                   # 🔌 확장 모듈 (opt-in/opt-out)
│   ├── security/baseline/        # 보안 규칙 (15개 규칙)
│   └── testing/property-based/   # 속성 기반 테스트 (10개 규칙)
│
└── operations/                   # 🟡 OPERATIONS 단계 (향후 확장)
    └── operations.md
```

---

## 🏛️ 핵심 워크플로우 (Integrated Workflow)

```text
  INCEPTION (거시적 거버넌스)
  ┌─ Workspace Detection ─── Reverse Engineering ──────────┐
  │  Requirements Analysis ── User Stories ── Workflow Plan │
  │  Application Design ──── Units Generation              │
  └────────────────────────────────────────────────────────┘
                        ▼ 각 Unit 진입 시
  CONSTRUCTION
  ┌────────────────────────────────────────────────────────┐
  │  Functional Design ── NFR Requirements ── NFR Design   │
  │  Infrastructure Design                                 │
  │  ┌──────────────────────────────────────────────────┐  │
  │  │  🔁 MICRO-LOOP (정밀 실행 제어)                    │  │
  │  │  Step 1: Deep Interview Gating                   │  │
  │  │    → 모호성 탐지 + Option A/B 대안 유도            │  │
  │  │    → specs/deep-interview-[slug].md 영속화         │  │
  │  │  Step 2: Consensus Planning                      │  │
  │  │    → Planner-Critic-Architect 합의                │  │
  │  │    → ADR + Intent Reconciliation                 │  │
  │  │  Step 3: Goal-Driven Execution                   │  │
  │  │    → goals.json + evidence 강제                   │  │
  │  │    → review_blocked + 블로커 동적 주입             │  │
  │  │    → nudge_budget 비용 차단기                      │  │
  │  └──────────────────────────────────────────────────┘  │
  │  Build and Test (6-Layer + Evidence Enforcement)        │
  └────────────────────────────────────────────────────────┘
```

---

## 🔑 핵심 설계 원칙

| 원칙 | 설명 | 출처 |
|:---|:---|:---|
| **임의 가정 차단** | 모호성 발견 시 즉시 중단, Deep Interview로 전환 | agent-docs |
| **트레이드오프 대안 유도** | Option A/B 형식으로 결과 영향도까지 명시 | agent-docs |
| **의도 영속화** | 확정 의사결정을 specs/ 파일로 불변 자산화 | agent-docs |
| **다중 페르소나 합의** | Planner-Critic-Architect 교차 검증 + ADR | agent-docs |
| **증거 기반 완료** | evidence 필드 미기입 시 complete 상태 전이 불가 | agent-docs |
| **동적 블로커 주입** | 테스트 실패 시 우회 없이 블로커 목표 자동 생성 | agent-docs |
| **비용 차단기** | nudge_budget 10회 초과 시 강제 중단 + 에스컬레이션 | agent-docs |
| **적응적 깊이** | 요청 복잡도에 따라 Minimal/Standard/Comprehensive 조절 | aidlc-rules |
| **브라운필드 역공학** | 기존 코드베이스 9단계 자동 분석 | aidlc-rules |
| **확장 모듈** | 보안/PBT 등 opt-in 방식 독립 규칙 세트 | aidlc-rules |
| **세션 연속성** | 중단 후 재개 시 상태 완전 복원 | aidlc-rules |
| **에러 복구** | 4단계 심각도별 복구 절차 정의 | aidlc-rules |

---

## 📁 토큰 최적화 사용법

> **키트의 모든 파일은 독립적으로 로드 가능합니다.**
> 현재 작업 단계에 해당하는 파일만 에이전트 컨텍스트에 제공하십시오.

| 현재 작업 | 로드할 파일 |
|:---|:---|
| 워크플로우 시작 | `core-workflow.md` + `common/` 필수 파일들 |
| 요구사항 분석 | `inception/requirements-analysis.md` |
| 기능 설계 | `construction/functional-design.md` |
| Micro-Loop 진입 | `micro-loop/deep-interview-gating.md` (Step 1만) |
| 합의 기획 | `micro-loop/consensus-planning.md` (Step 2만) |
| 목표 지향 실행 | `micro-loop/goal-driven-execution.md` + `micro-loop/state-machine-spec.md` |
| JSON 스키마 확인 | `schemas/goals-schema.md` 등 (필요 시) |
| 빌드 & 테스트 | `construction/build-and-test.md` |

---

## 🔄 멱등성 (Idempotency)

이 키트는 **어떤 프로젝트에든 동일하게 작동**하도록 설계되었습니다:
- 모든 내부 참조는 키트 루트 기준 상대 경로
- `core-workflow.md`가 자동으로 키트 루트 위치를 탐지
- 외부 의존성 없이 독립 실행 가능
- `aidlc-docs/` 출력 디렉토리는 프로젝트마다 독립 생성

---

> **버전**: 1.0.0
> **기반**: 표준 AI-DLC (aws-aidlc-rules) + 세션 수준 실행 지침 (agent-docs)
> **호환**: 모든 AI 에이전트 플랫폼 (Antigravity, Claude Code, Cursor, Cline, Amazon Q 등)
