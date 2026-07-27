# U01 Generalization Baseline — Code Generation Plan

> **상태**: 실행 승인 완료 (2026-07-22T12:48:35.0725639+09:00)
> **Single Source of Truth**: 이 체크리스트가 U01 code generation의 실행 순서다.
> **Consensus File Plan**: `u01-generalization-baseline/stage-01-planner.md`
> **Implementation Intent**: `../specs/deep-interview-u01-generalization-baseline.md`

## 1. Unit Context

- **Unit**: U01 Generalization Baseline
- **Primary requirements**: R2-YAML-009, R2-YAML-010
- **Supporting requirements**: R2-YAML-001, R2-YAML-005~008
- **NFR**: NFR-R2-001, 003~005, 007~009
- **Dependencies**: 기존 Java scan/fact graph/rule/output pipeline만 read/reuse; 선행 구현 Unit 없음
- **Consumers**: U02 corpus/baseline identity contract, U04/U05 fixture expectations, U06 evaluation gate
- **Owned database/API/UI**: 없음
- **Production wiring**: 변경 없음
- **Manual hard gate**: Step 9의 initial baseline disposition/label review는 본 plan 승인과 별도다.

## 2. Expected Contracts

- versioned `CorpusManifest`와 `BaselineManifest`
- current Java fact인 canonical `BaselineObservation`
- exact/scenario `BaselineDiff`와 coverage
- explicit `EvaluationProfile`과 run purpose
- fail-fast preflight + isolated evaluation workspace
- exact three-run determinism result
- versioned JSON/Markdown/completion bundle
- strict v1 reader와 migration registry contract

## 3. Execution Checklist

### Step 1 — Domain and run contracts

- [x] `BaselineDiagnostic.java`, `CorpusManifest.java`, `BaselineManifest.java` 구현
- [x] `EvaluationProfile.java`, `BaselineRunRequest.java`, `BaselineRunResult.java` 구현
- [x] `BaselineObservation.java`, `BaselineDiff.java`, `EvaluationResultBundle.java`, `CompletionManifest.java` 구현
- [x] immutable collection, explicit enum/status, non-blank/positive invariant unit assertions 추가
- **Verification**: compilation + `BaselineManifestContractTest`의 pure contract cases
- **Trace**: R2-YAML-009/010, NFR-R2-001/005

### Step 2 — Strict versioning and manifest validation

- [x] `BaselineJsonCodec.java` strict Jackson v1 codec 구현
- [x] `SchemaVersionReaderRegistry.java` exact reader registry 구현
- [x] `ArtifactMigrationRegistry.java` explicit contract/no-path diagnostic 구현
- [x] `BaselineManifestValidator.java` corpus/scenario/disposition/approval validation 구현
- [x] `BaselineManifestContractTest.java` unknown/duplicate/version/disposition/5~10 corpus negative matrix 구현
- **Verification**: unsupported schema와 missing migration은 analyzer invocation 전 실패
- **Trace**: R2-YAML-009/010, NFR-R2-005/008

### Step 3 — Corpus confinement and integrity

- [x] `CorpusAccessGuard.java` real-path/symlink/junction confinement 구현
- [x] `CorpusIntegrityVerifier.java` canonical SHA-256 구현
- [x] `EvaluationWorkspaceMaterializer.java` confined bytes→caller-owned isolated workspace bridge 구현
- [x] `BaselinePreflightSecurityTest.java` traversal/missing/digest/materialized-recheck/analyzer-zero assertions 구현
- **Verification**: original corpus path가 Java analyzer API에 전달되지 않는 construction test
- **Trace**: NFR-R2-003/009, C02~C04

### Step 4 — Execution controls and Java observation adapter

- [x] `RunDeadline.java` injectable monotonic deadline 구현
- [x] `RunMetricsCollector.java` checkpoint observed heap/elapsed/count 구현
- [x] `JavaBaselineAnalysisAdapter.java` existing scan/graph/rule/output observation 구현
- [x] `BaselineExecutionControlTest.java` sequential/no-retry/timeout/metric-failure cases 구현
- [x] `JavaBaselineAdapterParityTest.java` output + graph candidate parity 구현
- **Verification**: target build/process/network 호출 0; metric failure의 semantic digest 동일
- **Trace**: R2-YAML-009, NFR-R2-001/003/007/009

### Step 5 — Canonicalization, diff and coverage

- [x] `CanonicalSnapshotBuilder.java` exact/scenario identity와 semantic projection 구현
- [x] `BaselineDiffer.java` exact four-way/disposition/holdout/proposal 구현
- [x] `CoverageCalculator.java` graph/semantic numerator/denominator/exclusion 구현
- [x] `BaselineCanonicalDiffTest.java` shuffle/metric/path exclusion, collision, disposition와 rename mapping 구현
- **Verification**: formatting/order/metric 변화는 CHANGED가 아니며 unlabeled observation은 proposal/error
- **Trace**: R2-YAML-009/010, NFR-R2-001/003~005

### Step 6 — Purpose-aware orchestration and determinism

- [x] `SingleRunCoordinator.java` preflight→materialize→sequential analysis→canonical/diff/coverage state machine 구현
- [x] `DeterminismVerifier.java` exact three-run 1↔2/1↔3 comparator 구현
- [x] `BaselineEvaluationFacade.java` explicit mode routing/result assembly 구현
- [x] `BaselineDeterminismTest.java` exact invocation count, suite timeout와 injected nondeterminism 구현
- [x] proposal mode non-pass와 G01 baseline approval preflight tests 구현
- **Verification**: no retry, verifier→coordinator 단방향, invalid approved baseline analyzer 0회
- **Trace**: R2-YAML-009/010, NFR-R2-001/005/007

### Step 7 — Rendering, append-only publication and completed reading

- [x] `BaselineArtifactRenderer.java` same-model deterministic JSON/Markdown 구현
- [x] `ArtifactBundlePublisher.java` unique bundle/payload digest/completion-last 구현
- [x] `CompletedBundleReader.java` completion/schema/payload digest validation 구현
- [x] `BaselineArtifactPublicationTest.java` consistency, overwrite, incomplete/corrupt/version cases 구현
- **Verification**: failure bundle에는 completion manifest가 없고 existing bundle/resource overwrite 0
- **Trace**: NFR-R2-001/005/008/009, C06/C09/C10

### Step 8 — Reproducible corpus, holdout and explicit profiles

- [x] `baseline/v1/corpora`에 catalog/registry/billing/membership/shipping 5개 현실형 Spring MVC mini-repository 추가
- [x] 각 required corpus에 6 endpoint와 서로 다른 package/aggregate/field/method naming 구성
- [x] `holdout/catalog-renamed`에 semantic-equivalent rename/mutation variant 추가
- [x] `corpus-manifest.json`과 canonical content digest 추가
- [x] `profiles/smoke.json` 30초와 `profiles/g01.json` 10분/30분 추가
- [x] `BaselineTestWorkspace.java`, `RealEstateOptionalCorpusTest.java` 구현
- **Verification**: 5 required + separated holdout + optional RealEstate manifest review; holdout은 tuning input에서 제외
- **Trace**: R2-YAML-009, NFR-R2-004/008/009

### Step 9 — Snapshot proposal generation and manual labeling gate

- [x] `U01CorpusEvaluationTest.java`의 `SNAPSHOT_PROPOSAL` path 구현
- [x] `build.gradle`에 opt-in `u01BaselineEvaluation` task 추가
- [x] required corpus observation/coverage/diagnostic/proposal bundle 생성
- [x] generated bundle이 `build/reports/specscan/u01/<bundle-id>/`에만 존재하는지 확인
- [x] corpus 대표성, candidate entries와 delegated hardcoding의 proposed disposition을 사용자에게 제시
- [x] **STOP**: 사용자 label/disposition/rationale 승인 전 `baseline-manifest.json`을 approved로 만들지 않음
- **Verification**: proposal result는 항상 `REVIEW_REQUIRED`; approved resource write 0
- **Trace**: R2-YAML-009/010, BR-U01-060~064, C07/C09

### Step 10 — Approved baseline and G01 evidence

- [x] 별도 승인된 label만 `baseline-manifest.json` v1에 기록
- [x] 모든 entry의 disposition/rationale/expectation completeness 검증
- [x] delegated domain hardcoding을 `REPLACE` 또는 `UNSUPPORTED`로 기록
- [x] `G01_DETERMINISM`으로 3회 실행하고 canonical equality, coverage, diff와 report consistency 검증
- [x] optional RealEstate가 없으면 `EXCLUDED_OPTIONAL`, local digest binding이 있으면 추가 evidence 생성
- **Verification**: `PRESERVE` unexpected diff 0, approved expectation verdict, stable diagnostic와 completion bundle
- **Trace**: R2-YAML-009/010, G01

### Step 11 — Regression, documentation and handoff

- [ ] `NormalScanIsolationTest.java` 구현 및 invalid U01 input과 normal scan 독립성 검증
- [ ] 기존 `RuleEvaluationServiceTest`, migration tests와 전체 `test` 실행
- [ ] `u01BaselineEvaluation` 재실행 및 completed reader로 evidence 검증
- [ ] `aidlc-docs/construction/u01-generalization-baseline/code/implementation-summary.md` 작성
- [ ] requirement/evidence matrix와 미자동 항목(대표성/label 수동 승인)을 명시
- [ ] duplicate suffixed Java file과 production U01 dependency가 없는지 확인
- **Verification**: compile/test/G01 commands 모두 성공하고 evidence link 기록
- **Trace**: U01 Definition of Done 전체, G01 handoff to U02

## 4. Verification Commands

1. `.\gradlew.bat test --tests "io.atworks.specscan.analysis.evaluation.baseline.*"`
2. `.\gradlew.bat u01BaselineEvaluation`
3. `.\gradlew.bat test --tests "io.atworks.specscan.analysis.evaluation.RuleEvaluationServiceTest"`
4. `.\gradlew.bat test --tests "io.atworks.specscan.analysis.migration.*"`
5. `.\gradlew.bat test`

`REALESTATE_WORKSPACE`와 expected digest binding이 있을 때만 optional RealEstate evidence를 추가한다.

## 5. Verification Mapping

| Acceptance | Automated Evidence | Manual Evidence |
| :--- | :--- | :--- |
| 5~10 corpus, 6~10 endpoint scale | manifest/endpoint scope tests | corpus 대표성 review |
| disposition/rationale completeness | manifest validation test | semantic label approval |
| delegated hardcoding correction target | forbidden preserve test | REPLACE/UNSUPPORTED rationale approval |
| holdout isolation | scenario/holdout test | source naming diversity review |
| graph/semantic coverage separation | coverage calculator/integration test | 없음 |
| deterministic ordering/diff | three-run test and bundle digest | 없음 |
| normal scan isolation | `NormalScanIsolationTest` + full regression | 없음 |
| path/no-execution security | preflight/materialization negative suite | junction test N/A on unsupported filesystem, with rationale |

## 6. Scope Summary

- **Modify**: `build.gradle` 1개
- **New main contracts/services**: 29개 내외의 focused Java files
- **New test/helper files**: 11개
- **New resources**: 5 corpus × 3 Java files, holdout 3 files, manifest/profile 4 files
- **Existing production service modifications**: 0개
- **Database/API/frontend/deployment artifacts**: N/A — U01은 local evaluation capability이며 deployable이 아니다.
