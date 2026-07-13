# Micro-Loop: Consensus Planning

> **Location**: `micro-loop/consensus-planning.md`  
> **Scope**: Applied during individual Unit of Work planning in Construction phase.

---

## 🎯 1. Goal

To establish a defect-free, optimized Unit Implementation Plan by conducting multi-perspective review stages (**Planner ➔ Critic ➔ Architect ➔ Intent Reconciliation**). No code modification may proceed without passing this consensus pipeline.

### Core Principles
- **No Single-Opinion Plans**: Force reviews across at least 3 distinct personas.
- **Traceable Deliverables**: Write intermediate review stages into dedicated markdown files to log history.
- **Intent Alignment**: Final plan must align 100% with the requirements and deep interview specifications.

---

## 🔄 2. 4-Stage Review Pipeline

```text
┌──────────────────────────────────────────────────┐
│  Stage 1: Planner                                │
│  ─ Drafts plan, intent diff, and file plan.       │
│  ─ Output: stage-01-planner.md                    │
└───────────────────────┬──────────────────────────┘
                        ▼
┌──────────────────────────────────────────────────┐
│  Stage 2: Critic                                 │
│  ─ Audits plan for ambiguity and technical risk. │
│  ─ Verdict: OKAY | REJECT                        │
│  ─ Output: stage-02-critic.md                     │
└───────────────────────┬──────────────────────────┘
                        ▼ (If REJECT: Return to Stage 1)
┌──────────────────────────────────────────────────┐
│  Stage 3: Architect                              │
│  ─ Verifies layered isolation and design patterns│
│  ─ Decision: APPROVE | REJECT                    │
│  ─ Output: stage-03-architect.md                  │
└───────────────────────┬──────────────────────────┘
                        ▼ (If REJECT: Return to Stage 1)
┌──────────────────────────────────────────────────┐
│  Stage 4: Intent Reconciliation                  │
│  ─ Map every spec decision & constraint to plan. │
│  ─ Output: stage-04-reconciliation.md            │
└───────────────────────┬──────────────────────────┘
                        ▼
            Final: pending-approval.md
```

---

### Stage 1: Planner — Draft Implementation Plan

**Filename**: `stage-01-planner.md`

**Responsibilities**:
1. **Intent Diff**: Contrast AS-IS and TO-BE behaviors.
2. **Implementation Principles**: Outline design guidelines for the changes.
3. **File Plan**: Specify target files, change types (Modify, New, Delete), and detailed changes.

**Structure**:
```markdown
# Stage 1: Planner — [Unit Name]

## Intent Diff
| Feature Area | AS-IS Behavior | TO-BE Behavior |
|:---|:---|:---|
| ... | ... | ... |

## Principles
1. [Principle 1]
2. [Principle 2]

## File Plan
### 1. `path/to/TargetFile.java`
- **Change Type**: [Modify | New | Delete]
- **Detailed Specification**: [Logic changes, parameters, method signatures]
```

---

### Stage 2: Critic — Risk & Ambiguity Audit

**Filename**: `stage-02-critic.md`

**Responsibilities**:
1. Identify vague descriptions, lack of detail, or missing edge cases in the Planner's draft.
2. Formulate specific critique items with requested corrections.
3. Issue a **Verdict**: `OKAY` or `REJECT`.
4. Document **Residual Risks** even if the verdict is OKAY.

**Structure**:
```markdown
# Stage 2: Critic — [Unit Name]

## Critique Items
### [C1] [Title of Issue]
- **Target Section**: [e.g., File Plan #1]
- **Problem**: [Explain ambiguity or missing edge case]
- **Requested Action**: [Action needed to resolve]
- **Severity**: HIGH | MEDIUM | LOW

## Verdict
- **Decision**: OKAY | REJECT
- **Rationale**: [Reasoning for verdict]

## Residual Risks
| ID | Risk Item | Probability | Impact | Mitigation Strategy |
|:---|:---|:---|:---|:---|
| R1 | ... | ... | ... | ... |
```

- **Verdict REJECT**: Return to Stage 1. The Planner must revise the draft based on critique items before re-submitting.

---

### Stage 3: Architect — Architectural Compliance

**Filename**: `stage-03-architect.md`

**Responsibilities**:
1. **Layered Isolation**: Ensure no circular dependencies or violation of layer boundaries.
2. **Design Patterns**: Verify consistency with active architecture styles (e.g., Domain-Driven Design, Serverless, CQRS).
3. **Technical Feasibility**: Assess validation rules, database constraints, and API signatures.
4. Issue a **Decision**: `APPROVE` or `REJECT`.

**Structure**:
```markdown
# Stage 3: Architect — [Unit Name]

## Architecture Audit Checklist
### [A1] Layer Boundaries & Separation
- **Result**: PASS | FAIL
- **Details**: [Explain compliance status]

### [A2] Dependency Directions
- **Result**: PASS | FAIL
- **Details**: [Verify target references]

## Decision
- **Verdict**: APPROVE | REJECT
- **Rationale**: [Reasoning]
- **Conditions**: [Any conditional actions for implementation, if approved]
```

- **Decision REJECT**: Return to Stage 1. Planner must rewrite design elements to satisfy architectural boundaries.

---

### Stage 4: Intent Reconciliation — Specification Verification

**Filename**: `stage-04-reconciliation.md`

**Responsibilities**:
1. Extract all **Decisions** and **Hard Constraints** from `specs/deep-interview-[unit-slug].md`.
2. Map every constraint to a specific line, file plan, or verification test in the final draft.
3. If any constraint is unmapped or bypassed, issue a **FAIL** verdict.

**Structure**:
```markdown
# Stage 4: Intent Reconciliation — [Unit Name]

## Intent Mapping Matrix
| ID | Spec Decision / Constraint | Implementation Target | Status |
|:---|:---|:---|:---|
| D1 | [Decision 1 text] | File Plan #1 (Line X) | ✅ MATCH |
| C1 | [Constraint 1 text] | Verification Plan (Unit Test Y) | ✅ MATCH |

## Reconciliation Result
- **Matching Coverage**: 100% (N/N constraints)
- **Verdict**: PASS | FAIL
- **Discrepancies**: [List unmatched items, if any]
```

---

## 📄 3. Final Implementation Deliverable: `pending-approval.md`

After passing all 4 stages, compile them into a unified plan using Architecture Decision Record (ADR) format.

### Path Convention
```text
plans/[unit-slug]/pending-approval.md
```

### Template Structure (Template: `../templates/pending-approval-template.md`)
```markdown
# [Unit Name] Final Plan & Verification Specification

## Metadata
- Unit Slug: [unit-slug]
- Date Finalized: [ISO 8601 UTC]
- Review Pipeline: Stage 1 ✅ | Stage 2 ✅ | Stage 3 ✅ | Stage 4 ✅
- Spec Reference: specs/deep-interview-[unit-slug].md

## Architecture Decision Record (ADR)
### Decision
- [Summary of chosen design architecture]

### Drivers
1. [Key technical factors forcing this decision]

### Alternatives Considered
- **Option A (Chosen)**: [Details and Pros]
- **Option B (Rejected)**: [Details] — Reason: [Why rejected]

## Final Plan
### Target Files & Logic Specifications (File Plan)
1. `path/to/TargetFile.java`
   - [Step-by-step logic and signature details]

## Verification Plan
- **Unit Tests**: [Target tests, assertions, and Mock specs]
- **Assembly Tests**: [Compilations, integration runs, API parameters]
- **Regression Tests**: [Acceptance scenarios, verification mapping]
```

---

## 🔁 4. Iteration Constraints

| Condition | Action |
|:---|:---|
| Stage 2 Verdict = `REJECT` | Return to Stage 1. Resolve critiques. |
| Stage 3 Decision = `REJECT` | Return to Stage 1. Fix architectural boundaries. |
| Stage 4 Reconciliation = `FAIL` | Return to Stage 1. Align with specifications. |
| Loop Count > 3 Iterations | Halt and escalate to developer. |

---

## 📋 5. Enforcement Rules (Copy & Paste Rule)

```text
# RULE: MICRO-LOOP CONSENSUS PLANNING
- Before writing ANY code for a Unit of Work, complete all 4 stages of consensus planning.
- Stage 1 (Planner): Create stage-01-planner.md with Intent Diff, Principles, and File Plan.
- Stage 2 (Critic): Create stage-02-critic.md with critique items and Verdict (OKAY/REJECT).
- Stage 3 (Architect): Create stage-03-architect.md with architecture validation and Decision (APPROVE/REJECT).
- Stage 4 (Reconciliation): Create stage-04-reconciliation.md proving 100% match with specs/.
- If ANY stage issues REJECT/FAIL, return to Stage 1 and revise. Max 3 iterations before escalation.
- Draft pending-approval.md with ADR (Decision, Drivers, Alternatives Considered).
- NEVER execute code modifications until pending-approval.md is finalized and all 4 stages PASS.
- If sub-agent creation is restricted, main agent performs all persona reviews locally.
```

---

## 🔗 6. Cross-References

| Reference | Target Path |
|:---|:---|
| Deep Interview Gating | `./deep-interview-gating.md` |
| Goal-Driven Execution | `./goal-driven-execution.md` |
| Pending-Approval Template | `../templates/pending-approval-template.md` |
| Nudge Budget Rules | `../common/nudge-budget.md` |
