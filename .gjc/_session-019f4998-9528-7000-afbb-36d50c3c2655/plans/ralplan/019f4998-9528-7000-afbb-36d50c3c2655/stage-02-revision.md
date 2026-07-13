# Planner RALPLAN Stage 1 (Revised) — spen-scan validation condition / graph context improvement

## Summary
Revise phase-1 implementation planning so the production path explicitly defines how `SERVICE_HINT` candidates are matched against endpoint-scoped graph evidence, how rejected/ambiguous hints are surfaced, and how `@ModelAttribute OrderRequest` shape recovery is restored without breaking existing `validationConditions` consumers. The preferred approach remains a contained pipeline change across extraction, graph building, normalization, and export, with deterministic rule-based behavior only and `ddd-start2` as the acceptance-driving regression.

## Intent Diff
- **Before revision**: the plan preferred strict graph-gated `SERVICE_HINT` normalization but left the exact match contract, rejection observability path, and `@ModelAttribute` shape ownership somewhat implicit.
- **After revision**: the plan now defines:
  - the exact reachable / unreachable / ambiguous graph match contract for `SERVICE_HINT`
  - mandatory warning emission for rejected/ambiguous hints, with optional additive debug metadata
  - explicit ownership of `@ModelAttribute` shape restoration in extraction/request-binding resolution, with exporter preserving rather than inventing structure

## Principles
1. **Graph authority is narrow and explicit**: only `SERVICE_HINT` may be promoted or rejected by graph evidence.
2. **Deterministic evidence over inference**: no LLM, prompt, or text-generation fallback path is allowed.
3. **Compatibility before enrichment**: preserve current `validationConditions` consumer contract; metadata additions must be optional and non-breaking.
4. **Shape is restored upstream, preserved downstream**: request-object structure must be reconstructed before export, not guessed by the exporter.
5. **Prefer omission to contamination**: ambiguous service-hint evidence must be dropped, not guessed.

## Decision Drivers
1. **Strict graph-backed `SERVICE_HINT` contract** is the primary policy change and must be testable.
2. **`ddd-start2` acceptance bar** requires both structure recovery (`OrderRequest`) and service/domain validation recovery.
3. **Existing output compatibility** must hold while expanding correctness across unit, assembly, and regression layers.

## Viable Options

### Option A — Preferred: strict endpoint-scoped graph gating in normalization, upstream shape restoration, exporter preservation
**Shape**
- `ValidationExtractionService` continues to collect service-hint candidates and request bindings.
- `ValidationEvidenceGraphBuilder` provides endpoint-scoped reachable evidence.
- `RuleBasedConditionNormalizer` owns the strict `SERVICE_HINT` match contract and rejection behavior.
- `ExecutionSpecExporter` preserves recovered request shape and normalized condition paths without redefining policy.

**Pros**
- Fits the current architecture with minimal churn.
- Keeps graph authority where promotion actually happens.
- Avoids exporter-only masking and avoids introducing a new intermediate abstraction.
- Makes behavior directly unit-testable and assembly-testable.

**Cons**
- Requires precise path handling across extractor, normalizer, and exporter.
- Requires explicit warning/debug wiring for rejected hints.

### Option B — Pre-normalization candidate filtering by endpoint subgraph
**Shape**
- Introduce a new filtering concept before normalization that pre-qualifies `SERVICE_HINT` candidates using graph reachability.
- Normalizer assumes all remaining service hints are graph-backed.

**Pros**
- Clear conceptual separation between qualification and normalization.
- Could simplify normalizer branching.

**Cons**
- Larger refactor than needed for phase 1.
- Adds a new internal contract not evident in the current flow.
- Increases risk of touching more layers than the named production path and tests.

### Option C — Export-time suppression of non-graph-backed service hints
**Pros**
- Smallest code change.

**Cons**
- Rejected as invalid: wrong ownership layer, leaves internal semantics inconsistent, weakens unit coverage, and risks leaking forbidden hints to non-export consumers.

## Invalidation rationale for weaker alternatives
- **Option C is invalid** because policy enforcement must happen before or during normalization, not only at serialization.
- **Option B remains viable but weaker than Option A** because it introduces unnecessary new staging for a phase-1 change that the current architecture can absorb in the normalizer plus graph builder.
- **Option A remains preferred** because the required revisions still fit within the existing extractor → graph builder → normalizer → exporter flow while satisfying strictness, observability, and compatibility constraints.

## In scope
- Strict graph-backed `SERVICE_HINT` matching and promotion.
- Reachable / unreachable / ambiguous case handling.
- Mandatory observability for rejected/ambiguous `SERVICE_HINT`.
- `@ModelAttribute OrderRequest` shape recovery ownership and preservation.
- Nested BODY validation path preservation.
- `ExecutionSpecExporter` compatibility-safe updates.
- Unit, assembly, and `ddd-start2` regression planning.

## Out of scope / non-goals
- PromptBuilder or any LLM/prompt activation.
- Broad MVC view-response redesign.
- Mandatory Web UI work.
- Repo-specific symbol-name rules beyond reusable rule classes.
- Breaking schema changes for `validationConditions`.

## SERVICE_HINT ↔ graph matching contract
This contract is now explicit and mandatory for phase 1.

### Candidate input
A `SERVICE_HINT` candidate must carry, or be matchable to, these fields at normalization time:
- endpoint identity: HTTP method + endpoint path
- origin hint evidence: service method call / evidence snippet / source trace
- normalized target path candidate
- source type = `SERVICE_HINT`

### Graph evidence input
`ValidationEvidenceGraphBuilder` must produce endpoint-scoped evidence that is queryable by:
- endpoint node identity
- reachable service-method nodes from that endpoint
- reachable business-rule nodes descending from those service methods
- optional exception/status evidence attached to those business rules

### Match rule
A `SERVICE_HINT` condition may be promoted only if all of the following hold:
1. the candidate belongs to the current endpoint under normalization
2. the graph contains the endpoint node for that endpoint
3. there is at least one reachable path:
   `ENDPOINT -> CALLS -> SERVICE_METHOD -> (CALLS)* -> BUSINESS_RULE`
   or equivalent endpoint-scoped service/business evidence chain
4. the reachable rule evidence is semantically consistent with the candidate’s hinted condition class
   - examples: permission, state, existence, version-check
5. the matched evidence is not contradicted by multiple unrelated reachable rule classes such that the candidate cannot be resolved deterministically

### Reachable case
Promote the `SERVICE_HINT` into a normalized `ApiCondition` when:
- there is exactly one deterministically qualifying rule class, or
- multiple reachable nodes support the same rule class without contradiction

**Expected result**
- condition emitted
- no rejection warning
- optional debug metadata may note supporting graph node IDs/evidence

### Unreachable case
Reject the `SERVICE_HINT` when:
- the endpoint has no graph node
- the endpoint has no reachable service method
- the reachable service method chain contains no qualifying business-rule evidence
- only unrelated endpoint/service evidence exists

**Expected result**
- no condition emitted
- mandatory warning emitted
- optional debug metadata may include rejection reason such as `UNREACHABLE_GRAPH_EVIDENCE`

### Ambiguous case
Reject the `SERVICE_HINT` when:
- more than one reachable rule class could apply and the rule cannot be resolved deterministically
- evidence is reachable but endpoint association is unclear
- the same hint could be attributed to multiple unrelated targets or semantic classes
- graph path exists but does not support a stable condition operator/expected pair

**Expected result**
- no condition emitted
- mandatory warning emitted
- optional debug metadata may include candidate ID, competing node IDs, and ambiguity code such as `AMBIGUOUS_GRAPH_EVIDENCE`

### Forbidden shortcuts
These must not qualify a `SERVICE_HINT`:
- empty graph treated as success
- existence of any service method anywhere in the graph
- matching based only on repository-specific symbol names
- exporter-time masking instead of normalization-time rejection

## Observability path for rejected or ambiguous hints
This path is now explicit.

### Mandatory
Rejected or ambiguous `SERVICE_HINT` cases must emit an `IngestionWarning`-style warning on the production pipeline path, attached at the earliest stage that can state the reason accurately without guessing.

**Ownership**
- **Normalizer-owned reason**: if the candidate reaches normalization and fails reachable/ambiguous checks, the rejection warning is owned by the normalization path.
- **Graph-owned supporting detail**: graph builder may expose evidence shape, but it should not decide final rejection messaging unless normalization never runs.

**Minimum warning payload**
- stable code, e.g. `SERVICE_HINT_REJECTED` or `SERVICE_HINT_AMBIGUOUS`
- endpoint identity
- candidate source trace or evidence snippet
- machine-meaningful reason category:
  - `UNREACHABLE_GRAPH_EVIDENCE`
  - `NO_QUALIFYING_RULE`
  - `AMBIGUOUS_GRAPH_EVIDENCE`
  - `CROSS_ENDPOINT_CONTAMINATION_BLOCKED`

### Optional
Optional debug metadata may be added non-breakingly if it can be carried without changing current consumers:
- candidate ID
- supporting / competing graph node IDs
- matched rule class
- rejection reason detail
- endpoint-scoped subgraph summary

### Not allowed
- silent dropping of rejected/ambiguous hints
- mandatory consumer-visible schema change to carry observability
- debug metadata as the only observability path

## `@ModelAttribute OrderRequest` shape recovery path
This path is now explicit.

### Ownership
**Primary ownership: extraction/request-binding analysis layer**  
`ValidationExtractionService` is the layer that must preserve enough request-binding/type information for `OrderRequest` to remain a structured request object rather than degrading into leaf-only condition mapping.

### Supporting ownership
- `ValidationEvidenceGraphBuilder` may reference DTO fields for graph context, but it does not own request-shape restoration.
- `RuleBasedConditionNormalizer` must preserve nested target paths and not collapse them to leaf names when the path is already meaningful.
- `ExecutionSpecExporter` must serialize the restored shape faithfully; it must not be responsible for inferring structure that upstream discarded.

### Practical restoration contract
For `@ModelAttribute OrderRequest`-style inputs:
1. extraction must recognize the request object as a business request binding, not a framework infrastructure parameter
2. request binding must retain the DTO type needed for schema expansion
3. condition target paths for nested fields must remain stable enough for body/request schema mapping
4. exporter must expand schema from the DTO type and merge body-related conditions onto that schema without flattening structure to final-segment-only paths

### Reach of exporter behavior
Current `bodySchema` behavior is preservation-only in the revised plan:
- exporter should continue to build schema from the request DTO type
- exporter may normalize path formatting for output
- exporter must not become the first layer to reconstruct missing `OrderRequest` shape if extraction already lost it

### Acceptance implication
`ddd-start2` passes only if `/orders/orderConfirm` and `/orders/order` recover the `OrderRequest` request shape through this upstream-owned path, and exporter output reflects it.

## File-level change plan

### `src/main/java/io/atworks/specscan/analysis/application/ValidationExtractionService.java`
- Ensure `@ModelAttribute`-style complex request objects remain recognized as structured request bindings.
- Add or tighten general Spring MVC infrastructure-type filtering so business DTOs like `OrderRequest` are preserved.
- Keep service-hint extraction broad, but do not let extraction decide graph-backed validity.
- Ensure enough endpoint/candidate/source-trace identity survives into normalization for graph matching and warning emission.

### `src/main/java/io/atworks/specscan/analysis/support/ValidationEvidenceGraphBuilder.java`
- Guarantee endpoint-scoped graph identity for each endpoint.
- Ensure reachable paths from endpoint to service method to business rule can be queried deterministically.
- Make cross-endpoint contamination detectable by construction.
- Preserve only the graph detail needed to distinguish reachable, unreachable, and ambiguous service-hint evidence.

### `src/main/java/io/atworks/specscan/analysis/support/RuleBasedConditionNormalizer.java`
- Replace permissive empty-graph success with the explicit match contract above.
- Keep `CUSTOM_ANNOTATION` and `VALIDATOR` graph-independent.
- Preserve nested target paths instead of collapsing all paths to leaf names.
- Implement reusable rule-class matching for permission/state/existence/version-check classes only.
- Emit mandatory warnings for unreachable/ambiguous `SERVICE_HINT` rejection.
- Allow optional additive debug metadata if it does not alter existing consumer expectations.

### `src/main/java/io/atworks/specscan/analysis/support/ExecutionSpecExporter.java`
- Preserve current `validationConditions` structure.
- Serialize recovered nested/body paths faithfully.
- Use request-binding DTO type to expand request/body schema so `OrderRequest` shape survives.
- If optional debug metadata is serialized anywhere, make it additive and ignorable.

### `README.md`
- Update only if maintainers need the pipeline description corrected from LLM-shaped wording to deterministic graph-backed `SERVICE_HINT` wording after implementation lands.

## Sequencing and dependencies
1. **Lock policy and output constraints**: strict `SERVICE_HINT` graph contract, warning obligations, no LLM path, no consumer breakage.
2. **Fix request-shape ownership upstream**: preserve `@ModelAttribute OrderRequest` as structured binding and stabilize nested target paths.
3. **Strengthen endpoint-scoped graph evidence**: make reachable/unreachable/ambiguous determination possible.
4. **Implement normalizer contract**: graph-gate only `SERVICE_HINT`, emit warnings on rejection, preserve other normalization paths.
5. **Preserve export compatibility**: ensure recovered shape and conditions serialize under existing consumer semantics.
6. **Prove with layered regression**: unit → assembly → `ddd-start2`.

## Acceptance criteria
1. `SERVICE_HINT` promotion occurs only when endpoint-scoped reachable graph evidence satisfies the explicit contract.
2. Empty, missing, unrelated, or unreachable graph evidence does not promote a `SERVICE_HINT`.
3. Ambiguous reachable graph evidence does not produce a condition.
4. Every rejected/ambiguous `SERVICE_HINT` leaves a mandatory warning; debug metadata is optional only.
5. Annotation and custom-validator conditions remain graph-independent.
6. `@ModelAttribute OrderRequest` shape is restored for `/orders/orderConfirm` and `/orders/order`.
7. Nested BODY validation paths for `/orders/order` are preserved well enough to surface multiple nested validations in output.
8. Service/domain endpoints such as `/my/orders/{orderNo}/cancel` recover permission/state-class conditions.
9. `ExecutionSpecExporter` preserves existing `validationConditions` consumer compatibility.
10. Unit, assembly, and `ddd-start2` regression all pass before phase-1 completion.

## Verification

### Unit plan
- `ValidationExtractionServiceTest`
  - preserves complex `@ModelAttribute` request DTO binding
  - filters framework infrastructure params without removing business DTOs
  - preserves service-hint candidate identity needed for later graph match
- `NormalizationServiceTest`
  - reachable graph-backed `SERVICE_HINT` promotes
  - unreachable graph-backed `SERVICE_HINT` rejects with warning
  - ambiguous graph-backed `SERVICE_HINT` rejects with warning
  - unrelated endpoint evidence does not leak
  - annotation/custom-validator normalization unchanged by graph absence
  - nested target paths remain intact

### Assembly plan
- `OpenApiAssemblyServiceTest`
  - `validationConditions` contract preserved
  - nested BODY validations exported correctly
  - service/domain graph-backed conditions surface correctly
  - rejected/ambiguous service hints do not appear as conditions
- `ExecutionSpecExporter`-focused assertions
  - request schema expansion for `OrderRequest`
  - preserved path/query/header/body mapping
  - no flattening that destroys nested body semantics
  - additive-only metadata behavior if debug fields exist

### `ddd-start2` regression plan
Assert the representative endpoints satisfy the manual acceptance bar:
- `/orders/orderConfirm` and `/orders/order` expose `OrderRequest` shape
- `/orders/order` restores multiple nested BODY validations
- `/my/orders/{orderNo}/cancel` restores permission/state-class service/domain validations
- unrelated endpoints do not gain stray service hints
- rejected ambiguous service hints are absent from conditions and visible through warnings/debug evidence as planned

## Escalation / Risk Gate
Pause for approval if any of these emerge:
1. graph ambiguity/reachability cannot be expressed without new domain contracts beyond the planned files
2. `@ModelAttribute` shape restoration requires invasive controller scanning changes outside the intended production path
3. exporter compatibility cannot be preserved without changing downstream consumers
4. `ddd-start2` recovery appears to require repo-specific symbol-name hacks instead of reusable rule classes

## Verification Plan
- **Proof order**: extractor/path fidelity → graph reachability strictness → normalizer warning/rejection semantics → exporter compatibility → `ddd-start2`
- **Key positive proofs**
  - endpoint-scoped reachable service hints promote
  - `OrderRequest` request shape survives into export
  - permission/state/domain rules appear where expected
- **Key negative proofs**
  - empty graph does not promote
  - cross-endpoint graph does not contaminate
  - ambiguous evidence produces warning, not condition
- **Exit bar**
  - unit + assembly + `ddd-start2` all green
  - no breakage in `validationConditions` consumer-facing shape

## Risks and mitigations
- **Risk: reachable graph detection is too permissive.**  
  **Mitigation:** require endpoint-rooted path evidence, not mere graph presence.
- **Risk: ambiguous evidence is detected but not surfaced.**  
  **Mitigation:** make warning emission mandatory in normalization-time rejection.
- **Risk: nested target paths are lost before export.**  
  **Mitigation:** treat path preservation as an upstream contract and test pre-export and exported forms.
- **Risk: `OrderRequest` shape still depends on exporter heuristics.**  
  **Mitigation:** enforce extractor/request-binding ownership and test DTO-based schema expansion explicitly.
- **Risk: rule recovery drifts into repo-specific matching.**  
  **Mitigation:** constrain additions to reusable semantic classes only.

## Open assumptions
- Warning transport already exists or can be extended non-breakingly on the current pipeline path.
- `ddd-start2` expected outputs are acceptance-oriented, not strict snapshot-equality only.
- Optional debug metadata is strictly additive and may be deferred if warnings already satisfy observability.
- The current named tests either exist or are the intended extension points for this work.

## Compact RALPLAN-DR
- **R**: Recover `OrderRequest` shape and service/domain validations with strict graph-backed `SERVICE_HINT` only.
- **A**: Keep Option A; make graph match, warning path, and shape ownership explicit in extractor → graph → normalizer → exporter flow.
- **L**: No LLM/prompt path, no breaking consumer change, no broad MVC/UI scope.
- **PLAN-DR**: Architect review should stress graph contract and shape ownership; Critic review should stress ambiguous-case rejection, warning guarantees, and export compatibility.
