# ledger.jsonl Schema Specification

> **Schema Identifier**: `aidlc/ledger/v1`  
> **File Path**: `ledger.jsonl` (Workspace state root)  
> **File Format**: UTF-8 JSON Lines (JSONL - each line contains a single independent JSON object separated by `\n`)  
> **Writing Policy**: Append-only. Modification or truncation of existing records is strictly prohibited.  
> **Related Documents**: [Goals Schema](./goals-schema.md) · [Token Log Schema](./token-log-schema.md) · [State Machine Specification](../micro-loop/state-machine-spec.md)

---

## 1. Common Event Fields

Every event entry in `ledger.jsonl` must contain the following common properties:

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `eventId` | `string` | ✅ | Unique event identifier. **Format**: UUIDv4. |
| `event` | `string` | ✅ | Event type classification. (See Event Type mapping below). |
| `timestamp` | `string (datetime)` | ✅ | Event occurrence time. ISO 8601 UTC format (`YYYY-MM-DDTHH:MM:SS.sssZ`). |

---

## 2. Event Type Specifications

### 2.1 `plan_created`
Appended when a new Unit implementation plan is initialized in the goals state.

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `goalIds` | `array<string>` | ✅ | List of initial Goal IDs created (e.g., `["G001", "G002"]`). |

### 2.2 `goal_started`
Appended when a Goal transitions from `pending` to `active`, or re-enters `active` from `review_blocked`.

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `goalId` | `string` | ✅ | ID of the goal starting execution (e.g., `"G001"`). |

### 2.3 `goal_checkpointed`
Appended when a Goal transitions to a terminal state (`complete`, `failed`, `superseded`) or is blocked (`review_blocked`).

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `goalId` | `string` | ✅ | ID of the goal. |
| `status` | `string (enum)` | ✅ | New status of the goal: `complete`, `review_blocked`, `failed`, `superseded`. |
| `evidence` | `string` | ✅ | Verification proof logs or failure reasons. Mandatory. |

### 2.4 `review_blockers_recorded`
Appended when blocker targets are injected to resolve a `review_blocked` goal.

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `goalId` | `string` | ✅ | ID of parent goal blocked. |
| `blockerGoalId` | `string` | ✅ | ID of injected child goal. |

---

## 3. JSONL Entries Examples

```json
{"eventId":"a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d","event":"plan_created","goalIds":["G001","G002","G003"],"timestamp":"2026-07-13T07:00:00.000Z"}
{"eventId":"2b3c4d5e-6f7a-8b9c-0d1e-2f3a4b5c6d7e","event":"goal_started","goalId":"G001","timestamp":"2026-07-13T07:01:00.000Z"}
{"eventId":"cd5e6f7a-8b9c-0d1e-2f3a-4b5c6d7e8f9a","event":"goal_checkpointed","goalId":"G001","status":"review_blocked","evidence":"FAIL: BindingTest. NullPointerException at line 42.","timestamp":"2026-07-13T07:15:00.000Z"}
{"eventId":"e6f7a8b9-c0d1-e2f3-a4b5-c6d7e8f9a0b1","event":"review_blockers_recorded","goalId":"G001","blockerGoalId":"G004","timestamp":"2026-07-13T07:16:00.000Z"}
{"eventId":"f7a8b9c0-d1e2-f3a4-b5c6-d7e8f9a0b1c2","event":"goal_started","goalId":"G004","timestamp":"2026-07-13T07:17:00.000Z"}
{"eventId":"8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e","event":"goal_checkpointed","goalId":"G004","status":"complete","evidence":"PASS: BindingTest Null check added.","timestamp":"2026-07-13T07:22:00.000Z"}
{"eventId":"9c0d1e2f-3a4b-5c6d-7e8f-9a0b1c2d3e4f","event":"goal_started","goalId":"G001","timestamp":"2026-07-13T07:23:00.000Z"}
{"eventId":"0d1e2f3a-4b5c-6d7e-8f9a-0b1c2d3e4f5a","event":"goal_checkpointed","goalId":"G001","status":"complete","evidence":"PASS: BindingTest. All parameters bound successfully.","timestamp":"2026-07-13T07:28:00.000Z"}
```
