# RALPLAN Final Plan — spen-scan validation condition / graph context improvement

## ADR
### Decision
Adopt the strict endpoint-scoped graph-gating plan for `SERVICE_HINT` recovery while restoring `@ModelAttribute OrderRequest` shape upstream and preserving the existing `validationConditions` export contract.

### Drivers
1. `ddd-start2` acceptance requires both structure recovery and service/domain validation recovery.
2. graph authority must be deterministic, endpoint-scoped, and limited to `SERVICE_HINT`.
3. existing `validationConditions` consumers must not break.

### Alternatives considered
- **Option A (chosen):** strict graph gating in normalizer/export path with upstream shape restoration.
- **Option B:** pre-normalization candidate filtering by subgraph. Rejected as unnecessary extra staging for phase 1.
- **Option C:** export-time suppression only. Rejected because policy must hold before serialization.

### Why chosen
Option A solves the current policy breach (`SERVICE_HINT` surviving without graph-backed evidence), restores `OrderRequest` structure at the right ownership layer, keeps export compatibility intact, and fits the current extractor → graph builder → normalizer → exporter architecture with the least churn.

### Consequences
- `SERVICE_HINT` recall becomes intentionally stricter.
- ambiguous or unreachable service hints are dropped and surfaced through mandatory warnings.
- request-shape fidelity work shifts upstream into extraction/request-binding handling.
- optional debug metadata may be added, but warnings are the required observability path.

### Follow-ups
- Optional additive debug metadata if implementation cost stays low.
- README wording refresh only if deterministic graph-backed behavior materially differs from the current documented pipeline text.
- Broader MVC response-shape cleanup and PromptBuilder/LLM path remain follow-on work.

## Final Plan
### Scope
- Strict endpoint-scoped graph-backed `SERVICE_HINT` promotion only.
- `@ModelAttribute OrderRequest` shape recovery for `/orders/orderConfirm` and `/orders/order`.
- Nested BODY validation path preservation.
- `ExecutionSpecExporter` compatibility-safe serialization updates.
- Unit, assembly, and `ddd-start2` regression verification.

### File plan
1. `src/main/java/io/atworks/specscan/analysis/application/ValidationExtractionService.java`
   - preserve structured `@ModelAttribute` request bindings
   - filter known Spring MVC infrastructure parameters generically when they pollute validation accuracy
   - preserve candidate identity needed for graph matching and warning emission
2. `src/main/java/io/atworks/specscan/analysis/support/ValidationEvidenceGraphBuilder.java`
   - ensure endpoint-rooted reachability to service methods and business rules
   - make reachable / unreachable / ambiguous service-hint evidence distinguishable
3. `src/main/java/io/atworks/specscan/analysis/support/RuleBasedConditionNormalizer.java`
   - enforce strict graph-backed `SERVICE_HINT` contract
   - keep annotation/custom-validator behavior graph-independent
   - preserve nested target paths
   - emit mandatory warnings for rejected/ambiguous service hints
   - map only reusable rule classes: permission/state/existence/version-check
4. `src/main/java/io/atworks/specscan/analysis/support/ExecutionSpecExporter.java`
   - preserve `validationConditions` consumer contract
   - serialize restored `OrderRequest` body shape and nested paths faithfully
   - keep optional metadata additive only

### Exact contracts
#### SERVICE_HINT graph contract
- promote only when endpoint-scoped reachable evidence exists for the same endpoint
- never promote on empty, unrelated, unreachable, or ambiguous evidence
- ambiguous/unreachable cases must emit warnings and no condition

#### Warning contract
Warnings must include code, endpoint identity, candidate source/evidence, and reason category (`UNREACHABLE_GRAPH_EVIDENCE`, `NO_QUALIFYING_RULE`, `AMBIGUOUS_GRAPH_EVIDENCE`, `CROSS_ENDPOINT_CONTAMINATION_BLOCKED`).

#### `OrderRequest` export contract
- restored shape is represented under existing `request.bodySchema`
- nested `validationConditions` paths remain stable JSON paths
- exporter preserves upstream-restored structure and does not invent missing shape

### Verification
#### Unit
- `ValidationExtractionServiceTest`
- `NormalizationServiceTest`

#### Assembly
- `OpenApiAssemblyServiceTest`
- exporter-focused assertions for body schema and nested paths

#### Regression
- `ddd-start2` scenario must show:
  - restored `OrderRequest` shape for `/orders/orderConfirm` and `/orders/order`
  - multiple nested BODY validations for `/orders/order`
  - permission/state-class recovery for `/my/orders/{orderNo}/cancel`
  - no stray cross-endpoint service-hint inflation

### Non-goals
- PromptBuilder/LLM activation
- broad MVC view-response redesign
- mandatory Web UI changes
- repo-specific symbol-name rules

## Intent Reconciliation
- Deep-interview intent remains intact with no unresolved conflicts.
- ddd-start2 is still the representative regression fixture.
- Graph authority remains strict, deterministic, and scoped to `SERVICE_HINT` only.
- Existing `validationConditions` consumers remain protected.
