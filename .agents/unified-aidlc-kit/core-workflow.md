# PRIORITY: This workflow OVERRIDES all other built-in workflows
# When user requests software development, ALWAYS follow this workflow FIRST

## Adaptive Workflow Principle
**The workflow adapts to the work, not the other way around.**

The AI model intelligently assesses what stages are needed based on:
1. User's stated intent and clarity
2. Existing codebase state (if any)
3. Complexity and scope of change
4. Risk and impact assessment

---

## MANDATORY: Kit Root Resolution

**CRITICAL**: This is a portable, self-contained kit. Resolve the kit root directory by checking these paths in order (use the first that exists):

- `.agents/unified-aidlc-kit/` (recommended portable location)
- `.agents/.aidlc/unified-aidlc-kit/` (nested AI-DLC agent kit)
- `unified-aidlc-kit/` (workspace root level)
- `.aidlc-kit/` (compact naming)

All subsequent file references (e.g., `common/process-overview.md`, `micro-loop/deep-interview-gating.md`) are relative to whichever kit root directory was resolved above.

---

## MANDATORY: Common Rules Loading

**CRITICAL**: ALWAYS load common rules at workflow start:
- Load `common/process-overview.md` for workflow overview
- Load `common/session-continuity.md` for session resumption guidance
- Load `common/content-validation.md` for content validation requirements
- Load `common/question-format-guide.md` for question formatting rules (includes trade-off format)
- Load `common/nudge-budget.md` for cost control and turn budget rules
- Reference these throughout the workflow execution

---

## MANDATORY: Extensions Loading (Context-Optimized)

**CRITICAL**: At workflow start, scan the `extensions/` directory recursively but load ONLY lightweight opt-in files — NOT full rule files.

**Loading process**:
1. List all subdirectories under `extensions/`
2. In each subdirectory, load ONLY `*.opt-in.md` files
3. The corresponding rules file is derived by convention: strip `.opt-in.md` and append `.md`
4. Do NOT load full rule files at this stage

**Deferred Rule Loading**:
- During Requirements Analysis, opt-in prompts are presented to the user
- When user opts IN → load full rules file at that point
- When user opts OUT → never load full rules file (saves context)
- Extensions without `*.opt-in.md` are always enforced

**Enforcement** (loaded/enabled extensions only):
- Extension rules are hard constraints, not optional guidance
- Non-compliance with applicable enabled extension rule is a **blocking finding**
- Include compliance summary at each stage completion

---

## MANDATORY: Content Validation

**CRITICAL**: Before creating ANY file, validate per `common/content-validation.md`:
- Validate Mermaid/ASCII diagram syntax
- Escape special characters properly
- Test content parsing compatibility

---

## MANDATORY: Documentation Language

**CRITICAL**: Write all documentation in Korean.
- Do NOT translate existing documentation only to change language
- Preserve user input in audit logs; keep code/identifiers/paths in original form

---

## MANDATORY: Question File Format

**CRITICAL**: Follow `common/question-format-guide.md` for ALL questions:
- Multiple choice format (A, B, C, D, E options)
- Trade-off aware format for design decisions (see 트레이드오프 인지 질문 포맷)
- [Answer]: tag usage
- Answer validation and ambiguity resolution

---

## MANDATORY: Welcome Message

At workflow start, load and display `common/welcome-message.md` ONCE.

---

## MANDATORY: Nudge Budget & Cost Control

**CRITICAL**: Throughout ALL phases, enforce `common/nudge-budget.md` rules:
- Maximum 10 turns per blocker resolution cycle
- Record token usage in `token-logs/token-log.jsonl` every turn (schema: `schemas/token-log-schema.md`)
- Force stop + `failed` state + developer escalation on budget exceed
- When sub-agent creation is restricted, main agent performs 1-person multi-role execution

---

# Adaptive Software Development Workflow

---

# 🔵 INCEPTION PHASE

**Purpose**: Planning, requirements gathering, and architectural decisions
**Focus**: Determine WHAT to build and WHY

**Stages**:
- Workspace Detection (ALWAYS)
- Reverse Engineering (CONDITIONAL - Brownfield only)
- Requirements Analysis (ALWAYS - Adaptive depth)
- User Stories (CONDITIONAL)
- Workflow Planning (ALWAYS)
- Application Design (CONDITIONAL)
- Units Generation (CONDITIONAL)

---

## Workspace Detection (ALWAYS EXECUTE)

1. **MANDATORY**: Log initial user request in audit.md with complete raw input
2. Load all steps from `inception/workspace-detection.md`
3. Execute workspace detection:
   - Check for existing aidlc-state.md (resume if found)
   - Scan workspace for existing code
   - Determine if brownfield or greenfield
   - Check for existing reverse engineering artifacts
4. Determine next phase
5. **MANDATORY**: Log findings in audit.md
6. Present completion message and automatically proceed

---

## Reverse Engineering (CONDITIONAL - Brownfield Only)

**Execute IF**: Existing codebase detected AND no previous reverse engineering artifacts
**Skip IF**: Greenfield project OR previous artifacts exist

**Execution**:
1. **MANDATORY**: Log start in audit.md
2. Load all steps from `inception/reverse-engineering.md`
3. Execute 9-stage reverse engineering pipeline
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

---

## Requirements Analysis (ALWAYS EXECUTE - Adaptive Depth)

**Always executes** but depth varies: Minimal / Standard / Comprehensive

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `inception/requirements-analysis.md`
3. Execute requirements analysis including:
   - Intent analysis (clarity, type, scope, complexity)
   - Depth determination
   - Clarifying questions (proactive approach)
   - **Intent Permanence**: Save key decisions to `specs/deep-interview-[slug].md` (see Step 7.1)
   - Verification Expectations with goal-driven evidence tracking
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

---

## User Stories (CONDITIONAL)

**Execute IF**: User-facing features, multiple user types, complex business requirements
**Skip IF**: Pure refactoring, simple bug fixes, infrastructure-only changes

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `inception/user-stories.md`
3. **PART 1 - Planning**: Create story plan with questions, wait for answers
4. **PART 2 - Generation**: Execute approved plan
5. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
6. **MANDATORY**: Log user's response in audit.md

---

## Workflow Planning (ALWAYS EXECUTE)

1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `inception/workflow-planning.md`
3. Load content validation rules from `common/content-validation.md`
4. Load all prior context (RE artifacts, requirements, user stories)
5. Execute workflow planning including phase determination and depth levels
6. **MANDATORY**: Validate all content before file creation
7. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
8. **MANDATORY**: Log user's response in audit.md

---

## Application Design (CONDITIONAL)

**Execute IF**: New components/services, service layer design needed
**Skip IF**: Changes within existing boundaries, no new components

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `inception/application-design.md`
3. Execute at appropriate depth
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

---

## Units Generation (CONDITIONAL)

**Execute IF**: Multiple units of work needed, complex system decomposition
**Skip IF**: Single simple unit, no decomposition needed

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `inception/units-generation.md`
3. Execute at appropriate depth
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

---

# 🟢 CONSTRUCTION PHASE

**Purpose**: Detailed design, precision execution, and evidence-based code generation
**Focus**: Determine HOW to build it, then BUILD it with proof

**Stages in CONSTRUCTION PHASE**:
- Per-Unit Loop (executes for each unit):
  - Functional Design (CONDITIONAL)
  - NFR Requirements (CONDITIONAL)
  - NFR Design (CONDITIONAL)
  - Infrastructure Design (CONDITIONAL)
  - **🔁 MICRO-LOOP** (replaces simple Code Generation):
    - Deep Interview Gating (CONDITIONAL)
    - Consensus Planning (ALWAYS)
    - Goal-Driven Execution (ALWAYS)
- Build and Test (ALWAYS - after all units complete)

**Note**: Each unit is completed fully before moving to the next.

---

## Per-Unit Loop (Executes for Each Unit)

### Functional Design (CONDITIONAL, per-unit)

**Execute IF**: New data models, complex business logic, business rules need design
**Skip IF**: Simple logic changes, no new business logic

1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `construction/functional-design.md`
3. Execute functional design for this unit
4. **MANDATORY**: Present standardized 2-option completion message
5. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
6. **MANDATORY**: Log user's response in audit.md

### NFR Requirements (CONDITIONAL, per-unit)

**Execute IF**: Performance/security/scalability requirements, tech stack selection
**Skip IF**: No NFR requirements, tech stack determined

1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `construction/nfr-requirements.md`
3. Execute NFR assessment
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

### NFR Design (CONDITIONAL, per-unit)

**Execute IF**: NFR Requirements executed
**Skip IF**: NFR Requirements skipped

1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `construction/nfr-design.md`
3. Execute NFR design
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

### Infrastructure Design (CONDITIONAL, per-unit)

**Execute IF**: Infrastructure mapping needed, deployment architecture required
**Skip IF**: No infrastructure changes

1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `construction/infrastructure-design.md`
3. Execute infrastructure design
4. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
5. **MANDATORY**: Log user's response in audit.md

---

### 🔁 MICRO-LOOP: Precision Execution Control (per-unit)

> **CRITICAL**: The Micro-Loop replaces the traditional Code Generation stage with a
> rigorous 3-step execution control system. It ensures that the user's intent (vibe)
> is precisely captured, validated through multi-persona review, and executed with
> evidence-based verification and dynamic blocker management.

#### Micro-Loop Step 1: Deep Interview Gating (CONDITIONAL)

**Execute IF**:
- Any ambiguity remains in the unit's implementation specifics
- Implementation touches existing code with potential conflicts
- Design decisions haven't been fully resolved in INCEPTION

**Skip IF**:
- Unit's implementation is unambiguously clear from prior artifacts
- Simple, well-defined changes with no design decisions

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `micro-loop/deep-interview-gating.md`
3. Execute ambiguity detection against unit scope
4. If ambiguity found:
   - Formulate trade-off-aware questions (Option A/B with impact analysis)
   - Present to user and await answers
   - Save approved decisions to `specs/deep-interview-[unit-slug].md`
   - Use template: `templates/deep-interview-spec-template.md`
5. If no ambiguity found: Skip with justification logged
6. **MANDATORY**: Log findings in audit.md

#### Micro-Loop Step 2: Consensus Planning (ALWAYS, per-unit)

**Always executes for each unit** - Ensures multi-perspective validation

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `micro-loop/consensus-planning.md`
3. Execute 4-stage review process:
   - **Stage 1 - Planner**: Create implementation plan (Intent Diff, File Plan)
   - **Stage 2 - Critic**: Risk analysis, verdict (OKAY/REJECT), residual risk
   - **Stage 3 - Architect**: Architecture validation, decision (APPROVE/REJECT)
   - **Stage 4 - Intent Reconciliation**: Cross-validate against specs/ and requirements
4. Produce `pending-approval.md` with ADR (template: `templates/pending-approval-template.md`)
5. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
6. **MANDATORY**: Log user's response in audit.md

#### Micro-Loop Step 3: Goal-Driven Execution (ALWAYS, per-unit)

**Always executes for each unit** - Replaces traditional Code Generation

**Execution**:
1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `micro-loop/goal-driven-execution.md`
3. Load state machine spec from `micro-loop/state-machine-spec.md`
4. Convert approved plan to Goal Tree in `goals.json` (schema: `schemas/goals-schema.md`)
5. Initialize `ledger.jsonl` (schema: `schemas/ledger-schema.md`)
6. Execute goals sequentially:
   - For each goal: pending → active → implement → verify
   - On verification success: Record evidence in `goals.json`, mark `complete`, log to `ledger.jsonl`
   - On verification failure: Mark `review_blocked`, inject sub-blocker goal, log to `ledger.jsonl`
   - On nudge_budget exceed: Mark `failed`, escalate to developer (see `common/nudge-budget.md`)
7. **MANDATORY**: Update `token-logs/token-log.jsonl` every turn
8. **MANDATORY**: Present 2-option completion message (Request Changes / Approve & Continue)
9. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
10. **MANDATORY**: Log user's response in audit.md

---

## Build and Test (ALWAYS EXECUTE)

1. **MANDATORY**: Log any user input in audit.md
2. Load all steps from `construction/build-and-test.md`
3. Generate comprehensive build and test instructions:
   - Build instructions for all units
   - Unit test / Integration test / Performance test instructions
   - Additional tests (contract, security, e2e) as needed
   - **Evidence Enforcement Integration**: Update goals.json evidence fields with test results
   - **Dynamic Blocker Injection**: On test failure, create sub-blocker goals
   - Requirement-level verification evidence mapping
4. Create instruction files in `build-and-test/` subdirectory
5. **Wait for Explicit Approval** - DO NOT PROCEED until user confirms
6. **MANDATORY**: Log user's response in audit.md

---

# 🟡 OPERATIONS PHASE

**Purpose**: Placeholder for future deployment and monitoring workflows
**Focus**: How to DEPLOY and RUN it (future expansion)

## Operations (PLACEHOLDER)

Future scope: deployment, monitoring, incident response, maintenance, production readiness.

---

# Key Principles

- **Adaptive Execution**: Only execute stages that add value
- **Transparent Planning**: Always show execution plan before starting
- **User Control**: User can request stage inclusion/exclusion
- **Progress Tracking**: Update aidlc-state.md with executed and skipped stages
- **Complete Audit Trail**: Log ALL user inputs and AI responses in audit.md
  - **CRITICAL**: Capture user's COMPLETE RAW INPUT exactly as provided
  - **CRITICAL**: Never summarize or paraphrase user input
  - **CRITICAL**: Log every interaction, not just approvals
- **Evidence-Based Completion**: Never mark a goal complete without verification evidence
- **Dynamic Blocker Control**: Test failure triggers automatic blocker injection, not manual workaround
- **Cost Control**: Nudge budget enforces turn limits; token logs track spending
- **Content Validation**: Always validate before file creation per content-validation.md
- **NO EMERGENT BEHAVIOR**: Use standardized 2-option completion messages only
- **Idempotent Kit**: This kit is self-contained and portable across projects

---

## MANDATORY: Plan-Level Checkbox Enforcement

### Rules for Plan Execution
1. **NEVER complete any work without updating plan checkboxes**
2. **IMMEDIATELY after completing ANY step, mark that step [x]**
3. **This must happen in the SAME interaction where work is completed**
4. **NO EXCEPTIONS**: Every plan step must be tracked

### Two-Level Checkbox Tracking
- **Plan-Level**: Track detailed execution progress within each stage
- **Stage-Level**: Track overall workflow progress in aidlc-state.md
- **Goal-Level**: Track goal state transitions in goals.json (Micro-Loop phases only)
- **Update immediately**: All progress updates in SAME interaction

---

## Prompts Logging Requirements

- **MANDATORY**: Log EVERY user input with timestamp in audit.md
- **MANDATORY**: Capture user's COMPLETE RAW INPUT (never summarize)
- **MANDATORY**: Log every approval prompt with timestamp before asking
- **MANDATORY**: Record every user response with timestamp after receiving
- **CRITICAL**: ALWAYS append to audit.md, NEVER overwrite entire contents
- Use ISO 8601 format for timestamps

### Audit Log Format:
```markdown
## [Stage Name or Interaction Type]
**Timestamp**: [ISO timestamp]
**User Input**: "[Complete raw user input]"
**AI Response**: "[AI's response or action taken]"
**Context**: [Stage, action, or decision made]

---
```

---

## Directory Structure

```text
<WORKSPACE-ROOT>/                      # ⚠️ APPLICATION CODE HERE
├── [project-specific structure]       # Varies by project
│
├── aidlc-docs/                        # 📄 DOCUMENTATION ONLY
│   ├── inception/                     # 🔵 INCEPTION PHASE
│   │   ├── plans/
│   │   ├── reverse-engineering/       # Brownfield only
│   │   ├── requirements/
│   │   ├── user-stories/
│   │   └── application-design/
│   ├── construction/                  # 🟢 CONSTRUCTION PHASE
│   │   ├── plans/
│   │   ├── {unit-name}/
│   │   │   ├── functional-design/
│   │   │   ├── nfr-requirements/
│   │   │   ├── nfr-design/
│   │   │   ├── infrastructure-design/
│   │   │   ├── micro-loop/           # 🔁 MICRO-LOOP ARTIFACTS
│   │   │   │   ├── specs/            # Deep Interview decision specs
│   │   │   │   ├── plans/            # Consensus Planning stage artifacts
│   │   │   │   │   └── [UUID]/
│   │   │   │   │       ├── stage-01-planner.md
│   │   │   │   │       ├── stage-03-critic.md
│   │   │   │   │       ├── stage-03-architect.md
│   │   │   │   │       ├── stage-03-post-interview.md
│   │   │   │   │       └── pending-approval.md
│   │   │   │   ├── ultragoal/        # Goal-Driven Execution state
│   │   │   │   │   ├── goals.json
│   │   │   │   │   └── ledger.jsonl
│   │   │   │   └── token-logs/
│   │   │   │       └── token-log.jsonl
│   │   │   └── code/                 # Markdown summaries only
│   │   └── build-and-test/
│   ├── operations/                    # 🟡 OPERATIONS PHASE (placeholder)
│   ├── aidlc-state.md
│   └── audit.md
```

**CRITICAL RULES**:
- Application code: Workspace root (NEVER in aidlc-docs/)
- Documentation: aidlc-docs/ only
- Micro-loop artifacts: Per-unit under `micro-loop/`
- Project structure: See code-generation.md for patterns by project type
