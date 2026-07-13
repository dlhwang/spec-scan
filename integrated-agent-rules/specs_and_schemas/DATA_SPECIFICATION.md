# AI-DLC 데이터 규격 및 스키마 명세 (Data Specification)

이 지침서는 AI-DLC 워크플로우 구동 시 에이전트의 JSON 파싱 에러와 필드 정합성 꼬임을 원천 차단하기 위한 **엄격한 데이터 규격서(Strict Schema Definition)**입니다. 에이전트는 상태 및 로그 업데이트 시 이 명세에 정의된 스키마를 100% 준수해야 합니다.

---

## 📄 1. 목표 정의 파일 스키마 (`goals.json`)

* **경로**: `[state_directory]/_session-[UUID]/ultragoal/goals.json`
* **역할**: 계층화된 목표 트리(Goal Tree)의 명세와 최종 검증 증거(Evidence) 보관.

### JSON Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "GoalsSpecification",
  "type": "OBJECT",
  "properties": {
    "version": { "type": "INTEGER" },
    "brief": { "type": "STRING" },
    "goals": {
      "type": "ARRAY",
      "items": {
        "type": "OBJECT",
        "properties": {
          "id": { "type": "STRING", "pattern": "^G\\d{3}$" },
          "title": { "type": "STRING" },
          "objective": { "type": "STRING" },
          "status": { "type": "STRING", "enum": ["pending", "active", "review_blocked", "complete", "failed", "superseded"] },
          "createdAt": { "type": "STRING", "format": "date-time" },
          "updatedAt": { "type": "STRING", "format": "date-time" },
          "evidence": { "type": "STRING" },
          "steering": {
            "type": "OBJECT",
            "properties": {
              "kind": { "type": "STRING", "enum": ["review_blocker"] },
              "blockedGoalId": { "type": "STRING", "pattern": "^G\\d{3}$" }
            },
            "required": ["kind", "blockedGoalId"]
          }
        },
        "required": ["id", "title", "objective", "status", "createdAt", "updatedAt"]
      }
    }
  },
  "required": ["version", "brief", "goals"]
}
```

---

## 📄 2. 트랜잭션 원장 스펙 (`ledger.jsonl`)

* **경로**: `[state_directory]/_session-[UUID]/ultragoal/ledger.jsonl`
* **역할**: 목표 상태 변경 트랜잭션 기록 (Append-Only JSON Lines).
* **규격**: 개행(`\n`)으로 구분된 단일 JSON 객체들의 나열.

### 이벤트별 데이터 구조
```json
// 1. 기획 단계 목표 트리 최초 생성
{"eventId": "UUID", "event": "plan_created", "goalIds": ["G001"], "timestamp": "ISO_DATE"}

// 2. 특정 목표 구현 착수
{"eventId": "UUID", "event": "goal_started", "goalId": "G001", "timestamp": "ISO_DATE"}

// 3. 목표 검증 체크포인트 도달 (성공/락인)
{"eventId": "UUID", "event": "goal_checkpointed", "goalId": "G001", "status": "complete"|"review_blocked", "evidence": "STRING", "timestamp": "ISO_DATE"}

// 4. 검증 실패로 하위 블로커 목표 추가
{"eventId": "UUID", "event": "review_blockers_recorded", "goalId": "상위ID", "blockerGoalId": "하위ID", "timestamp": "ISO_DATE"}
```

---

## 📄 3. 토큰 가계부 스펙 (`token-log.jsonl`)

* **경로**: `[state_directory]/_session-[UUID]/token-logs/token-log.jsonl`
* **역할**: 매 턴(turn) LLM 토큰 사용량 및 요금 모니터링 (Append-Only JSON Lines).

### 라인별 데이터 구조
```json
{
  "subagentId": "root" | "sub-agent-uuid",
  "agent": "main" | "critic" | "architect",
  "turn": 1,
  "at": "ISO_DATE",
  "input": 12345,
  "output": 570,
  "cacheRead": 11776,
  "cacheWrite": 0,
  "totalTokens": 24691,
  "model": "codex-auto-review"
}
```
* **필드 설명**:
  - `cacheRead`/`cacheWrite`: 모델의 Context Caching 활용 토큰 수 추적.
  - `totalTokens`: 해당 턴에 소비된 전체 토큰 합계 (`input` + `output` + `cacheRead`).
