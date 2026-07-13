# Micro-Loop: State Machine Specification

> **Location**: `micro-loop/state-machine-spec.md`  
> **Scope**: Applied to all goal status transitions in Goal-Driven Execution.

---

## 🎯 1. Goal

To define all transitions in the Goal Tree lifecycle as mechanical, predictable states, preventing the agent from bypassing validation requirements or editing status fields arbitrarily.

---

## 📌 2. State Definitions

| State | Value | Description | Entry Condition |
|:---|:---|:---|:---|
| **Pending** | `pending` | Initial state. Not yet scheduled or ready for execution. | Default state when a Goal is created. |
| **Active** | `active` | In-progress. Under active implementation or validation. | Turn starts, or target blocker resolves. |
| **Review Blocked** | `review_blocked` | Blocked. Compilation/test validation failed. | Local tests fail, assertions miss, or regression occurs. |
| **Complete** | `complete` | Completed. Verifiable proof has been provided and validated. | `evidence` field is written with proof logs. |
| **Failed** | `failed` | Terminated. Budget limit reached or terminal error occurred. | Nudge budget exceeded; escalation triggered. |
| **Superseded** | `superseded` | Invalidated. Scope modified by upstream plan revisions. | Upstream design or unit plan is modified/withdrawn. |

---

## 📊 3. State Transition Matrix

| ID | Source State | Destination State | Trigger | Required Actions |
|:---|:---|:---|:---|:---|
| **T1** | `pending` | `active` | Target goal's execution turn arrives. | ① Set `status` to `"active"` in goals.json.<br/>② Record `goal_started` event in ledger.jsonl. |
| **T2** | `active` | `review_blocked` | Validation fails (compile, unit test, regression). | ① Write failure log/details in `evidence` field.<br/>② Record `goal_checkpointed` (review_blocked) and `review_blockers_recorded` in ledger.jsonl.<br/>③ Inject child blocker goal connected via `steering.blockedGoalId`. |
| **T3** | `active` | `complete` | Verification passed and assertion requirements satisfied. | ① Write verification metrics (command, pass count, coverage) into `evidence` (**Mandatory**).<br/>② Record `goal_checkpointed` (complete) in ledger.jsonl. |
| **T4** | `review_blocked` | `active` | All blocker child goals transition to `complete`. | ① Re-transition parent `status` to `"active"`.<br/>② Record `goal_started` in ledger.jsonl.<br/>③ Re-run verification tests. |
| **T5** | `active` | `failed` | Cumulative turns >= nudge budget (10 turns). | ① Transition `status` to `"failed"`.<br/>② Write failure metrics in `evidence` (e.g. "nudge_budget exceeded").<br/>③ Record `goal_checkpointed` (failed) in ledger.jsonl.<br/>④ Escalate to developer. |
| **T6** | `review_blocked` | `failed` | Blocker loop turns >= nudge budget. | Same actions as T5. |
| **T7** | *(any except complete, failed)* | `superseded` | Upstream implementation plan is revised or replaced. | ① Transition `status` to `"superseded"`.<br/>② Write reason in `evidence` (e.g., "superseded by plan v2").<br/>③ Record `goal_checkpointed` (superseded) in ledger.jsonl. |

---

## 🖼️ 4. Transition Diagram

```text
                         ┌─────────────────────────────┐
                         │   Upstream Plan Changed     │
                         │   (T7: any ➔ superseded)     │
                         └──────────┬──────────────────┘
                                    │
                                    ▼
┌───────────┐    T1     ┌───────────┐    T3     ┌───────────┐
│           │──────────→│           │──────────→│           │
│  pending  │           │  active   │           │ complete  │
│           │           │           │           │           │
└───────────┘           └─────┬─────┘           └───────────┘
                               │    ▲
                          T2   │    │  T4
                               │    │ (Blocker Fixed)
                               ▼    │
                         ┌───────────┐
                         │  review_  │
                         │  blocked  │
                         └─────┬─────┘
                               │
                          T5/T6│ (Budget Exceeded)
                               │
                               ▼
                         ┌───────────┐           ┌─────────────┐
                         │           │           │             │
                         │  failed   │           │ superseded  │
                         │           │           │             │
                         └───────────┘           └─────────────┘
```

---

## 🔒 5. Invariants

The following rules MUST NOT be bypassed under any circumstances:

- **INV-1: No Backward Transitions**:
  - `complete` ➔ cannot transition to any state.
  - `failed` ➔ cannot transition to any state.
  - `superseded` ➔ cannot transition to any state.
  - *Exception*: `review_blocked ➔ active` (T4) is allowed to resolve blockers.
- **INV-2: Complete Requires Evidence**:
  - If `status == "complete"`, the `evidence` field must contain a non-empty string.
- **INV-3: Review Blocked Requires Blocker Child**:
  - If `status == "review_blocked"`, at least one child goal with `steering.blockedGoalId` mapping to the parent ID must exist.
- **INV-4: Strict Values**:
  - Allowed status values: `["pending", "active", "review_blocked", "complete", "failed", "superseded"]`.

---

## 🚨 6. Escalation Protocols

### 6.1 Failed Transition Triggers
- **Budget Exceeded**: Active turns >= nudge_budget (10).
- **Infinite Blocker Loop**: Same blocker goal injected 3 or more times.
- **System Exception**: Sandbox/file system blocks execution.

### 6.2 Escalation Process
1. Save the target failed state to `goals.json`.
2. Write root cause, turns count, and last failure output into `evidence`.
3. Record `goal_checkpointed` (failed) in `ledger.jsonl`.
4. Stop execution of the active Unit of Work.
5. Report status to the developer and request intervention.

---

## 📊 7. Allowed State Transition Matrix

| From ╲ To | pending | active | review_blocked | complete | failed | superseded |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **pending** | — | ✅ T1 | ❌ | ❌ | ❌ | ✅ T7 |
| **active** | ❌ | — | ✅ T2 | ✅ T3 | ✅ T5 | ✅ T7 |
| **review_blocked** | ❌ | ✅ T4 | — | ❌ | ✅ T6 | ✅ T7 |
| **complete** | ❌ | ❌ | ❌ | — | ❌ | ❌ |
| **failed** | ❌ | ❌ | ❌ | ❌ | — | ❌ |
| **superseded** | ❌ | ❌ | ❌ | ❌ | ❌ | — |

---

## 🔗 8. Cross-References

| Reference | Target Path |
|:---|:---|
| Goal-Driven Execution | `./goal-driven-execution.md` |
| goals.json Schema | `../schemas/goals-schema.md` |
| ledger.jsonl Schema | `../schemas/ledger-schema.md` |
| Nudge Budget Rules | `../common/nudge-budget.md` |
