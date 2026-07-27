# YAML Rule Engine PoC U06 — Cross-Repository Evaluation Gate

U06 adds the final evaluation-only decision contract. YAML results are always compared with Java-authoritative results; coverage gaps, fallback use, holdout generalization, and parity differences are reported separately.

Decision policy:

- `GO`: complete coverage, exact parity, no fallback, and holdout evidence.
- `PARTIAL`: evidence exists but coverage/fallback/holdout limitations remain.
- `NO_GO`: corpus scope is empty or Java parity differs.

`CrossRepositoryEvaluationGateTest` covers all three verdicts. It does not change normal scan output or select YAML as production authority.
