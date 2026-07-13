# Multi-Agent Selection & Spawning Governance

This document defines the governance rules for identifying, selecting, and spawning sub-agents to distribute or implement tasks within the adaptive development lifecycle (AI-DLC).

---

## 1. Catalog-Based Selection Principles

- **Official Catalog Source**:
  - Sub-agents must not be invented or assigned roles arbitrarily.
  - Agents must be selected only from catalog configurations (e.g., `readytoagent` directory) registered under the systems' active agent storage.
- **Least Privilege Principle**:
  - Select the minimum number of sub-agents required to perform the active step.
  - Prefer specialized agents (Narrow Specialists) over broad generalists.

---

## 2. Phase-Based Spawning Controls (Phase Gating)

The active mode and permission level of sub-agents are strictly gated based on the development phase:

### 2.1 Planning and Analysis (Inception Phase)
- **Constraint**: During requirements analysis, risk review, and workspace detection, select and spawn **read-only** sub-agents only.
- **Prohibition**: Spawning agents with workspace write privileges (`workspace-write`) is strictly prohibited during planning and analysis steps.

### 2.2 Execution and Generation (Construction Phase)
- **Policy**: Spawning sub-agents with write permissions (`workspace-write`) is allowed for code modification and verification tasks.
- **Gating Condition**: Spawning workspace-write agents is permitted only AFTER the target unit's implementation plan (`pending-approval.md`) has received explicit developer approval.

---

## 3. Selector Decision Gating (Agent Selector Phase 0/1)

Before launching sub-agents, the main Codex must perform Phase 0 or Phase 1 decision evaluations to output structured candidate matrices.

### 3.1 Selection Points
- Right before drafting or revising the Unit of Work Generation Plan.
- Right before starting implementation edits on any Unit of Work.
- Right before starting final build and verification testing.

### 3.2 Output Specification
The selector process must output logs containing:
- `catalog_source`: Active path to readytoagent registry.
- `current_context`: Active feature name, unit scope, and risk areas.
- `selected_agents`: List of selected sub-agents with modes (`read-only`/`workspace-write`), task briefs, and selection rationale.
- `not_selected`: Candidates evaluated but skipped, with clear justifications.

---

## 4. Spawning Fallback Rules

If sub-agent spawning fails due to environment restrictions, quota blocks (e.g., HTTP 429), or security sandbox policies:

- **Multi-Role Fallback**:
  - The main agent must perform local reviews sequentially.
  - Act as **Planner**, **Critic**, and **Architect** across turns.
  - Write critique, risks, and reconciliation logs directly into output streams in separate sections.
- **State Logging**:
  - Log the fallback status in `aidlc-state.md` or `audit.md` to inform the developer of local execution.
