# YAML Rule Engine PoC U05 — Framework Pack Activation

U05 adds an evidence-gated activation contract for optional framework packs. A pack is activated only when catalog owner/method identity, resolved type, and graph path evidence all agree.

Negative cases are explicit: unrelated same-name calls, missing type resolution, and missing delegated graph path remain `UNRESOLVED`; no name-only or domain-value guess is substituted.

`FrameworkPackActivationTest` passes. This is still evaluation-only; the normal Java scan and `SemanticRuleDispatcher` authority are unchanged.
