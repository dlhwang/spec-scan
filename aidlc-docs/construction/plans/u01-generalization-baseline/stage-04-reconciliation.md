# Stage 4: Intent Reconciliation — U01 Generalization Baseline

## Intent Mapping Matrix

| ID | Spec Decision / Constraint | Implementation Target | Status |
| :--- | :--- | :--- | :---: |
| D01 | 기존 evaluation package additive 확장 | Planner File Plan #2~28, #46 | ✅ MATCH |
| D02 | legacy evaluation 의미/fixture 유지 | Planner Principle #1/#12, File Plan #46, legacy regression command | ✅ MATCH |
| D03 | checked-in 현실형 Spring MVC corpus 5개 | Planner File Plan #14, #37, #40, #44 | ✅ MATCH |
| D04 | rename/mutation holdout isolation | Planner File Plan #3, #20~22, #32, #37, #40, #45 | ✅ MATCH |
| D05 | optional RealEstate snapshot | Planner File Plan #29, #39~40; Critic C3 resolution | ✅ MATCH |
| D06 | reviewed resources/generated build report 분리 | Planner Principle #10, File Plan #1, #27, #40~45 | ✅ MATCH |
| D07 | append-only bundle/completion last/incomplete 보존 | Planner File Plan #10, #26~28, #35; Architect Condition #7 | ✅ MATCH |
| D08 | explicit 30초/10분/30분 profile | Planner File Plan #5, #17, #33, #42~43 | ✅ MATCH |
| D09 | nanoTime + checkpoint heap observation | Planner File Plan #17~18, #33 | ✅ MATCH |
| D10 | strict v1 reader와 migration contract | Planner File Plan #11~13, #28, #30, #35 | ✅ MATCH |
| C01 | production 정상 scan isolation | Planner Principle #12, File Plan #1/#38/#46; Architect A1/A2/A7 | ✅ MATCH |
| C02 | target build/test/process/network 실행 금지 | Planner File Plan #15~16a/#19/#31/#36; Architect A4 | ✅ MATCH |
| C03 | confined read-only access | Planner File Plan #15~16a/#19/#29/#31; Critic C1 resolution | ✅ MATCH |
| C04 | required preflight fail-fast, no retry | Planner Principle #7, File Plan #14~17/#23/#31/#33 | ✅ MATCH |
| C05 | sequential exact three-run determinism | Planner Principle #9, File Plan #23~25/#34/#37 | ✅ MATCH |
| C06 | metric/timestamp semantic equality 제외 | Planner Principle #6, File Plan #6/#18/#20/#32~34 | ✅ MATCH |
| C07 | explicit disposition/rationale, unlabeled proposal | Planner File Plan #4/#7/#14/#21/#30/#41 | ✅ MATCH |
| C08 | graph/semantic coverage 분리 | Planner File Plan #6/#22/#32/#37 | ✅ MATCH |
| C09 | no auto rebaseline | Planner Principle #10~11, File Plan #21/#27/#37/#41, Sequence #9~11 | ✅ MATCH |
| C10 | stable scoped diagnostics | Planner File Plan #2/#8/#12~18/#23/#28/#30~35 | ✅ MATCH |

## Architect Condition Mapping

| Condition | Enforcement / Test | Status |
| :--- | :--- | :---: |
| A-C1 adapter accepts prepared workspace only | Planner #16a/#19, `JavaBaselineAdapterParityTest` | ✅ MATCH |
| A-C2 proposal never G01 PASS | Planner #8a/#23/#37 | ✅ MATCH |
| A-C3 approved baseline preflight before analyzer | Planner #8a/#23/#37, invocation-count assertion | ✅ MATCH |
| A-C4 materialized digest recheck | Planner #16a/#31 | ✅ MATCH |
| A-C5 renderer fails rather than repairs mismatch | Planner #26/#35 | ✅ MATCH |
| A-C6 label approval is separate gate | Planner Principle #11, Sequence #10, #41 | ✅ MATCH |
| A-C7 no automatic cleanup | Planner #16a/#27, Architect Condition #7 | ✅ MATCH |
| A-C8 minimal public visibility | Planner package principles, final code review checklist | ✅ MATCH |

## Requirements Reconciliation

| Requirement | Acceptance Coverage | Status |
| :--- | :--- | :---: |
| R2-YAML-009 | 5 required corpus, 6 endpoint scale, diversity, holdout, separate coverage | ✅ MATCH |
| R2-YAML-010 | explicit disposition, preserve/replace/unsupported, proposal-only update | ✅ MATCH |
| NFR-R2-001 | canonical ordering and exact three-run equality | ✅ MATCH |
| NFR-R2-003 | no guessed truth; current observation separated from approved expectation | ✅ MATCH |
| NFR-R2-004 | different names/structures and holdout isolation | ✅ MATCH |
| NFR-R2-005 | stable diagnostic and incomplete-state preservation | ✅ MATCH |
| NFR-R2-007 | elapsed/observed heap/graph count metrics separated from semantics | ✅ MATCH |
| NFR-R2-008 | unit, security, integration, snapshot, holdout and full regression tests | ✅ MATCH |
| NFR-R2-009 | no target execution/network, path confinement and isolated materialization | ✅ MATCH |

## Reconciliation Result

- **Matching Coverage**: 100% (20/20 deep-interview decisions/constraints, 8/8 architect conditions, 9/9 assigned requirements)
- **Verdict**: PASS
- **Discrepancies**: 없음
- **Deferred items preserved**: concrete v1→v2 migrator, parallel execution, production CLI/bootstrap, remote corpus registry와 U02 YAML 구현은 plan에 포함되지 않았다.
