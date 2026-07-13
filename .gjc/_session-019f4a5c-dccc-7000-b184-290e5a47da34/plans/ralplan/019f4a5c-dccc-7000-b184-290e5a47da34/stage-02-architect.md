## Summary
The revised plan is directionally correct on strict graph-gating and upstream shape ownership, but it still leaves one blocker contract implicit and one compatibility decision underspecified. Revise it before implementation so the warning path and `@ModelAttribute` export shape are explicit, testable, and achievable within the real pipeline.

## Claims
- The plan correctly closes the current permissive `SERVICE_HINT` behavior: `RuleBasedConditionNormalizer.normalizeServiceHint` still accepts an empty graph today because `hasServiceRuleEvidence` returns `true` when nodes and edges are empty (`src/main/java/io/atworks/specscan/analysis/support/RuleBasedConditionNormalizer.java:18-27,59-66,163-166`).
- The plan is right to push request-shape preservation upstream, because the current normalizer and exporter both collapse dotted paths to the leaf segment (`RuleBasedConditionNormalizer.java:244-252`, `ExecutionSpecExporter.java:229-237`).
- The mandatory-warning portion is not fully implementable as planned because the current normalizer returns only `Optional<ApiCondition>` with no warning channel, while the exporter only surfaces `scanResult.warnings().size()` (`RuleBasedConditionNormalizer.java:18-27`, `ExecutionSpecExporter.java:34-48`).
- The `@ModelAttribute OrderRequest` acceptance bar is not fully specified against the current exporter contract, because `ExecutionSpecExporter.buildRequest` emits `bodySchema` only when a `BindingLocation.BODY` binding exists and otherwise returns `null` (`ExecutionSpecExporter.java:68-92`).

## Analysis
The preferred Option A remains the right architectural direction. Keeping graph authority narrow to `SERVICE_HINT`, preserving annotation/validator behavior, and fixing nested-path loss upstream avoids the invalid exporter-only suppression path and matches the current layer boundaries.

The strongest antithesis is that the plan still assumes two contracts that the codebase does not currently have. First, observability for rejected hints is described as mandatory and normalizer-owned, but the only reviewed normalization surface returns an `Optional<ApiCondition>`; there is no reviewed place to carry a warning object forward once a hint is rejected. Second, the exporter contract is body-centric today; if `@ModelAttribute` remains a structured non-BODY binding, upstream preservation alone will not make the request shape appear in output without an explicit representation decision.

A stronger revision keeps Option A but adds two explicit design constraints:
1. introduce a normalization result / aggregated warning contract on the production path so rejected `SERVICE_HINT` cases can emit durable warnings without silent drop;
2. choose and document how structured non-BODY request DTOs are exported compatibly (for example, reclassify them as BODY only if that is semantically accepted by consumers, or add a compatibility-safe structured request object alongside existing query/path/header fields).

## Root Cause
The remaining weakness is not policy; it is unmodeled transport. The plan defines stricter decisions than the current normalization and export contracts can carry, so those contracts must be expanded explicitly rather than assumed.

## Findings
- **HIGH** — `.gjc/_session-019f4998-9528-7000-afbb-36d50c3c2655/plans/ralplan/019f4998-9528-7000-afbb-36d50c3c2655/stage-02-revision.md:160-188` — Mandatory rejection warnings are specified without naming the result/pipeline contract change needed to transport them out of normalization. Fix: add the exact warning-carrying contract and downstream owner.
- **MEDIUM** — `.gjc/_session-019f4998-9528-7000-afbb-36d50c3c2655/plans/ralplan/019f4998-9528-7000-afbb-36d50c3c2655/stage-02-revision.md:190-240` — `@ModelAttribute` shape recovery is assigned upstream, but the exporter representation for structured non-BODY bindings is still unspecified against the current body-only schema contract. Fix: choose the export representation explicitly and state compatibility rules.

## Recommendations
1. Amend the plan to add a normalization warning transport contract before implementation starts.
2. Amend the plan to define the consumer-visible representation of structured non-BODY request DTOs and its backward-compatibility rules.
3. Keep Option A and the strict graph-gating rules unchanged once those two contracts are explicit.

## Architectural Status
BLOCK

## Code Review Recommendation
REQUEST CHANGES

## Tradeoffs
- **Keep Option A + explicit warning/result contract**: lowest policy churn, preserves current layering, but requires a small domain/pipeline contract expansion.
- **Move rejection handling into exporter**: smaller diff, but architecturally wrong because it hides failed normalization and recreates the rejected fallback path.
- **Pre-filter before normalization**: viable, but adds a new stage without solving the warning transport or non-BODY export decision by itself.
