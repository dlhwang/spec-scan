# YAML Rule Engine PoC U04 — Core Semantic Recipes

## Scope

U04 validates four Spring MVC/Java semantic classifier families as generic YAML recipe compositions:

- binary failure guard
- composite requirement (`&&`/`||` style)
- null/empty guard
- JDK `Optional` lookup failure

Recipes bind runtime graph evidence to typed target/operator/value slots. No repository class, method name, domain field, literal, or `custom_expression` is embedded in the recipe definitions.

## Evidence

- `CoreSemanticRecipeTest` compiles all four recipes through the strict U02 schema/compiler.
- The same four recipes resolve against two renamed candidate graphs (`OrderController.check`, `AccountController.validate`) without recipe changes.
- Missing graph evidence remains `UNRESOLVED`; the executor does not synthesize a constraint.
- Targeted U03/U04 runtime tests: **BUILD SUCCESSFUL**.

## Boundary

This is an evaluation-only runtime proof. The normal Java-authoritative scan path is unchanged. Actual FactGraph-to-`EvaluationGraphView` adaptation and golden-corpus parity remain follow-up work.
