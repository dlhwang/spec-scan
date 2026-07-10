# AI-DLC Agent Kit Instructions

## Scope

- For software development work, follow `.agents/.aidlc/aidlc-rules/aws-aidlc-rules/core-workflow.md`.
- Do not modify `core-workflow.md`. This document is supplemental guidance for agent selection and delegation.
- The single source of truth for agent selection policy is `.agents/agent-selector.toml`. Do not duplicate detailed policies such as selection points, candidate lists, or output formats in this document.
- If `.agents/skills/CATALOG.md` exists, identify the skills appropriate for the task and follow the selected skill's `SKILL.md`.
- If `aidlc-docs/inception/reverse-engineering/` exists, reference its outputs during architecture and domain analysis.

## Required Integration

1. At the start of the workflow, read `core-workflow.md` and `.agents/agent-selector.toml`.
2. Whenever `phase0.trigger` in `agent-selector.toml` requires it, the main Codex must run Agent Selector Phase 0 first.
3. In particular, complete the selector decision before every Unit of Work Generation Plan step and before creating any task-specific subagent.
4. Do not create Agent Selector as an implementation subagent. The main Codex applies `developer_instructions` from the TOML and produces the selection JSON.
5. Agent Selector Phase 0 must search the readytoagent catalog and evaluate candidates appropriate for the current step, regardless of whether subagent spawning is actually permitted.
6. During planning and analysis stages, allow only read-only agents. Allow workspace-write agents only after explicit user approval for the relevant plan, and only within the approved scope.
7. Interpret Unit of Work detail rule files relative to the rule details directory determined by the **MANDATORY: Rule Details Loading** section in `core-workflow.md`.
8. Create task-specific subagents only from catalog agents included in `selected_agents` in the selector JSON. If no suitable agent exists, do not create arbitrary agents.
9. If spawning is allowed and a selected subagent is actually created, continue the workflow only after that work is completed. If spawning is not allowed, the main Codex performs the step using the selector result as guidance.

## Configuration File Error Handling

- If `.agents/agent-selector.toml` cannot be read or required fields cannot be interpreted, do not create task-specific subagents.
- In that case, the main Codex works only within read-only scope for the current step and records the blocking reason and required user approval.

## Selection and Spawn Separation

- `selected_agents` represents the result of choosing candidates suitable for the current task. It does not mean those agents will actually be spawned.
- Do not empty `selected_agents` merely because spawn permission is unavailable.
- Return an empty array for `selected_agents` only when the catalog has been evaluated and either no suitable agent exists or no agent is needed. In that case, record the specific rationale in `selection_notes`.
- If an agent is selected but cannot be spawned because of higher-priority instructions or user permissions, keep the candidate in `selected_agents` and record `unit_of_work_generation.ready_for_selected_subagents=false`.
- Record the spawn blocking reason, the required user permission, and whether the main Codex will perform the work instead in `selection_notes`.
- `blocked_workspace_write_agents` records mode restrictions for planning and analysis stages. Do not mix spawn permission absence with agent suitability judgment.
- In planning and analysis stages, choose read-only candidates. In generation stages, workspace-write candidates may also be selected after approval of the relevant plan, but actual spawn permission must still be checked separately.
- Each Phase 0 record must distinguish at minimum `candidate selection result`, `spawn allowed`, `actual creation`, and `main Codex fallback execution`.

## Conflict Handling

- Follow `core-workflow.md` for step execution, approval, audit logs, and output rules.
- Follow `.agents/agent-selector.toml` for agent selection, permission modes, catalog limits, and task brief rules.
- If the selection result conflicts with spawn permission, preserve the selection result, do not create the task-specific subagent, let the main Codex perform the step, and inform the user about the conflict.
