# Micro-Loop: Deep Interview Gating

> **Location**: `micro-loop/deep-interview-gating.md`  
> **Scope**: Applied during individual Unit of Work execution in Construction phase.

---

## 🎯 1. Goal

To identify ambiguities at the Unit level and capture precise specifications via structured developer interviews. This prevents the agent from making arbitrary implementation decisions, ensuring all choices are based on explicit consensus.

### Core Principles
- **Halt on Ambiguity**: Stop coding immediately when any implementation detail is unclear or conflicted.
- **Option-Based Trade-offs**: Pose questions using structured Option A/B trade-offs with explicit impact analysis.
- **Intent Permanence**: Save resolved choices into spec files to prevent context drift.

---

## 🚨 2. Trigger Conditions (When to Execute)

Deep Interview Gating MUST trigger if the agent detects one or more of the following conditions during Unit analysis.

### Ambiguity Detection Rules

| ID | Trigger Condition | Example |
|:---|:---|:---|
| **T1** | **Policy Conflict**: New rules conflict with existing schema/validation constraints. | Unclear how to support backward compatibility for a JSON path. |
| ****T2** | **Undefined Assertions**: Requirements are too vague to establish concrete verification assertions. | "Improve performance" without explicit SLA metrics. |
| **T3** | **Unclear Blast Radius**: The files to change have undocumented downstream impact. | Modifying a shared utility function with wide usage. |
| **T4** | **Edge Cases Undefined**: Boundary conditions or exceptional states are not specified. | How to handle null parameters, empty lists, or concurrent writes. |
| **T5** | **External Dependency Uncertainty**: Third-party API versions or integration formats are unconfirmed. | Expected response format of an unintegrated payment gateway. |

### Exemption Conditions
- A prior spec file under `specs/deep-interview-*.md` already documents the resolved decisions for the active ambiguity.
- The unit involves simple refactoring only (no functional or behavioral changes).

---

## 🔍 3. Ambiguity Resolution Workflow

```text
┌─────────────────────────────────────────┐
│  Step 1: Codebase & Scope Inspection     │
│  ─ Map requirements to existing code    │
├─────────────────────────────────────────┤
│  Step 2: Apply Ambiguity Rules (T1-T5)   │
│  ─ If any trigger matches ➔ HALT coding │
├─────────────────────────────────────────┤
│  Step 3: Draft Trade-off Questions      │
│  ─ Option A/B + pros/cons + blast radius│
├─────────────────────────────────────────┤
│  Step 4: Present Questions to Developer │
│  ─ Await explicit answer selection      │
├─────────────────────────────────────────┤
│  Step 5: Write Intent Specification     │
│  ─ specs/deep-interview-[unit-slug].md  │
└─────────────────────────────────────────┘
```

### Question Formulation Guidelines
1. **No Binary Questions**: Avoid simple "Yes/No" questions. Offer structured alternatives.
2. **Expose Trade-offs**: Detail the pros, cons, and downstream impacts (Compatibility, Performance, Maintenance) for each choice.
3. **Provide Recommendations**: Mark the agent's recommended choice with a `(Recommended)` tag.

---

## 📝 4. Trade-off Question Format

```markdown
## Trade-off Question [Number]
[Describe the ambiguity or conflict in detail]

### Option A: [Title]
[Description of the option]
- **Pros**: [Benefits]
- **Cons**: [Drawbacks/Costs]
- **Impact Analysis**:
  - Compatibility: [Impact on backward compatibility]
  - Performance: [Impact on runtime/build speed]
  - Maintainability: [Long-term support impact]

### Option B: [Title]
[Description of the option]
- **Pros**: [Benefits]
- **Cons**: [Drawbacks/Costs]
- **Impact Analysis**:
  - Compatibility: [Impact on backward compatibility]
  - Performance: [Impact on runtime/build speed]
  - Maintainability: [Long-term support impact]

[Answer]: (Awaiting Developer Input)
```

---

## 📄 5. Output Specification: Specs Artifact

### Naming Convention
```text
specs/deep-interview-[unit-slug].md
```
- `[unit-slug]`: Unique slug representing the active Unit of Work (e.g., `order-validation-refactor`).

### Structure Template
```markdown
# Deep Interview Specification: [Unit Name]

## Metadata
- Unit Slug: [unit-slug]
- Date Created: [ISO 8601 UTC]
- Questions Answered: [N]
- Status: RESOLVED | PARTIAL

## Decisions
- [D1] [Description of resolved decision]
- [D2] [Description of resolved decision]

## Hard Constraints
- [C1] [Strict rule that MUST NOT be bypassed during construction]
- [C2] [Strict rule that MUST NOT be bypassed during construction]

## Question Log
### Q1: [Brief Question Summary]
- Selected: Option [X]
- Developer Rationale: [Notes on developer input]
```

### Schemas & Templates Cross-References
- Schema Spec: `../schemas/deep-interview-spec-schema.md` (See `goals-schema.md` directory)
- Template Path: `../templates/deep-interview-spec-template.md`

---

## 📋 6. Enforcement Rules (Copy & Paste Rule)

```text
# RULE: MICRO-LOOP DEEP INTERVIEW GATING
- Before coding any Unit of Work, scan for ambiguity using triggers T1–T5.
- If ANY ambiguity is detected, HALT coding immediately.
- Formulate questions as Option A/B/C with trade-off analysis (pros, cons, impact).
- Mark recommended option with (Recommended) tag.
- Wait for explicit developer selection on every question.
- Save approved answers to specs/deep-interview-[unit-slug].md with Decisions + Hard Constraints.
- In subsequent implementation, STRICTLY follow the spec file — never deviate.
- If a prior spec already covers the ambiguity, skip the interview and reference the existing spec.
```

---

## 🔗 7. Cross-References

| Reference | Target Path |
|:---|:---|
| Schema Registry | `../schemas/` |
| Deep Interview Template | `../templates/deep-interview-spec-template.md` |
| Consensus Planning | `./consensus-planning.md` |
| Nudge Budget Rules | `../common/nudge-budget.md` |
