# 에이전트 세션 상태 디렉토리 구조 및 명세

AI-DLC 런타임은 세션 단위로 동작하며, 에이전트의 실행 상태와 트랜잭션을 추적하기 위해 세션 상태 디렉토리(`.gjc` 등)에 다음과 같은 파일 구조를 유지합니다.

---

## 📂 1. 전체 디렉토리 레이아웃

```text
D:\workspace\auto-oas\
└── [state_directory]/                   # 에이전트 상태가 보관되는 폴더
    ├── _session-[UUID]/                 # 각 실행 세션의 독립된 작업 공간
    │   ├── locks/                       # 파일 락 및 프로세스 동기화 데이터
    │   ├── runtime/
    │   │   └── runtime-state.json       # 런타임 기본 사양 및 프로세스 메타데이터
    │   │
    │   ├── plans/
    │   │   └── ralplan/
    │   │       └── [UUID]/              # 세션별 다단계 기획(Multi-stage Planning) 산출물
    │   │           ├── index.jsonl      # 기획 단계별 산출물 인덱스 원장
    │   │           ├── stage-01-planner.md     # Planner 기획 초안
    │   │           ├── stage-02-revision.md    # 기획 1차 수정본
    │   │           ├── stage-03-architect.md   # Architect 승인 리포트
    │   │           ├── stage-03-critic.md      # Critic 리스크 감사 리포트
    │   │           ├── stage-03-post-interview.md # 인터뷰 정합성 교차 분석서
    │   │           ├── stage-03-final.md       # 최종 종결 계획서
    │   │           └── pending-approval.md     # 사용자의 최종 승인 대기 기획서 (ADR 포함)
    │   │
    │   ├── specs/                       # 요구사항 구체화를 위한 명세 폴더
    │   │   ├── deep-interview-index.jsonl # 심층 인터뷰 이력 인덱스
    │   │   └── deep-interview-[slug].md  # 심층 인터뷰 결과 및 제약 조건 명세서
    │   │
    │   ├── state/
    │   │   ├── audit.jsonl              # 상태 변경 감사 로그 (State Mutation Audit)
    │   │   ├── ralplan-state.json       # 계획 엔진 상태 로그
    │   │   ├── skill-active-state.json  # 활성화된 스킬 일치 및 에이전트 HUD 정보
    │   │   ├── ultragoal-state.json     # 목표 엔진 제어용 상태
    │   │   └── active/
    │   │       ├── ralplan.json         # 활성 계획 스냅샷
    │   │       └── ultragoal.json       # 활성 목표 스냅샷
    │   │
    │   ├── token-logs/
    │   │   └── token-log.jsonl          # 매 턴(turn)별 사용 토큰 및 비용 로깅 원장
    │   │
    │   └── ultragoal/
    │       ├── brief.md                 # 세션 시작 시 에이전트에게 주어진 원본 목표
    │       ├── goals.json               # 세부 목표 트리, 상태 및 검증 증거 데이터
    │       └── ledger.jsonl             # 목표 상태 변화 트랜잭션 감사 원장
    │
    └── ...
```

---

## 📑 2. 각 구조별 지침서 매핑

각 디렉토리 및 세부 파일 포맷에 대한 설명과 스키마 명세는 아래의 플랜별 지침서를 참고하십시오.

* **인터뷰 및 스펙 명세(`specs/`)**: [01_DEEP_INTERVIEW.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/01_DEEP_INTERVIEW.md)
* **다단계 기획서(`plans/`)**: [02_CONSENSUS_PLANNING.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/02_CONSENSUS_PLANNING.md)
* **목표 상태, 트랜잭션 원장, 토큰 로그(`ultragoal/`, `token-logs/`, `state/`)**: [03_GOAL_DRIVEN_EXECUTION.md](file:///d:/workspace/auto-oas/agent-docs/workflow-plans/03_GOAL_DRIVEN_EXECUTION.md)
