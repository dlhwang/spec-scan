# AI-DLC Workflow Plan 03: Goal-Driven Execution

이 지침서는 AI-DLC 방법론의 세 번째 단계인 **Goal-Driven Execution(목표 지향적 실행 및 검증)** 단계를 완수하기 위한 에이전트 실행 지침 및 상태 머신 명세서입니다.

---

## 🎯 1. 실행 목표 (Goal)
승인된 기획안의 파일 계획과 검증 계획을 세부 목표(Goal Tree)로 변환하고, 코드 구현 후에는 반드시 객관적 테스트 통과 증거(Evidence)를 입증하며 해결하고, 실패 시 동적으로 블로커 서브 목표를 생성해 우회 없는 안전을 담보하는 것입니다.

---

## 🛠️ 2. 상태 머신 및 전이 규칙 (State Machine)

에이전트는 목표 상태(`status`) 전환 시 아래 규칙 테이블에 지정된 액션을 기계적으로 자동 처리해야 합니다.

| 현재 상태 | 전이 상태 | 전이 트리거 (Trigger) | 필수 후속 조치 (Required Actions) |
| :--- | :--- | :--- | :--- |
| **pending** | **active** | 해당 목표 차례가 도래해 구현을 착수할 때 | 1. 목표 상태 파일의 상태를 `active`로 변경.<br>2. 원장 파일에 `goal_started` 기록 추가. |
| **active** | **review_blocked** | 로컬 검증 실패, assertion 누락, regression 발생 시 | 1. 목표 상태 파일 내 `evidence` 필드에 실패 원인 상세 기입.<br>2. 원장 파일에 `goal_checkpointed` 및 `review_blockers_recorded` 기록 추가.<br>3. 문제의 직접 원인을 타격하는 **하위 블로커 목표(예: G002)**를 트리 구조에 주입하고, `steering.blockedGoalId`를 현재 목표 ID로 연결. |
| **active** | **complete** | 기획의 Assertion 및 어셈블리 검증을 완벽히 입증했을 때 | 1. 목표 상태 파일 내 `evidence` 필드에 테스트 통과 메타데이터 및 입증된 Assertion 사양 상세 기입.<br>2. 원장 파일에 `goal_checkpointed` (complete) 기록 추가. |
| **review_blocked** | **active** | 차단의 원인이 된 모든 하위 블로커 목표의 상태가 `complete`로 처리되었을 때 | 1. 상위 목표 상태를 `active`로 원복.<br>2. 원장 파일에 `goal_started`를 재기록하고 재검증 시도. |

---

## 📄 3. JSON 파일 스키마 명세

### ① goals.json 스펙
```json
{
  "version": 1,
  "brief": "최상위 미션 원문",
  "goals": [
    {
      "id": "G001",
      "title": "세부 목표 명칭",
      "objective": "목표 달성 조건",
      "status": "pending" | "active" | "review_blocked" | "complete",
      "createdAt": "UTC_DATE",
      "updatedAt": "UTC_DATE",
      "evidence": "구체적인 테스트 결과, 어설션 입증 로그 (complete 판정 시 필수)",
      "steering": {
        "kind": "review_blocker",
        "blockedGoalId": "상위목표ID"
      }
    }
  ]
}
```

### ② ledger.jsonl 스펙 (단일 라인 Append JSON)
* **목표 시작**: `{"eventId":"UUID","event":"goal_started","goalId":"G001","timestamp":"UTC_DATE"}`
* **목표 완료/락**: `{"eventId":"UUID","event":"goal_checkpointed","goalId":"G001","status":"complete"|"review_blocked","evidence":"STRING","timestamp":"UTC_DATE"}`
* **블로커 연동**: `{"eventId":"UUID","event":"review_blockers_recorded","goalId":"상위ID","blockerGoalId":"하위ID","timestamp":"UTC_DATE"}`

---

## ⚠️ 4. 예외 및 비용 예산 통제 규칙 (Nudge Budget)
1. **턴 제한 (Nudge Budget)**: 세션 내 누적 턴 수 혹은 블로커 해결 재귀 루프가 **10회(nudge_budget: 10)**를 초과하면 추가 비용 방지를 위해 강제로 작업을 중단하고 에러 처리하십시오.
2. **비용 기록**: 매 턴 종료 전, LLM에 소비된 캐시 및 입력/출력 토큰 데이터를 `token-logs/token-log.jsonl`에 한 줄씩 기록하고 현재 턴 번호를 증가시키십시오.

---

## 📋 5. 에이전트 적용용 룰 (Copy & Paste Rule)
> ```text
> # RULE: AI-DLC GOAL-DRIVEN EXECUTION
> - Split final plan tasks into Goal Tree in goals.json.
> - State transitions MUST follow the state machine table (pending ➔ active ➔ review_blocked ➔ complete).
> - Never set a goal to complete without writing specific verification evidence logs.
> - If validation checks fail, mark active goal as review_blocked and inject a sub-goal with steering constraints.
> - Terminate process if execution exceeds 10 turns (nudge_budget).
> - Update token-log.jsonl in every turn to trace cost.
> ```
