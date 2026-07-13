# AI-DLC Welcome Message

**Purpose**: This file contains the user-facing welcome message displayed ONCE at the start of any AI-DLC workflow.

---

# 👋 Welcome to the Unified AI-DLC Kit! 👋

I will guide you through an adaptive software development lifecycle designed to deliver robust, evidence-backed implementation aligned precisely with your intent.

## 🛠️ What is the Unified AI-DLC Kit?

This kit merges macro-level project governance (standard AI-DLC) with micro-level execution control (session-level constraints) to prevent agent derailment, resolve ambiguities, and ensure safety.

- **Ambiguity Gating**: Automatically halts coding when assumptions are detected, resolving them using trade-off-aware questions (Option A/B).
- **Intent Permanence**: Saves resolved design decisions into persistent specifications to prevent context corruption.
- **Consensus Planning**: Requires multi-persona reviews (Planner ➔ Critic ➔ Architect) and explicit Architecture Decision Records (ADR) before any edit.
- **Evidence-Backed Verification**: Enforces verifiable execution. No task is complete without passing test runs and assertion verification recorded in goals state.

---

## 🔄 The Unified Lifecycle

```text
                         User Request
                              │
                              ▼
        ┌───────────────────────────────────────────────┐
        │     🔵 INCEPTION PHASE (Planning & Analysis)   │
        ├───────────────────────────────────────────────┤
        │ * Workspace Detection (ALWAYS)                │
        │ * Reverse Engineering (CONDITIONAL - Brown)   │
        │ * Requirements Analysis (ALWAYS - Intent Spec)│
        │ * User Stories (CONDITIONAL)                  │
        │ * Workflow Planning (ALWAYS)                  │
        │ * Application Design (CONDITIONAL)            │
        │ * Units Generation (CONDITIONAL)              │
        └───────────────────────────────────────────────┘
                              │
                              ▼ Repeated for each Unit
        ┌───────────────────────────────────────────────┐
        │     🟢 CONSTRUCTION PHASE (Implementation)     │
        ├───────────────────────────────────────────────┤
        │ * Per-Unit Loop (Unit Design)                 │
        │   - Functional Design / NFR Design (COND)     │
        │   - Infrastructure Design (COND)              │
        │                                               │
        │   🔁 MICRO-LOOP (Precision Control - ALWAYS)  │
        │   ┌─────────────────────────────────────────┐ │
        │   │ 1. Deep Interview Gating (CONDITIONAL)  │ │
        │   │    - Detect ambiguity & ask trade-offs  │ │
        │   │ 2. Consensus Planning (ALWAYS)          │ │
        │   │    - Planner-Critic-Architect reviews   │ │
        │   │ 3. Goal-Driven Execution (ALWAYS)       │ │
        │   │    - goals.json & evidence-backed run   │ │
        │   │    - Cost budgets & automatic blockers  │ │
        │   └─────────────────────────────────────────┘ │
        │                                               │
        │ * Build and Test (ALWAYS - Multi-layer proof)  │
        └───────────────────────────────────────────────┘
                              │
                              ▼
        ┌───────────────────────────────────────────────┐
        │     🟡 OPERATIONS PHASE (Deployment)           │
        ├───────────────────────────────────────────────┤
        │ * Operations (Placeholder for future setup)   │
        └───────────────────────────────────────────────┘
                              │
                              ▼
                           Complete
```

---

## 🎯 Core Directives

1. **No Assumptions**:
   - If any ambiguity is encountered, coding must immediately halt to present trade-off questions.
2. **Trade-off-Aware Selection**:
   - Technical decisions are posed with trade-off analysis showing impact on Compatibility, Performance, and Maintenance.
3. **Nudge Budget**:
   - Enforces a 10-turn limit per active blocker loop to prevent infinite agent runs and token wastage.
4. **Verifiable Proof**:
   - All code changes require explicit, reproducible verification evidence (test outputs, logs) before completing.

## 🚀 Next Steps

1. I will analyze your active workspace to check if this is a greenfield or brownfield project.
2. I will build an execution roadmap proposing which stages will run, and await your confirmation.
3. We will proceed stage-by-stage with checkpoints and approval gates.

Let's begin!
