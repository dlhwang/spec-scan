# Stage 3: Architect — U01 Generalization Baseline

## Architecture Audit Checklist

### [A1] Layer Boundaries & Separation

- **Result**: PASS
- **Details**:
  - immutable manifest/observation/diff/result contract는 기존 `analysis.domain.evaluation`에 위치한다.
  - I/O, orchestration, canonicalization과 adapter는 `analysis.support.evaluation`에 위치한다.
  - classpath/local fixture locator와 JUnit harness는 test source set에 한정된다.
  - production `analysis.application`은 신규 evaluation package를 참조하지 않으며 신규 support adapter만 기존 application service를 inward dependency로 호출한다.

### [A2] Dependency Directions

- **Result**: PASS
- **Details**:

```text
test harness/resource adapter
  -> BaselineEvaluationFacade
     -> DeterminismVerifier -> SingleRunCoordinator
        -> preflight/materializer -> JavaBaselineAnalysisAdapter
        -> canonicalizer -> differ/coverage
     -> renderer -> publisher

production normal scan
  -> ScanPreparationService / RuleOutputService
  -X-> any U01 facade/runner/publisher
```

  - `DeterminismVerifier`만 `SingleRunCoordinator`를 호출하고 reverse reference가 없다.
  - renderer/publisher는 semantic evaluator/differ를 호출하지 않는다.
  - version reader registry와 migration registry는 서로 자동 연결되지 않는다.
  - adapter는 original corpus path가 아니라 materialized evaluation workspace만 받는다.

### [A3] Domain Integrity

- **Result**: PASS
- **Details**:
  - `CorpusManifest`, `BaselineManifest`, `BaselineObservation`, `BaselineDiff` aggregate가 inventory, truth, fact와 change proposal을 분리한다.
  - disposition default와 auto-labeling이 없어 current Java output이 truth로 승격되지 않는다.
  - exact identity와 scenario identity의 비교 scope가 별도 type으로 표현된다.
  - defensive collection copy와 explicit enum/status가 partial/null state를 제한한다.

### [A4] Security Boundary Feasibility

- **Result**: PASS
- **Details**:
  - Java NIO real-path validation만으로 legacy analyzer를 신뢰하지 않고 confined byte view → isolated workspace materialization → digest recheck를 추가한다.
  - source tree 안의 symlink/junction을 거부하며 downstream에는 controlled workspace만 전달한다.
  - process/network/build/test/class loading port가 component graph에 없다.
  - caller-owned workspace lifecycle은 materializer가 삭제하지 않아 hidden destructive behavior가 없다.

### [A5] Run State and Failure Semantics

- **Result**: PASS
- **Details**:
  - `SNAPSHOT_PROPOSAL`, `G01_DIFF`, `G01_DETERMINISM`은 입력 invariant와 terminal status가 다르다.
  - required failure는 analyzer invocation 0회의 preflight abort이고 optional RealEstate만 exclusion 가능하다.
  - timeout은 monotonic cooperative checkpoint이며 retry/interrupt가 없다.
  - metric failure와 publication failure가 semantic verdict를 재작성하지 않는다.

### [A6] Determinism and Artifact Consistency

- **Result**: PASS
- **Details**:
  - canonicalizer만 raw observation을 semantic comparison model로 바꾼다.
  - three-run verifier는 exact 3회와 1↔2/1↔3 비교를 수행한다.
  - JSON과 Markdown은 동일 completed model을 소비하고 completion manifest가 publication barrier다.
  - host path, timestamp와 metric은 semantic digest에서 제외된다.

### [A7] Backward Compatibility

- **Result**: PASS
- **Details**:
  - 기존 evaluation model/service/resource는 No Change 대상으로 명시됐다.
  - 기존 Gradle task 의미를 바꾸지 않고 opt-in task만 추가한다.
  - `ScanPreparationService`, `RuleOutputService`, default budget 또는 production wiring 변경이 없다.

### [A8] Technical API Feasibility

- **Result**: PASS
- **Details**:
  - Java 21 record/sealed enum, Jackson 2.15, JDK NIO/SHA-256/ManagementFactory만으로 구현 가능하다.
  - core seam은 constructor injection으로 fake clock, heap supplier, analyzer invocation counter와 temporary publisher를 대입할 수 있다.
  - Spring fixture source는 test resources이므로 Spring dependency로 compile하지 않고 existing JavaParser가 source로 분석한다.

## Required Implementation Conditions

1. `JavaBaselineAnalysisAdapter` public API는 original corpus `Path`를 받지 않고 `PreparedEvaluationWorkspace`만 받는다.
2. `SNAPSHOT_PROPOSAL` result는 approved baseline이 없으므로 어떤 경우에도 G01 `PASS`가 될 수 없다.
3. `G01_DIFF`와 `G01_DETERMINISM`은 approval state/version validation 전에 analyzer를 호출하지 않는다.
4. materializer는 source bytes와 materialized bytes의 digest가 다르면 workspace를 분석하지 않는다.
5. `BaselineArtifactRenderer`는 model count/status 불일치를 보정하지 않고 publication을 실패시킨다.
6. initial label approval은 implementation plan 승인과 별개의 사용자 gate로 유지한다.
7. incomplete bundle과 caller-owned evaluation workspace를 자동 삭제하지 않는다.
8. package-private/internal implementation을 우선하고 U02~U06이 소비할 immutable contract만 public으로 노출한다.

## Decision

- **Verdict**: APPROVE
- **Rationale**: revised plan은 domain/support/test 경계, one-way orchestration, legacy analyzer confinement bridge와 normal-scan isolation을 만족한다. 모든 계획 요소는 현재 Java 21/Gradle/Jackson stack에서 구현 가능하며 신규 runtime 또는 production bootstrap 변경이 없다.
- **Conditions**: 위 8개 implementation condition을 Stage 4와 final plan의 hard constraint 및 verification에 100% 매핑한다.
