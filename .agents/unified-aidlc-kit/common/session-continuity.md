# Session Continuity Templates

## Welcome Back Prompt Template
When a user returns to continue work on an existing AI-DLC project, present this prompt:

```markdown
**Welcome back! I can see you have an existing AI-DLC project in progress.**

Based on your aidlc-state.md, here's your current status:
- **Project**: [project-name]
- **Current Phase**: [INCEPTION/CONSTRUCTION/OPERATIONS]
- **Current Stage**: [Stage Name]
- **Last Completed**: [Last completed step]
- **Next Step**: [Next step to work on]

**What would you like to work on today?**

A) Continue where you left off ([Next step description])

B) Review a previous stage ([Show available stages])

[Answer]: 
```

## MANDATORY: Session Continuity Instructions
1. **Always read aidlc-state.md first** when detecting existing project
2. **Parse current status** from the workflow file to populate the prompt
3. **MANDATORY: Load Previous Stage Artifacts** - Before resuming any stage, automatically read all relevant artifacts from previous stages:
   - **Reverse Engineering**: Read architecture.md, code-structure.md, api-documentation.md
   - **Requirements Analysis**: Read requirements.md, requirement-verification-questions.md
   - **User Stories**: Read stories.md, personas.md, story-generation-plan.md
   - **Application Design**: Read application-design artifacts (components.md, component-methods.md, services.md)
   - **Design (Units)**: Read unit-of-work.md, unit-of-work-dependency.md, unit-of-work-story-map.md
   - **Per-Unit Design**: Per-unit artifacts live under `aidlc-docs/construction/{unit-name}/` in
     `functional-design/`, `nfr-requirements/`, `nfr-design/`, and `infrastructure-design/`
     subdirectories. On resume, determine the in-progress unit from `aidlc-state.md` and load that
     unit's design artifacts, plus the design artifacts of any units it depends on (per
     `unit-of-work-dependency.md`). The exact files in each subdirectory are enumerated by the
     corresponding construction stage rules.
   - **Code Stages**: Read all code files, plans, AND all previous artifacts
4. **Smart Context Loading by Stage**:
   - **Early Stages (Workspace Detection, Reverse Engineering)**: Load workspace analysis
   - **Requirements/Stories**: Load reverse engineering + requirements artifacts
   - **Design Stages**: Load requirements + stories + architecture + design artifacts
   - **Code Stages**: Load ALL artifacts + existing code files
   - **Micro-Loop Stages**: Load micro-loop 아티팩트 (goals.json, ledger.jsonl, token-log.jsonl, specs/*)
5. **Adapt options** based on architectural choice and current phase
6. **Show specific next steps** rather than generic descriptions
7. **Log the continuity prompt** in audit.md with timestamp
8. **Context Summary**: After loading artifacts, provide brief summary of what was loaded for user awareness
9. **Asking questions**: ALWAYS ask clarification or user feedback questions by placing them in .md files. DO NOT place the multiple-choice questions in-line in the chat session.

## Error Handling
If artifacts are missing or corrupted during session resumption, see [error-handling.md](error-handling.md) for guidance on recovery procedures.

---

## Micro-Loop State Restoration

**Purpose**: Defines the procedure to restore exact execution states when a session is interrupted during Micro-Loop execution (Deep Interview ➔ Consensus Planning ➔ Goal-Driven Execution).

### State Restoration Targets

The following state files must be loaded upon session resumption:

| Target File | Expected Path | Description |
|:---|:---|:---|
| `goals.json` | `aidlc-docs/construction/{unit-name}/micro-loop/goals.json` | Active goals tree and status values. |
| `ledger.jsonl` | `aidlc-docs/construction/{unit-name}/micro-loop/ledger.jsonl` | Append-only event history transaction ledger. |
| `token-log.jsonl` | `aidlc-docs/construction/{unit-name}/micro-loop/token-logs/token-log.jsonl` | Step-by-step turn budget and token usage. |
| `specs/*.md` | `aidlc-docs/construction/{unit-name}/micro-loop/specs/` | Resolved deep interview specifications. |

### Session Resumption Steps

#### Step 1: Identify Active Unit of Work
```text
1. Parse aidlc-state.md to determine the in-progress Unit in CONSTRUCTION phase.
2. Verify the existence of the unit's micro-loop state directory.
3. If missing: Initialize Micro-Loop from Step 1 (Interview Gating).
4. If exists: Proceed to Step 2.
```

#### Step 2: Load goals.json & Assess Status
```text
1. Load and parse goals.json.
2. Evaluate target goal status fields:
   - "pending": Scheduled but not started.
   - "active": Execution or verification was in progress.
   - "review_blocked": Verification was blocked by child issues.
   - "complete": Successfully verified and complete.
3. Determine "active" or "review_blocked" goals as target entry points.
```

#### Step 3: Parse ledger.jsonl for Latest Event
```text
1. Read the tail of ledger.jsonl.
2. Identify the last appended event transaction:
   - goal_started ➔ Resume code implementation/verification for the target goal.
   - goal_checkpointed (review_blocked) ➔ Resume resolving the blocking issues.
   - goal_checkpointed (complete) ➔ Proceed to next available pending goal.
   - review_blockers_recorded ➔ Resume resolving target blocker child goals.
3. Restore execution context matching the event timeline.
```

#### Step 4: Validate Nudge Budget
```text
1. Load token-log.jsonl.
2. Calculate total cumulative turns spent on the active Unit.
3. Contrast with nudge_budget (10 turns).
4. If remaining turns <= 0: Trigger developer escalation (failed state).
5. If remaining turns > 0: Resume execution within remaining turn budget.
```

#### Step 5: Load Decisions & Constraints
```text
1. Scan specs/ directory for deep-interview-[slug].md.
2. Load all decisions (Decisions) and hard constraints (Constraints).
3. Bind constraints as immutable check rules for subsequent edits.
```

### Resumption Prompt Template

```markdown
**Micro-Loop execution state restored successfully.**

- **Target Unit**: [unit-name]
- **Active Goal**: [Goal ID] - [Goal Title]
- **Goal Status**: [active / review_blocked]
- **Progress**: [N] / [M] goals complete
- **Remaining Nudge Budget**: [X] turns
- **Specifications Loaded**: [N] files under specs/
- **Last Event Recorded**: [event type] at [timestamp]

**Resumption Plan**:
[Describe next immediate actions based on restored state]
```

### Status Resumption Matrix

| goals.json Status | Resumption Action |
|:---|:---|
| All goals are `pending` | Initialize the first pending goal sequentially. |
| Target goal is `active` | Resume coding or testing for the target active goal immediately. |
| Target goal is `review_blocked` | Identify and resolve the child blocker goal (`steering.blockedGoalId`) first. |
| Blocker goal resolves to `complete` | Re-transition parent target back to `active` and re-verify. |
| All goals are `complete` | Finalize micro-loop; transition to Build and Test stage. |
| Target goal is `failed` | Turn budget exceeded. Halt execution and await developer intervention. |

### Smart Context Loading Additions

Integrate the following targets into the main Codex Smart Context Loading checklist:

- **Micro-Loop State Files (Mandatory on CONSTRUCTION resume)**:
  - Load `goals.json` ➔ Understand goal statuses.
  - Load `ledger.jsonl` ➔ Check latest transaction event.
  - Load `token-log.jsonl` ➔ Evaluate remaining nudge budget.
  - Load `specs/*.md` ➔ Enforce resolved constraints.
  - Load Unit design assets (`functional-design/`, `nfr-design/`, `infrastructure-design/`).
