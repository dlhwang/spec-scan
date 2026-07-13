# Planner RALPLAN Stage 3 (Local Fallback Revision) — spen-scan validation condition / graph context improvement

## Delta from Stage 2
This revision closes the remaining explicit gaps from Architect/Critic review:
1. normalization-time warning transport is now mandatory and precisely owned
2. restored `@ModelAttribute OrderRequest` export shape is now precisely represented
3. rule-class and ambiguity matching are now spelled out without repo-specific symbol dependence

## Preferred plan
Keep Option A. The production path remains:
`ValidationExtractionService -> ValidationEvidenceGraphBuilder -> RuleBasedConditionNormalizer -> ExecutionSpecExporter`
with no new pre-normalization stage, no LLM path, and no exporter-time policy masking.

## Exact normalization-time warning transport contract
### Owner
`RuleBasedConditionNormalizer` owns the final rejection decision for `SERVICE_HINT` and therefore owns the mandatory warning emission contract whenever a candidate reaches normalization and is rejected as unreachable or ambiguous.

### Representation
Warnings are emitted as pipeline warnings on the same production path already used for scan/extract/normalize warnings.
Minimum fields per warning:
- code: `SERVICE_HINT_REJECTED` or `SERVICE_HINT_AMBIGUOUS`
- endpoint: `{method} {path}`
- candidateId when available
- targetPath candidate
- sourceTrace or evidence snippet
- reasonCategory: `UNREACHABLE_GRAPH_EVIDENCE` | `NO_QUALIFYING_RULE` | `AMBIGUOUS_GRAPH_EVIDENCE` | `CROSS_ENDPOINT_CONTAMINATION_BLOCKED`
- severity: `MEDIUM`

### Acceptance implication
A rejected/ambiguous hint is not considered fully handled unless:
- no `ApiCondition` is produced, and
- at least one warning with one of the above codes/categories is present.
Optional debug metadata may exist, but warnings are the mandatory proof.

## Exact export representation for restored `@ModelAttribute OrderRequest` shape
### Upstream ownership
`ValidationExtractionService` must preserve `RequestBinding` identity so `@ModelAttribute OrderRequest` stays a structured business request binding rather than collapsing into a simple string/leaf path.

### Export representation
`ExecutionSpecExporter` must represent restored shape in the existing request/body structure without changing the consumer contract:
- `operations[n].request.bodySchema` remains the canonical schema container.
- For `/orders/orderConfirm` and `/orders/order`, `bodySchema` must expand the `OrderRequest` DTO tree so callers can see nested structure:
  - `orderProducts[] { productId, quantity }`
  - `ordererMemberId { id }`
  - `shippingInfo { address { zipCode, address1, address2 }, message, receiver { name, phone } }`
- `validationConditions` remains a flat array of conditions referencing stable nested JSON paths such as:
  - `$.orderProducts`
  - `$.orderProducts[*].productId`
  - `$.shippingInfo.receiver.name`
  - `$.shippingInfo.address.zipCode`
- No new required top-level export field is introduced.
- Optional debug metadata, if present, must be additive and ignorable.

### Not allowed
- exporter inventing shape after upstream loss
- collapsing nested validation paths to final leaf names only
- replacing `validationConditions` with a new schema or envelope

## Exact reusable rule-class contract
The normalizer may map reachable graph-backed `SERVICE_HINT` evidence only into these reusable rule classes for phase 1:
- permission
- state
- existence
- version-check

Allowed recognition sources:
- operator/evidence semantics from reachable business-rule snippets
- exception/status context on the same reachable rule path
- stable path/target semantics already attached to the candidate

Not allowed:
- repo-specific symbol-name shortcuts as deciding evidence
- matching solely because a method name happens to contain project-specific vocabulary

## Exact ambiguity contract
A `SERVICE_HINT` is ambiguous when any of the following holds:
- more than one rule class is equally supportable from the same endpoint-scoped reachable evidence
- reachable evidence supports the class but not a stable operator/expected pair
- the same candidate can be attached to more than one target path without deterministic tie-break
- the graph path exists but ownership cannot be attributed to the current endpoint with confidence

Result:
- no condition
- mandatory warning
- optional additive debug metadata only

## File-level implementation plan
1. `ValidationExtractionService`
   - preserve structured `@ModelAttribute` request bindings
   - filter known Spring MVC infrastructure parameters generically when they would pollute validation accuracy
   - preserve candidate identity fields needed for later warning emission and graph matching
2. `ValidationEvidenceGraphBuilder`
   - guarantee endpoint-rooted reachability from endpoint → service method → business rule
   - expose enough evidence to distinguish reachable/unreachable/ambiguous
3. `RuleBasedConditionNormalizer`
   - enforce strict graph-backed promotion only for `SERVICE_HINT`
   - preserve graph independence for annotation/custom validator paths
   - emit mandatory rejection/ambiguity warnings
   - preserve nested target paths
4. `ExecutionSpecExporter`
   - preserve existing `validationConditions` contract
   - serialize `bodySchema` using restored `OrderRequest` DTO shape
   - carry nested JSON paths faithfully
   - optionally surface additive debug metadata only if low-risk

## Acceptance criteria
- `SERVICE_HINT` produces a condition only with endpoint-scoped reachable graph evidence.
- Empty, missing, unrelated, or ambiguous graph evidence never produces a `SERVICE_HINT` condition.
- Each rejected/ambiguous `SERVICE_HINT` leaves a mandatory warning with the defined contract.
- `/orders/orderConfirm` and `/orders/order` export restored `OrderRequest` shape in `request.bodySchema`.
- `/orders/order` exports multiple nested BODY validations through unchanged `validationConditions` semantics.
- `/my/orders/{orderNo}/cancel` exports permission or state class service/domain validations.
- Existing `validationConditions` consumers remain compatible.
- Unit + assembly + ddd-start2 regression all pass before execution completion.

## Verification additions
- Add explicit assertions for warning presence and reasonCategory in normalization/assembly tests.
- Add explicit assertions that `bodySchema` contains the nested `OrderRequest` tree for both order endpoints.
- Add explicit assertions that nested validation paths remain nested in export, not leaf-collapsed.

## Open assumptions
- Warning transport can reuse the existing warning channel already present in scan/extract/normalize outputs.
- `bodySchema` for `@ModelAttribute` endpoints is acceptable as the compatibility-preserving place to expose structured request shape.
- Optional debug metadata may still be skipped if warnings cover observability sufficiently.

## Compact RALPLAN-DR
- Result: strict graph-backed SERVICE_HINT, mandatory warning path, preserved OrderRequest shape, stable validationConditions export.
- Approach: upstream shape restoration, endpoint-rooted graph reachability, normalization-owned rejection, exporter preservation only.
- Limits: no LLM path, no Web UI requirement, no breaking schema change, no repo-specific symbol hacks.
