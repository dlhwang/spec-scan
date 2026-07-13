# Micro-Loop: Goal-Driven Execution

> **Location**: `micro-loop/goal-driven-execution.md`  
> **Scope**: Applied during individual Unit of Work code execution in Construction phase.

---

## 🎯 1. Goal

To translate the finalized implementation plan (`pending-approval.md`) into a structured Goal Tree, verify each goal using concrete validation tests (Evidence), and handle failures dynamically by injecting sub-blocker goals rather than bypassing failures.

### Core Principles
- **No Evidence, No Completion**: A goal status cannot transition to `complete` without writing verified test logs in its `evidence` field.
- **Failures are Blockers**: Build or test failures transition the target goal to `review_blocked`. Workarounds are blocked.
- **Cost Controls**: Turn execution loops are strictly monitored under the Nudge Budget rules.

---

## 🌳 2. Goal Tree Construction

Convert the file plans and verification plans of the approved 기획서 (`pending-approval.md`) into sequential nodes.

### Tree Construction Rules

1. **File Plan ➔ Implementation Goals**: Create one implementation goal for each target file.
2. **Verification Plan ➔ Verification Goals**: Create separate goals for Unit, Assembly, and Regression tests.
3. **ID Format**: Use zero-padded alphanumeric IDs: `G` + 3 digits (`G001`, `G002`, ...).
4. **Sequential Dependencies**: Verification goals must run after implementation goals.
5. **Granularity**: Ensure each goal is atomic, with a single verifiable objective.

### Tree Layout Example
```text
pending-approval.md
├── G001: Implement DTO restoration in TargetFile.java
├── G002: Add constructor properties to AnotherFile.java
├── G003: Write and pass Unit Tests (TargetFileTest)
├── G004: Verify Assembly integration (mvn clean compile)
└── G005: Verify Regression test suite (Acceptance scenarios)
```

---

## ⚙️ 3. Goal State Machine

Goals must strictly follow state transitions defined in `./state-machine-spec.md`.

### State Overview
- `pending`: Initial state before execution starts.
- `active`: Under active implementation or validation.
- `review_blocked`: Verification failed. Blocked until child blockers are resolved.
- `complete`: Verification passed and logs recorded in `evidence`.
- `failed`: Nudge budget exceeded; execution halted.
- `superseded`: Invalidated due to upstream plan modifications.

### State Transition Summary
```text
pending ──[turn comes]──→ active
active ──[verify PASS + write logs]──→ complete
active ──[verify FAIL]──→ review_blocked
active ──[nudge_budget exceeded]──→ failed
review_blocked ──[blockers resolved]──→ active
any ──[upstream plan changes]──→ superseded
```

> See [state-machine-spec.md](./state-machine-spec.md) for full invariants and escalation workflows.

---

## 📋 4. Evidence Enforcement Rules

### Rule: `evidence` field is MANDATORY for `complete` transition.

The agent MUST write verifiable proof into the `evidence` field before marking a goal as `complete`:

| Goal Type | Required Evidence Format |
|:---|:---|
| **Unit Test** | Test class name, run count, pass count, coverage, and assertion confirmation. |
| **Assembly/Build** | Compilation tool command, build output status (SUCCESS), warning/error count. |
| **Regression** | Scenario/ acceptance suite ID, individual test case pass logs. |
| **Implementation** | Path to modified files, compiler confirmation, file signature check. |

### Anti-Patterns
```json
// ❌ Forbidden: Transitioning to complete with empty/null evidence
{ "id": "G001", "status": "complete", "evidence": "" }
{ "id": "G001", "status": "complete", "evidence": null }

// ✅ Allowed: Concrete proof before complete transition
{
  "id": "G001",
  "status": "complete",
  "evidence": "TargetFileTest: 5/5 tests passed (mvn test -Dtest=TargetFileTest). Coverage: Class 100%, Line 94%."
}
```

---

## 🚧 5. Dynamic Blocker Goal Injection

On compilation or test failure, the agent must generate a temporary child goal to fix the specific issue.

### Blocker Injection Process
```text
1. Transition active goal status to "review_blocked".
2. Write error logs or failure stacktrace in "evidence" field.
3. Append "goal_checkpointed" and "review_blockers_recorded" to ledger.jsonl.
4. Inject a new child goal into goals.json:
   ├ id: next available ID (e.g., G006)
   ├ status: pending
   ├ objective: Fix the specific compiler error/test failure
   └ steering: { kind: "review_blocker", blockedGoalId: "Target Goal ID (e.g., G003)" }
5. Transition G006 from pending ➔ active and start resolution immediately.
6. Once G006 transitions to complete ➔ set G003 status back to active.
7. Re-run verification tests for G003.
```

---

## 💰 6. Nudge Budget Cost Controls

- **Turn Cap**: Halt immediately if execution turns exceed **10 turns (`nudge_budget: 10`)**.
- **On Failure**: Set goal status to `failed` and escalate to developer.
- **Log Cost**: Record input/output tokens in `token-logs/token-log.jsonl` every turn.
- **Reference**: Refer to `../common/nudge-budget.md` for details.

---

## 📊 7. JSON/JSONL Specification Reference

### goals.json
```json
{
  "version": 1,
  "unitSlug": "unit-slug",
  "brief": "Core objective text",
  "goals": [
    {
      "id": "G001",
      "title": "Goal title",
      "objective": "Target condition",
      "status": "pending | active | review_blocked | complete | failed | superseded",
      "createdAt": "YYYY-MM-DDTHH:MM:SS.sssZ",
      "updatedAt": "YYYY-MM-DDTHH:MM:SS.sssZ",
      "evidence": "Verification proof (required for complete)",
      "steering": {
        "kind": "review_blocker",
        "blockedGoalId": "Target Goal ID"
      }
    }
  ]
}
```
> Full schema details: `../schemas/goals-schema.md`

---

## 📋 8. Enforcement Rules (Copy & Paste Rule)

```text
# RULE: MICRO-LOOP GOAL-DRIVEN EXECUTION
- Split the approved pending-approval.md into a Goal Tree in goals.json.
- Goal IDs: G + 3-digit zero-padded number (G001, G002, ...).
- State transitions MUST follow state-machine-spec.md (pending → active → complete).
- NEVER set a goal to complete without writing specific evidence into the evidence field.
- If validation fails, transition to review_blocked and inject a sub-goal with steering.blockedGoalId.
- When ALL blocker sub-goals are complete, transition the parent goal back to active and re-verify.
- Terminate and set status to failed if execution exceeds nudge_budget (10 turns).
- Record token usage in token-logs/token-log.jsonl at every turn.
- Reference: state-machine-spec.md for full transition rules, ../schemas/ for JSON schemas.
```

---

## 🔗 9. Cross-References

| Reference | Target Path |
|:---|:---|
| State Machine Spec | `./state-machine-spec.md` |
| Consensus Planning | `./consensus-planning.md` |
| goals.json Schema | `../schemas/goals-schema.md` |
| ledger.jsonl Schema | `../schemas/ledger-schema.md` |
| Nudge Budget Rules | `../common/nudge-budget.md` |
