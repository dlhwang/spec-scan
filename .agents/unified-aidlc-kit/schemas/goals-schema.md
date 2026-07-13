# goals.json Schema Specification

> **Schema Identifier**: `aidlc/goals/v1`  
> **File Path**: `goals.json` (Workspace state root)  
> **File Format**: UTF-8 Single JSON File  
> **Related Documents**: [State Machine Specification](../micro-loop/state-machine-spec.md) · [Ledger Schema](./ledger-schema.md) · [Token Log Schema](./token-log-schema.md)

---

## 1. Top-Level Structure

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `version` | `integer` | ✅ | Schema version number. Active value: `1`. |
| `brief` | `string` | ✅ | High-level mission statement. Describes the overall objective of the active session. |
| `goals` | `array<Goal>` | ✅ | Array containing unit goals. Minimum 1 item required. |

---

## 2. Goal Object Schema

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `id` | `string` | ✅ | Unique goal identifier. **Regex**: `^G\d{3}$` (e.g. `G001`, `G042`). |
| `title` | `string` | ✅ | Short descriptive name of the goal. Max 120 characters. |
| `objective` | `string` | ✅ | Target condition text specifying requirements to resolve the goal. |
| `status` | `string (enum)` | ✅ | Goal status value. (See Enum values section). |
| `createdAt` | `string (datetime)` | ✅ | Timestamp created. ISO 8601 UTC format (`YYYY-MM-DDTHH:MM:SS.sssZ`). |
| `updatedAt` | `string (datetime)` | ✅ | Timestamp updated. ISO 8601 UTC format. Updated on every transition. |
| `evidence` | `string` | Conditional | Verification proof. **Mandatory** when `status` is `"complete"`. Contains test logs or build confirmations. Recommended for failure logs in `review_blocked`. |
| `steering` | `object (Steering)` | Optional | Metadata used for blocker control. Present in child blocker goals only. |

---

## 3. Status Enumerated Values (`status` enum)

| Value | Description |
|:---|:---|
| `pending` | Waiting. Goal is scheduled but execution has not started. |
| `active` | Active. Agent is actively writing code or running verification tests. |
| `review_blocked` | Blocked. Local verification failed. A child blocker goal must be created. |
| `complete` | Completed. Verification passed and logs stored in `evidence`. |
| `failed` | Terminated. Budget limit reached; execution halted. Escalated. |
| `superseded` | Invalidated. Target goal abandoned due to upstream plan modifications. |

---

## 4. Steering Object Schema

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `kind` | `string` | ✅ | Control type classification. Valid value: `"review_blocker"`. |
| `blockedGoalId` | `string` | ✅ | ID of parent goal blocked. **Regex**: `^G\d{3}$`. |

---

## 5. Validation Rules

### 5.1 Field Level Validations

| Rule ID | Target Field | Condition | Action on Failure |
|:---|:---|:---|:---|
| **V-001** | `id` | Match regex `^G\d{3}$` | Reject goal creation. |
| **V-002** | `id` | ID uniqueness inside array | Reject duplicates. |
| **V-003** | `status` | Match enum list | Block transition. |
| **V-004** | `createdAt`/`updatedAt` | Valid ISO 8601 UTC string | Reject timestamp write. |
| **V-005** | `evidence` | Non-empty when `status === "complete"` | Block complete transition. |
| **V-006** | `steering.blockedGoalId` | Refer to existing ID inside array | Reject blocker creation. |

### 5.2 Transition Paths
Allowed transition paths:
- `pending ➔ active`
- `active ➔ review_blocked`
- `active ➔ complete`
- `review_blocked ➔ active` (When child blockers resolve)
- `active ➔ failed` (Turn budget exceeded)
- `* ➔ superseded` (Plan modified/withdrawn)

---

## 6. Complete JSON Example

```json
{
  "version": 1,
  "brief": "Restore @ModelAttribute OrderRequest bindings and maintain compatibility",
  "goals": [
    {
      "id": "G001",
      "title": "Restore OrderRequest model bindings",
      "objective": "Restore OrderRequest parameter bindings and pass binding test suite.",
      "status": "review_blocked",
      "createdAt": "2026-07-13T07:00:00.000Z",
      "updatedAt": "2026-07-13T07:25:00.000Z",
      "evidence": "FAIL: OrderRequestBindingTest#shouldBindNestedParams - Expected 3 fields bound, got 1. Missing @ConstructorProperties."
    },
    {
      "id": "G002",
      "title": "Add @ConstructorProperties to OrderRequest",
      "objective": "Add missing constructor metadata to resolve G001 binding failure.",
      "status": "complete",
      "createdAt": "2026-07-13T07:26:00.000Z",
      "updatedAt": "2026-07-13T07:40:00.000Z",
      "evidence": "PASS: OrderRequestBindingTest#shouldBindNestedParams (3/3 fields bound).",
      "steering": {
        "kind": "review_blocker",
        "blockedGoalId": "G001"
      }
    },
    {
      "id": "G003",
      "title": "Run API compatibility suite",
      "objective": "Ensure changes do not break existing API consumers.",
      "status": "pending",
      "createdAt": "2026-07-13T07:00:00.000Z",
      "updatedAt": "2026-07-13T07:00:00.000Z"
    }
  ]
}
```
