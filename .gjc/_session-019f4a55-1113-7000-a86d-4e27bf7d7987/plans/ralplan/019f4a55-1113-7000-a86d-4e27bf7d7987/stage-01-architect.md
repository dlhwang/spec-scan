## Summary
The planner is pointed at the right files and correctly rejects export-time masking, but it leaves three acceptance-critical ownership gaps unresolved. In its current form it does not yet prove how ambiguous `SERVICE_HINT`s emit evidence, how graph backing is tied to a specific candidate, or how `@ModelAttribute` request shape is serialized without breaking the contract.

## Claims
- Option A is directionally correct in keeping graph authority out of annotation/custom-validator normalization and out of export-only filtering.
- The current production seams do not support the planner's ambiguous-case and `@ModelAttribute` acceptance criteria without additional contract decisions.
- Approval should wait until the plan assigns explicit ownership for candidate-level graph backing, observability, and non-BODY request-shape export.

## Analysis
The strongest part of the plan is its rejection of Option C: `ExecutionSpecExporter` should not become the place where forbidden service hints are hidden after normalization. That matches the code path, where `RuleBasedConditionNormalizer` is the policy seam for `SERVICE_HINT` promotion.

The weak part is that the preferred Option A stays too endpoint-centric for the stated acceptance bar. `ValidationEvidenceGraphBuilder` currently receives `ValidationExtractionResult` but never uses it, and it builds endpoint→method call edges plus generic business-rule nodes. `RuleBasedConditionNormalizer.hasServiceRuleEvidence(...)` then reduces that graph to a yes/no endpoint check. Tightening that boolean gate is not enough to distinguish "this candidate is backed" from "this endpoint calls some service somewhere," nor is it enough to classify same-endpoint ambiguity.

The plan also leaves observability underspecified in a way that is architectural, not cosmetic. The normalizer currently has an `Optional<ApiCondition>` return and no warning side channel, while `ExecutionSpecExporter` only emits `warningCount` from `StaticScanResult`. Saying "warnings are the default" is not implementable until the plan names which interface grows or commits to additive exporter metadata as the accepted evidence path.

`@ModelAttribute` recovery is similarly under-owned. The exporter only materializes request schema when a BODY binding exists; otherwise it returns null body schema/example. If the target regression expects an `OrderRequest` shape for non-BODY complex bindings, the plan must explicitly decide whether that shape is represented through an existing body-like field, expanded query/form structure, or another additive contract surface.

## Root Cause
The plan treats three contract decisions as implementation details: candidate-to-graph ownership, ambiguity observability, and complex non-BODY request-shape serialization. In this codebase those are boundary decisions, so leaving them implicit makes the execution plan incomplete.

## Findings
- HIGH — stage-01-planner.md:101. Define a concrete observability channel for dropped service hints. The current seams only allow silent `Optional.empty()` drops in the normalizer and a top-level `warningCount` in the exporter.
- HIGH — stage-01-planner.md:24-27. Add candidate-level graph ownership instead of endpoint-only gating. Endpoint reachability alone cannot satisfy strict backing and ambiguity rejection for a specific `SERVICE_HINT` candidate.
- HIGH — stage-01-planner.md:105. Choose the serialization contract for `@ModelAttribute` request objects. The exporter currently only builds request schema for BODY bindings, so acceptance criterion 5 depends on an unstated contract decision.

## Recommendations
1. Revise Option A so the graph builder or an adjacent selector produces candidate-addressable backing evidence, not just endpoint reachability.
2. Pick one observability contract now: either extend normalization/assembly warnings explicitly, or define additive exporter metadata as the required ambiguity evidence path.
3. Decide where non-BODY complex request objects are represented in the execution spec before implementation starts, and test that contract directly.
4. After those choices, keep the rest of Option A: graph authority only for `SERVICE_HINT`, path preservation, and export compatibility.

## Architectural Status
BLOCK

## Code Review Recommendation
REQUEST CHANGES

## Tradeoffs
- **Revised Option A:** smallest churn if it grows candidate-level backing and one explicit observability contract.
- **Option B:** more refactor, but cleaner if candidate/subgraph ownership cannot be expressed inside the current normalizer contract.
- **Option C:** still invalid because it hides policy failure at serialization time.
