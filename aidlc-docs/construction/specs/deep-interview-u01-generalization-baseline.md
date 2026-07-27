# Deep Interview Specification — U01 Generalization Baseline

> **확정 시각**: 2026-07-22T12:36:09.8245377+09:00
> **사용자 응답**: `전부 A`
> **상태**: 확정

## 1. Intent

U01은 기존 Java semantic/rule engine의 현재 관찰 결과를 무조건 정답으로 고정하지 않는다. 서로 다른 현실형 Spring MVC corpus에서 현재 결과를 수집하고, 승인된 `PRESERVE`, `REPLACE`, `UNSUPPORTED` 기대와 비교할 수 있는 재현 가능한 baseline contract와 evidence harness를 제공한다.

## 2. Decisions

### D01 — Additive Evaluation Package

재사용 가능한 immutable contract와 pure service는 기존 `io.atworks.specscan.analysis.domain.evaluation` 및 `io.atworks.specscan.analysis.support.evaluation`에 additive하게 배치한다. filesystem corpus adapter와 JUnit 실행 harness는 test source set에 둔다.

### D02 — Legacy Compatibility

기존 `RuleEvaluationService`, `EvaluationReport`, `QualityGateConfig`, `golden-labels.json`의 의미와 regression test를 유지한다. 신규 corpus/baseline manifest는 별도 strict v1 contract로 추가하며 legacy artifact를 silent conversion하지 않는다.

### D03 — Reproducible Corpus Set

서로 다른 package, aggregate, field, method 이름과 현실적인 controller/service/repository 구조를 가진 checked-in Spring MVC mini-repository fixture 5개를 required corpus로 사용한다. 각 corpus는 약 6개 endpoint 또는 동등한 분석 복잡도를 가진다.

### D04 — Holdout Isolation

base corpus와 이름·구조가 다른 rename/mutation holdout 하나 이상을 tuning input과 분리한다. holdout 이름에 맞춘 application/fixture-specific 분기는 금지한다.

### D05 — Optional RealEstate Snapshot

`REALESTATE_WORKSPACE` local snapshot은 여섯 번째 optional corpus다. 부재는 `EXCLUDED_OPTIONAL` diagnostic으로 기록하되 required G01 evidence를 실패시키지 않는다.

### D06 — Artifact Layout

reviewed v1 manifest/baseline은 `src/test/resources/rule-based-static-analysis/baseline/v1/`에 보관한다. generated proposal/evidence bundle은 `build/reports/specscan/u01/<bundle-id>/`에만 생성한다.

### D07 — Immutable Publication

publisher는 unique bundle directory에 JSON, Markdown, digest를 기록하고 consistency 검증 후 completion manifest를 마지막에 작성한다. 자동 baseline 갱신과 test resource overwrite는 금지한다. incomplete directory는 자동 삭제하지 않고 completed reader가 무시한다.

### D08 — Explicit Evaluation Profiles

모든 실행은 hidden default가 없는 immutable `EvaluationProfile`을 요구한다. checked-in smoke profile은 single run 30초, G01 full profile은 single run 10분, determinism suite guard는 30분이다.

### D09 — Time and Memory Observation

deadline/elapsed time은 `System.nanoTime()` 기반 monotonic clock을 사용한다. heap은 JDK `MemoryMXBean.getHeapMemoryUsage().getUsed()`를 run/corpus/endpoint checkpoint에서 표본화하고 최대값을 `observedHeapPeakBytes`로 기록한다. metric 실패는 diagnostic이며 semantic verdict를 바꾸지 않는다.

### D10 — Strict v1 Reader and Migration Boundary

normal reader는 artifact kind와 exact schema version으로 v1 reader만 선택한다. unsupported version과 migration path 부재는 stable diagnostic이다. `ArtifactMigrator` 및 registry contract와 원본 보존 규칙은 구현하지만 실제 v1→v2 migrator는 v2 schema가 생길 때까지 만들지 않는다.

## 3. Hard Constraints

### C01 — Production Isolation

production 정상 scan entry point와 default Java budget/engine path는 U01 bootstrap, corpus loader, runner, publisher 또는 generated artifact에 의존하지 않는다.

### C02 — No Target Execution

target repository code, build, test, script와 process를 실행하지 않는다. network access와 remote clone/download도 runner capability에 포함하지 않는다.

### C03 — Confined Read-only Access

모든 corpus locator는 configured allowed root 아래 real path descendant여야 한다. symlink/junction/out-of-root escape를 거부하고 analyzer에는 검증된 read-only view만 전달한다.

### C04 — Fail-fast Required Corpus

schema, path, digest, timeout 또는 required corpus preflight 실패 시 analyzer invocation은 0회이며 전체 run을 diagnostic-preserving abort한다. 자동 retry는 없다.

### C05 — Deterministic Reference Execution

G01 evidence는 canonical corpus/endpoint ordering의 sequential executor로 생성한다. 동일 pinned input을 `SingleRunCoordinator`로 정확히 3회 실행한 canonical semantic snapshot이 모두 같아야 한다.

### C06 — Semantic and Metric Separation

timestamp, elapsed time와 heap observation은 semantic identity, baseline diff와 determinism comparison에서 제외한다.

### C07 — Explicit Disposition

모든 approved baseline entry는 non-blank rationale과 `PRESERVE`, `REPLACE`, `UNSUPPORTED` 중 하나를 가진다. unlabeled observation은 자동 승인하지 않고 proposal/gate failure로 남긴다.

### C08 — Coverage Separation

graph coverage와 semantic coverage를 독립적인 분자/분모로 계산한다. graph 부재·truncation을 semantic miss 또는 success로 숨기지 않는다.

### C09 — No Auto Rebaseline

runner와 test는 approved baseline을 덮어쓰거나 자동 승인하지 않는다. 변경 후보는 generated proposal로만 출력하고 사람의 review 후 별도 반영한다.

### C10 — Stable Diagnostics

required/optional availability, preflight failure, timeout, unsupported schema, missing migration, graph incompleteness와 publication failure는 stable code와 scope identity를 가진 diagnostic으로 노출한다.

## 4. Acceptance Commitments

1. checked-in required corpus 5개와 분리된 rename/mutation holdout이 manifest validation을 통과한다.
2. 모든 approved baseline entry에 disposition/rationale가 있고 legacy delegated hardcoding 대상은 `REPLACE`로 식별된다.
3. 같은 pinned input의 세 canonical snapshot과 deterministic JSON payload digest가 동일하다.
4. optional RealEstate 부재와 metric sampling 실패가 semantic result를 바꾸지 않는다.
5. invalid preflight에서 analysis invocation이 0회다.
6. JSON/Markdown summary와 completion manifest의 bundle identity, status와 count가 일치한다.
7. incomplete/corrupt/unsupported-version bundle은 completed reader가 거부한다.
8. 기존 evaluation tests와 normal scan regression은 변경 없이 통과한다.

## 5. Deferred by Design

- concrete v1→v2 artifact migration logic
- parallel corpus/endpoint execution
- production CLI와 production bootstrap wiring
- remote corpus acquisition 또는 artifact registry
- U02 YAML schema/runtime 구현
