# Planner RALPLAN Stage 1 — spen-scan validation condition / graph context improvement

## Summary
Implement phase-1 validation recovery around four production files so `ddd-start2` becomes a passing representative regression: recover `@ModelAttribute OrderRequest` request shape, recover nested/body and service/domain `validationConditions`, and make graph context authoritative **only** for `SERVICE_HINT` promotion. The preferred plan keeps annotation/custom-validator behavior graph-independent, adds strict endpoint-scoped graph gating for service hints, preserves the existing `validationConditions` export contract, and limits scope to extractor/normalizer/exporter plus their assembly-facing behavior.

## Intent Diff
- **Before**: service hints can normalize even when graph evidence is absent because `RuleBasedConditionNormalizer.hasServiceRuleEvidence(...)` returns `true` for null/empty graphs; target-path normalization collapses nested paths to leaf names; exporter falls back broadly to BODY when a body binding exists; README still reflects an LLM-shaped normalization pipeline.
- **After (planned)**: only graph-backed, endpoint-reachable `SERVICE_HINT` candidates can become conditions; ambiguous or unbacked hints are dropped with warning/debug evidence; nested/body paths remain stable enough to restore `OrderRequest` shape and nested BODY rules; exporter keeps current consumer-facing `validationConditions` structure while carrying the stricter normalization output; no LLM/prompt path is introduced.

## Principles
1. **Strict authority boundary**: graph context may directly decide only `SERVICE_HINT`; annotation and custom-validator extraction/normalization stay graph-independent.
2. **Consumer compatibility first**: preserve current `validationConditions` payload semantics and only add optional metadata in a non-breaking manner.
3. **Endpoint isolation over recall**: prefer dropping ambiguous service/domain rules to leaking cross-endpoint evidence.
4. **Pattern-class generalization**: recover permission/state/existence/version-check classes, not repo-specific symbol hacks.
5. **Deterministic pipeline only**: no PromptBuilder/LLM activation or prompt-shaped fallback in phase 1.

## Decision Drivers
1. **Strict graph-backed `SERVICE_HINT` requirement** from the deep-interview spec, including ambiguous-case rejection.
2. **`ddd-start2` acceptance bar**: `@ModelAttribute OrderRequest` shape, nested BODY validation recovery, and service/domain permission/state recovery must all show up in output.
3. **Non-breaking export contract**: `ExecutionSpecExporter` and assembly tests are in scope, but downstream `validationConditions` consumers must not be forced to change.

## Viable Options

### Option A — Preferred: endpoint-scoped graph gate in normalizer/export path, extractor remains broad but cleaner
**Shape**
- Keep `ValidationExtractionService` responsible for collecting annotation/custom-validator/service-hint candidates.
- Tighten `ValidationEvidenceGraphBuilder` so endpoint→service→business-rule reachability is explicit and usable for endpoint-scoped checks.
- Replace permissive `hasServiceRuleEvidence(...)` behavior in `RuleBasedConditionNormalizer` with strict endpoint-scoped graph-backed promotion for `SERVICE_HINT` only.
- Adjust path preservation/export mapping so nested BODY conditions and `@ModelAttribute` shape survive into `ExecutionSpecExporter` output.

**Pros**
- Smallest change footprint across the actual production path named in the spec.
- Preserves existing annotation/custom-validator behavior and consumer contract.
- Fits current architecture: extractor emits candidates, graph builder emits evidence, normalizer decides promotion, exporter serializes results.
- Lets assembly tests prove behavior without introducing parallel pipelines.

**Cons**
- Requires careful path-handling changes in both normalizer and exporter to avoid regressions.
- Some ambiguity handling may need new warning/debug plumbing beyond current happy-path methods.

### Option B — Graph-first subgraph selection before normalization
**Shape**
- Introduce a stricter pre-normalization stage conceptually between graph builder and normalizer that filters candidates per endpoint/subgraph before any service-hint normalization.
- Normalizer then assumes all incoming `SERVICE_HINT` candidates are already graph-backed.

**Pros**
- Cleaner separation of concerns on paper.
- Makes endpoint isolation easier to reason about in tests.

**Cons**
- Larger refactor than phase-1 needs.
- Risks creating a new implicit contract between extraction and normalization not visible in the current files.
- More likely to force collateral changes outside the stated must-touch files/tests.

### Option C — Export-time filtering only
**Shape**
- Leave extraction and normalization mostly intact; suppress non-graph-backed service hints when exporting execution spec / assembly outputs.

**Pros**
- Lowest implementation effort.

**Cons**
- Invalid for this assignment: wrong abstraction layer, leaves internal normalized conditions inconsistent, weakens unit-testability, and risks other consumers observing forbidden service hints before export.

## Invalidation rationale for weaker alternatives
- **Option C is not acceptable** because the spec requires graph strictness for actual normalization/promotion semantics, not merely serialized output masking.
- **Option B is viable but weaker than A for phase 1** because the current code already centralizes service-hint promotion inside `RuleBasedConditionNormalizer`; moving authority earlier adds architecture churn without improving the acceptance target.
- **Option A is preferred** because it fixes the currently observed policy breach (`hasServiceRuleEvidence(...)` default-true on empty graph), preserves pipeline shape, and contains the contract-sensitive changes to the exact files already identified by the spec.

## In scope
- Strict graph-backed `SERVICE_HINT` promotion.
- Endpoint isolation for service/domain evidence.
- Recovery of `@ModelAttribute OrderRequest`-driven request/body shape where required by `ddd-start2` acceptance.
- Nested BODY validation path preservation/recovery.
- `ExecutionSpecExporter` compatibility updates and assembly-test coverage.
- Unit, assembly, and `ddd-start2` regression planning.

## Out of scope / non-goals
- PromptBuilder/LLM activation or any prompt-based normalization path.
- Broad MVC view-response classification redesign.
- Mandatory Web UI changes.
- Repo-specific symbol-name rule proliferation beyond reusable pattern classes.

## File-level change plan

### `src/main/java/io/atworks/specscan/analysis/application/ValidationExtractionService.java`
- Audit request-binding traversal so complex request objects relevant to `@ModelAttribute OrderRequest` are extracted reliably while known Spring MVC infrastructure parameters are filtered by general rule, not repo-specific name.
- Keep service-hint extraction broad enough to discover candidates, but avoid encoding graph authority here.
- If ambiguous-service-hint suppression needs warnings/debug breadcrumbs, define where extraction vs later stages own that emission.

### `src/main/java/io/atworks/specscan/analysis/support/ValidationEvidenceGraphBuilder.java`
- Tighten endpoint-scoped evidence construction so controller endpoint → request bindings / DTO fields → service methods → business rules is traversable deterministically.
- Verify node/edge modeling is sufficient for: reachable service method, reachable business rule, unrelated endpoint isolation, and ambiguous-case classification.
- Add only minimal graph enrichments needed for strict `SERVICE_HINT` backing and `ddd-start2` service/domain rule recovery.

### `src/main/java/io/atworks/specscan/analysis/support/RuleBasedConditionNormalizer.java`
- Replace permissive empty-graph acceptance with strict graph-backed gating for `SERVICE_HINT`.
- Preserve graph independence for `CUSTOM_ANNOTATION` and `VALIDATOR` cases.
- Refine target-path handling so nested/body paths are not collapsed too aggressively when `ddd-start2` needs structural fidelity.
- Expand or harden reusable service/domain rule-class mapping (permission/state/existence/version-check) only at a pattern-class level.
- Ensure ambiguous graph-backed hints produce **no condition**, plus warning/debug metadata path defined for observability.

### `src/main/java/io/atworks/specscan/analysis/support/ExecutionSpecExporter.java`
- Keep `validationConditions` contract stable.
- Update condition-to-request/body mapping so nested BODY conditions and `@ModelAttribute`-derived shape survive serialization without flattening to leaf-only paths where that would lose structure.
- Ensure assembly/export behavior does not regress for non-body/path/query/header conditions.
- Treat optional debug metadata as additive-only if included.

### `README.md`
- Minimal documentation update only if implementation changes make the current pipeline description materially misleading for maintainers (for example, documenting deterministic graph-backed `SERVICE_HINT` handling and avoiding LLM wording drift). This is secondary to code/test scope and should remain non-invasive.

## Sequencing and dependencies
1. **Codify constraints against current behavior**: align plan/tests to strict graph-backed `SERVICE_HINT`, endpoint isolation, and `ddd-start2` acceptance targets.
2. **Stabilize extraction inputs**: ensure request bindings and service-hint candidates cover `@ModelAttribute`/service-domain cases without relying on graph authority.
3. **Strengthen graph evidence**: make endpoint-scoped reachability explicit enough to distinguish reachable, unreachable, and ambiguous service hints.
4. **Tighten normalization**: apply graph authority only to `SERVICE_HINT`, preserve annotation/custom-validator paths, and fix nested-path handling.
5. **Preserve export contract**: update execution-spec assembly/serialization so recovered conditions appear under existing `validationConditions` semantics.
6. **Run targeted regressions**: unit → assembly → `ddd-start2` scenario, stopping for approval before wider follow-on scope.

Dependencies:
- Normalizer strictness depends on graph builder exposing stable endpoint-scoped evidence.
- Exporter path fidelity depends on normalized condition paths and request-binding resolution being stable.
- `ddd-start2` acceptance depends on all three: extraction coverage, graph strictness, and export mapping.

## Acceptance criteria
1. `SERVICE_HINT` conditions are emitted only when endpoint-reachable graph evidence exists for that endpoint.
2. Annotation/custom-validator-derived conditions remain available without graph dependency.
3. Unrelated endpoint graph nodes do not contaminate another endpoint’s normalized conditions.
4. Ambiguous or insufficient graph-backed service hints yield **no normalized condition** and leave warning/debug evidence.
5. `@ModelAttribute OrderRequest` shape is restored for `/orders/orderConfirm` and `/orders/order` in the `ddd-start2` regression output.
6. Nested BODY validation conditions for `/orders/order` are materially recovered rather than flattened away.
7. Service/domain endpoints such as `/my/orders/{orderNo}/cancel` recover permission/state-class conditions.
8. `ExecutionSpecExporter` preserves current `validationConditions` consumer compatibility.

## Verification

### Unit coverage
- `ValidationExtractionServiceTest`: verify annotation/custom-validator/service-hint candidate extraction still works and `@ModelAttribute`-relevant bindings are not lost; verify framework infrastructure filtering does not remove business-relevant request objects.
- `NormalizationServiceTest`: verify graph-backed `SERVICE_HINT` promotion, no-promotion on empty/unreachable/ambiguous graph, endpoint isolation, and preserved annotation/custom-validator behavior.
- Add focused tests around nested target-path normalization so BODY paths are not reduced incorrectly.

### Assembly coverage
- `OpenApiAssemblyServiceTest`: assert `validationConditions` contract stability, nested BODY conditions export correctly, and service/domain recovered conditions surface in assembled output.
- `ExecutionSpecExporter`-focused assembly assertions: BODY/path/query/header mapping, dedupe behavior, and non-breaking output structure.

### `ddd-start2` regression coverage
- Compare produced execution/openapi outputs against the manual acceptance bar from the spec:
  - `/orders/orderConfirm` and `/orders/order` expose `OrderRequest` shape.
  - `/orders/order` restores multiple nested BODY validations.
  - `/my/orders/{orderNo}/cancel` restores permission/state-class service/domain conditions.
- Regression must also confirm no blanket service-hint inflation on unrelated endpoints.

## Escalation/Risk Gate
Stop at approval before execution if any of these appear during implementation review:
1. The current graph model cannot express ambiguity/reachability cleanly without introducing new domain types outside the planned files.
2. `@ModelAttribute` shape recovery requires controller/request-binding changes outside the named production path.
3. Export compatibility would be broken unless downstream consumers change schema.
4. `ddd-start2` expected outputs imply repo-specific symbol rules instead of reusable pattern classes.

## Verification Plan
- **Order of proof**: unit tests for extractor/normalizer edge cases → assembly/export tests → `ddd-start2` regression scenario.
- **Primary assertions**:
  - strict graph gating for `SERVICE_HINT`
  - no graph dependency for annotation/custom validator
  - nested BODY path preservation
  - stable `validationConditions` output contract
  - `ddd-start2` endpoint-specific acceptance targets
- **Negative assertions**:
  - empty graph does not promote service hints
  - unrelated endpoint graph does not leak conditions
  - ambiguous graph creates warning/debug evidence but no condition
- **Regression threshold**: phase 1 is not complete unless all three layers pass: unit, assembly, and `ddd-start2`.

## Risks and mitigations
- **Risk: nested-path loss remains hidden behind exporter fallback-to-BODY behavior.**  
  **Mitigation:** add explicit tests for full target paths and request schema expansion, not just presence/absence of conditions.
- **Risk: graph builder over-collects calls and causes false-positive service-hint reachability.**  
  **Mitigation:** assert endpoint isolation with unrelated controller/service paths and require direct reachable evidence classes.
- **Risk: framework parameter filtering becomes too aggressive and removes valid `@ModelAttribute` objects.**  
  **Mitigation:** use type/class-based infrastructure filters only for known Spring MVC infrastructure types; test against representative `OrderRequest` bindings.
- **Risk: permission/state recovery drifts into repo-specific keyword rules.**  
  **Mitigation:** restrict additions to reusable rule classes (permission/state/existence/version) with explicit invalidation of symbol-name hacks.
- **Risk: warning/debug evidence path is underspecified.**  
  **Mitigation:** treat warnings as the default mandatory observability path; add debug metadata only if it can be carried non-breakingly.

## Open assumptions
- Existing test suites named in the deep-interview spec either already exist or will be extended rather than replaced.
- `ddd-start2` expected outputs are judged against the manual spec acceptance bar, not byte-for-byte equality with a single snapshot.
- Optional debug metadata is not required for phase-1 acceptance if warnings already expose ambiguity/invalidation.
- README update is optional unless reviewers decide the LLM wording now materially misleads contributors.

## Compact RALPLAN-DR
- **R (Result):** strict graph-backed `SERVICE_HINT`, recovered `OrderRequest` shape, recovered nested/service-domain validations, stable export contract.
- **A (Approach):** prefer Option A—tighten graph reachability + service-hint normalization + exporter path fidelity without introducing new LLM or parallel pipelines.
- **L (Limits):** no Web UI requirement, no PromptBuilder/LLM path, no MVC response redesign, no repo-specific hacks.
- **PLAN-DR:** execute only after approval; Architect should review graph authority/path semantics, Critic should stress endpoint-isolation, ambiguity handling, and export compatibility.
