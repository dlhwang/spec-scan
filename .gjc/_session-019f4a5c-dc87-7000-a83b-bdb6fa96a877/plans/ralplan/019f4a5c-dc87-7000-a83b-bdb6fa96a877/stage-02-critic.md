## Verdict
**ITERATE**

## Claim Checks
- The revised plan correctly targets the two verified source mismatches that block the goal today: `RuleBasedConditionNormalizer.hasServiceRuleEvidence(...)` currently treats an empty graph as success, and both `RuleBasedConditionNormalizer.normalizeTargetPath(...)` and `ExecutionSpecExporter.normalizeConditionName(...)` collapse nested paths to the final segment, so the strict graph gate and nested-path preservation work are real and well-placed.
- The preferred ownership split mostly matches the code: `ValidationExtractionService` gathers DTO/service-hint inputs, `ValidationEvidenceGraphBuilder` already emits endpoint/service/business-rule nodes and edges, `RuleBasedConditionNormalizer` is where `SERVICE_HINT` promotion currently happens, and `ExecutionSpecExporter` is downstream serialization rather than policy.
- The plan is strong on policy, scope, and negative cases: it clearly rejects exporter-time masking, repo-specific symbol hacks, LLM fallback, and silent ambiguous promotion.

## Missing Evidence
- **Definite gap — warning transport is not implementable from the named file plan alone.** In the verified code, `RuleBasedConditionNormalizer.normalize(...)` returns only `Optional<ApiCondition>`, `ValidationExtractionService` warnings are extraction-only, and `ExecutionSpecExporter` just reports `scanResult.warnings().size()`. The plan requires mandatory normalization-time warnings for rejected/ambiguous `SERVICE_HINT`, but it does not name the concrete carrier/owner file(s) or result-contract changes needed to move those warnings through the pipeline.
- **Thin area — `@ModelAttribute OrderRequest` shape representation is still ambiguous at export time.** The exporter currently expands schema only for a `BODY` binding (`buildRequest(...)` finds the first `BindingLocation.BODY` and otherwise emits `bodySchema: null`). The plan says shape restoration is upstream-owned and exporter is preservation-only, but it does not specify whether the implementation must reclassify these request objects as `BODY`, add a separate structured request-object representation, or teach exporter to expand complex non-body bindings without violating compatibility.
- **Thin area — semantic rule-class matching needs one more concrete contract.** The graph builder currently emits raw `if` condition labels, service-method nodes, and optional exception/status edges, but the plan does not pin down what the classifier actually reads to distinguish permission/state/existence/version-check classes or how ambiguity is computed from those raw nodes.
- Test references (`ValidationExtractionServiceTest`, `NormalizationServiceTest`, `OpenApiAssemblyServiceTest`, `ddd-start2` regression) were not verifiable under the assignment’s read restrictions.

## Approval Boundary
Execution is approved only for the already-grounded parts of the plan: replacing empty-graph success, preserving nested target paths, and keeping graph authority limited to `SERVICE_HINT` in the existing extractor → graph → normalizer → exporter flow. Execution is **not** approved yet for the mandatory warning behavior or the `@ModelAttribute` shape recovery/export contract until the planner names the exact transport contract/files and the concrete exported representation.

## Summary
- Clarity: strong on policy and scope; weak on two implementation contracts.
- Verifiability: good for graph-gating/path-preservation behavior; incomplete for warning transport and structured `@ModelAttribute` export.
- Completeness: missing the pipeline contract that carries normalization warnings and the exact representation contract for restored request shape.
- Big Picture: aligned with the real production path and current defects.
- Principle/Option Consistency: consistent; Option A still fits the code best.
- Alternatives Depth: sufficient.
- Risk/Verification Rigor: strong on negative cases, but the highest-risk plumbing step is still implicit.

## Required Changes
1. Expand the plan with the exact warning transport path for rejected/ambiguous `SERVICE_HINT`: which result object(s) and file(s) are allowed to change, who appends the warning, and where the warning is asserted.
2. Expand the plan with the exact `@ModelAttribute OrderRequest` export contract against the current exporter shape: whether these bindings become `BODY`, gain a new structured request slot, or are exported some other compatibility-safe way.
3. Add one concrete implementation contract for rule-class classification/ambiguity resolution using the graph that actually exists today (for example, which node/edge labels are inspected and what makes two reachable rules “ambiguous”).
