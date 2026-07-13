# token-log.jsonl Schema Specification

> **Schema Identifier**: `aidlc/token-log/v1`  
> **File Path**: `token-logs/token-log.jsonl` (Workspace state subdirectory)  
> **File Format**: UTF-8 JSON Lines (JSONL)  
> **Writing Policy**: Append-only. Append one log entry at the end of every interaction turn.  
> **Related Documents**: [Goals Schema](./goals-schema.md) · [Ledger Schema](./ledger-schema.md) · [Nudge Budget Rules](../common/nudge-budget.md)

---

## 1. Schema Definitions

Each entry in `token-log.jsonl` represents the cost and token usage of a single turn:

| Field | Type | Mandatory | Description |
|:---|:---|:---:|:---|
| `turnNumber` | `integer` | ✅ | Relative turn sequence index within the active Unit (1-based). |
| `inputTokens` | `integer` | ✅ | Prompt tokens consumed in this turn. |
| `outputTokens` | `integer` | ✅ | Completion tokens generated in this turn. |
| `cacheTokens` | `integer` | ✅ | Number of tokens read from cache (if supported, otherwise write `0`). |
| `model` | `string` | ✅ | Identifier of the LLM model used (e.g., `"gemini-1.5-pro"`). |
| `costEstimate` | `number` | ✅ | Estimated monetary cost in USD for this turn. |
| `cumulativeTurns` | `integer` | ✅ | Cumulative turns executed. Used to enforce `nudge_budget` limits. |
| `timestamp` | `string (datetime)` | ✅ | ISO 8601 UTC timestamp (`YYYY-MM-DDTHH:MM:SS.sssZ`). |

---

## 2. JSONL Entries Examples

```json
{"turnNumber":1,"inputTokens":12000,"outputTokens":1500,"cacheTokens":0,"model":"gemini-1.5-pro","costEstimate":0.0825,"cumulativeTurns":1,"timestamp":"2026-07-13T07:01:00.000Z"}
{"turnNumber":2,"inputTokens":13500,"outputTokens":2100,"cacheTokens":10500,"model":"gemini-1.5-pro","costEstimate":0.0450,"cumulativeTurns":2,"timestamp":"2026-07-13T07:15:00.000Z"}
{"turnNumber":3,"inputTokens":15000,"outputTokens":900,"cacheTokens":12000,"model":"gemini-1.5-pro","costEstimate":0.0315,"cumulativeTurns":3,"timestamp":"2026-07-13T07:22:00.000Z"}
```
