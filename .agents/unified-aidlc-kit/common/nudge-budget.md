# Nudge Budget & Cost Control Rules

This document defines the rules for runtime budget and token cost control to prevent agent loops, runaway turns, and excessive token usage.

---

## 1. Nudge Budget Rule (Turn Limits)

- **Definition**: The maximum number of consecutive interaction turns the agent is allowed to execute autonomously to resolve a single active unit of work or blocker goal.
- **Default Limit**: **Maximum 10 turns (`nudge_budget: 10`)**
- **Enforcement Mechanics**:
  1. For every turn, increment the turn counter. The cumulative count must be logged in `token-logs/token-log.jsonl`.
  2. When the turn count reaches 7 turns, issue a system warning and review the resolution strategy.
  3. When the turn count reaches 10 turns, **force stop** the execution immediately and set the target goal status to `failed`.
  4. Perform developer escalation immediately.

---

## 2. Token Usage Logging

At the end of every interaction turn, the agent MUST append a record to `micro-loop/token-logs/token-log.jsonl`.
- **Required Schema Fields** (Refer to `../schemas/token-log-schema.md`):
  - `turnNumber`: Relative turn number within the current active unit of work.
  - `inputTokens`: Input tokens used in the turn.
  - `outputTokens`: Output tokens used in the turn.
  - `cacheTokens`: Cache-read or cached tokens (if supported).
  - `model`: Name of the LLM model used.
  - `costEstimate`: Estimated cost in USD for this turn.
  - `cumulativeTurns`: Running cumulative turns count.
  - `timestamp`: Current ISO 8601 UTC timestamp.

---

## 3. Cost Monitoring and Thresholds

- **Warning Threshold**: Cumulative turn cost > 2.0 USD or turn count >= 7 turns.
- **Blocker Threshold**: Cumulative cost > 5.0 USD or turn count >= 10 turns. At this point, freeze additional API requests.

---

## 4. Escalation Protocol

On reaching the nudge budget limit or cost threshold:

1. **State Persistence**:
   - Transition the active goal's status to `failed` in `goals.json`.
   - Append a `goal_failed` event to `ledger.jsonl`.
   - Save compilation error logs, file diffs, and state.

2. **Blocker Reporting**:
   - Write a summary explaining the target goal, the active blocker, and root cause analysis.
   - List the last actions attempted and errors received.

3. **Developer Intervention**:
   - Present the failed status to the developer and request intervention (e.g., manual budget increment or rule adjustment).
   - Never auto-restart the loop without developer permission.

---

## 5. Sub-Agent Fallback Rules

If spawning independent sub-agents is restricted due to sandbox limits, API quotas (e.g., 429 errors), or configuration blocks:

- **Multi-Role Execution**:
  - The main agent must perform multi-persona review locally.
  - Sequentially act as **Planner**, **Critic**, and **Architect** across turns.
  - Explicitly document each perspective's critique in separate markdown sections in the output logs.
- **Log Restriction**:
  - Log the restriction status in `aidlc-state.md` or `audit.md` and alert the developer of single-agent fallback mode.
