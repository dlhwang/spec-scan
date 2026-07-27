# Stage 1: Planner — U01 Generalization Baseline

## Intent Diff

| Feature Area | AS-IS Behavior | TO-BE Behavior |
| :--- | :--- | :--- |
| Evaluation contract | `GoldenRuleLabel`과 aggregate metrics 중심의 단일-rule fixture 평가 | versioned corpus, observation, approved disposition, diff, coverage와 diagnostic aggregate |
| Corpus | 작은 synthetic source와 환경변수 기반 RealEstate regression이 분리됨 | 5개 required checked-in Spring MVC corpus, 별도 holdout, optional RealEstate를 하나의 manifest로 관리 |
| Current Java result | test assertion 또는 metric 입력 | 사실인 `ObservedSnapshot`으로 수집하고 approved baseline과 분리 |
| Baseline | `golden-labels.json`의 accepted rule/category | `PRESERVE`, `REPLACE`, `UNSUPPORTED`와 rationale/expectation을 가진 strict v1 manifest |
| Failure | 개별 JUnit failure | stable scoped diagnostic, required fail-fast, optional exclusion, partial evidence |
| Determinism | fingerprint 1회 재계산 또는 two-run map equality | 동일 pinned input의 exact three-run canonical snapshot 비교 |
| Security | external workspace test가 raw `Path`를 직접 사용 | allowed-root real-path confinement와 read-only view를 거친 explicit evaluation |
| Artifact | optional 단일 JSON report path | append-only version bundle, JSON/Markdown/digest, completion manifest last-write |
| Migration | 없음 | exact v1 reader registry와 explicit migration contract; concrete v2 migrator는 보류 |
| Production scan | U01 contract 없음 | 기존 정상 scan dependency graph와 output을 그대로 유지 |

## Principles

1. 기존 `analysis.domain.evaluation`/`analysis.support.evaluation`을 additive하게 확장하고 legacy contract를 변경하지 않는다.
2. current Java output은 관찰 사실이며 approved baseline과 자동으로 합치지 않는다.
3. preflight가 성공하기 전 Java analyzer를 호출하지 않는다.
4. checked-in corpus는 test workspace로 materialize한 뒤 같은 path confinement을 거친다.
5. legacy analyzer에는 original corpus path를 주지 않고 confined byte view에서 만든 caller-owned isolated evaluation workspace만 전달한다.
6. canonical semantic model만 diff/determinism에 사용하고 timestamp/metric/host path는 분리한다.
7. required corpus 실패는 전체 run을 중단하며 optional RealEstate 부재만 명시적으로 제외한다.
8. `SNAPSHOT_PROPOSAL`은 approved baseline 없이 실행하되 G01 pass를 만들 수 없고, `G01_DIFF`/`G01_DETERMINISM`은 approved baseline을 필수로 한다.
9. `DeterminismVerifier -> SingleRunCoordinator` 단방향을 지키고 retry/unstable-field masking을 금지한다.
10. generated evidence는 `build/reports`에만 쓰며 approved resource를 자동 갱신하지 않는다.
11. initial baseline labeling은 generated proposal review 후 별도 사용자 승인을 받는다.
12. 정상 scan entry point, `ScanPreparationService`, `RuleOutputService`와 default graph budget은 수정하지 않는다.

## File Plan

### 1. `build.gradle`

- **Change Type**: Modify
- **Detailed Specification**:
  - 기존 `test`와 `ruleEvaluation` task를 변경하지 않는다.
  - `u01BaselineEvaluation` JUnit task를 추가하여 `io.atworks.specscan.analysis.evaluation.baseline.*`만 실행한다.
  - generated output root를 `build/reports/specscan/u01`로 system property에 전달한다.
  - 신규 dependency, production application entry point와 default task dependency는 추가하지 않는다.

### 2. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/BaselineDiagnostic.java`

- **Change Type**: New
- **Detailed Specification**:
  - stable `code`, `severity`, `stage`, optional `corpusId/operationKey/identity`, `details`를 가진 immutable record를 정의한다.
  - canonical diagnostic ordering comparator를 제공하되 diagnostic을 gate verdict로 변환하지 않는다.

### 3. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/CorpusManifest.java`

- **Change Type**: New
- **Detailed Specification**:
  - strict v1 root와 nested immutable `CorpusEntry`, `EndpointScope`, `ScenarioDefinition`, `ScenarioRole`을 정의한다.
  - source kind는 `CHECKED_IN_FIXTURE`, `LOCAL_SNAPSHOT`만 허용한다.
  - locator, required, digest, optional commit ref, source roots, endpoint expectation과 tags를 표현한다.

### 4. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/BaselineManifest.java`

- **Change Type**: New
- **Detailed Specification**:
  - v1 baseline metadata, approval state와 immutable entry 목록을 정의한다.
  - nested `BaselineEntry`, `SemanticExpectation`, `DiagnosticExpectation`, `ApprovalMetadata` 및 `PRESERVE/REPLACE/UNSUPPORTED` enum을 둔다.
  - disposition default를 두지 않고 rationale/expectation 조합은 validator가 검증한다.

### 5. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/EvaluationProfile.java`

- **Change Type**: New
- **Detailed Specification**:
  - positive single-run timeout, optional suite timeout과 profile identity를 가진 immutable record를 정의한다.
  - checked-in profile factory는 `smoke()` 30초, `g01()` 10분 single run/30분 suite만 제공하며 hidden infinite default는 제공하지 않는다.

### 6. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/BaselineObservation.java`

- **Change Type**: New
- **Detailed Specification**:
  - raw/canonical snapshot, corpus/endpoint observation, semantic observation, exact/scenario identity, graph/semantic coverage와 metrics를 하나의 versioned aggregate로 정의한다.
  - metric/timestamp는 canonical semantic equality와 분리된 필드로 유지한다.
  - collection은 defensive copy하고 host absolute path를 허용하지 않는다.

### 7. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/BaselineDiff.java`

- **Change Type**: New
- **Detailed Specification**:
  - `ADDED/REMOVED/CHANGED/UNCHANGED`, disposition verdict, before/after semantic value와 change proposal을 정의한다.
  - exact diff와 scenario holdout diff를 명시적으로 구분한다.

### 8. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/BaselineRunResult.java`

- **Change Type**: New
- **Detailed Specification**:
  - preflight/analysis/canonicalization/diff terminal state, canonical snapshot, diagnostics, metrics와 partial evidence를 표현한다.
  - failed result도 diagnostic을 보존하며 null 기반 상태 조합 대신 명시적 run status를 사용한다.

### 8a. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/BaselineRunRequest.java`

- **Change Type**: New
- **Detailed Specification**:
  - `SNAPSHOT_PROPOSAL`, `G01_DIFF`, `G01_DETERMINISM` run purpose, corpus reference, optional baseline reference, explicit profile, engine revision, allowed root, caller-owned evaluation workspace root와 output root를 정의한다.
  - proposal은 baseline 부재만 허용하고 overall G01 status를 항상 `REVIEW_REQUIRED`로 제한한다.
  - diff/determinism은 approved exact-version baseline을 필수로 검증한다.

### 9. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/EvaluationResultBundle.java`

- **Change Type**: New
- **Detailed Specification**:
  - bundle ID/input identity, run/determinism result, baseline diff, coverage, diagnostics, metrics와 overall G01 status를 renderer 입력으로 고정한다.
  - renderer가 verdict/count를 재계산하지 않도록 완결된 수치를 보유한다.

### 10. `src/main/java/io/atworks/specscan/analysis/domain/evaluation/CompletionManifest.java`

- **Change Type**: New
- **Detailed Specification**:
  - bundle/schema version, run/input identity, payload name/SHA-256 목록, overall status, metadata timestamp와 completion marker를 정의한다.

### 11. `src/main/java/io/atworks/specscan/analysis/support/evaluation/BaselineJsonCodec.java`

- **Change Type**: New
- **Detailed Specification**:
  - Jackson strict unknown-field rejection, stable property/map ordering과 Java time support 없이 duration string adapter를 구성한다.
  - corpus/baseline/completion v1 read와 deterministic JSON byte rendering을 제공한다.
  - unsupported version을 임의 latest reader로 넘기지 않는다.

### 12. `src/main/java/io/atworks/specscan/analysis/support/evaluation/SchemaVersionReaderRegistry.java`

- **Change Type**: New
- **Detailed Specification**:
  - artifact kind + exact schema version을 immutable reader에 매핑한다.
  - duplicate registration과 unsupported version을 stable diagnostic/exception으로 거부한다.
  - normal read path에서 migrator를 호출하지 않는다.

### 13. `src/main/java/io/atworks/specscan/analysis/support/evaluation/ArtifactMigrationRegistry.java`

- **Change Type**: New
- **Detailed Specification**:
  - source/target kind/version을 명시하는 `ArtifactMigrator` contract와 immutable registry를 제공한다.
  - 원본 overwrite를 API에서 허용하지 않고 target path와 semantic diff 반환을 요구한다.
  - v1→v2 concrete migrator는 등록하지 않으며 missing path를 안정적으로 보고한다.

### 14. `src/main/java/io/atworks/specscan/analysis/support/evaluation/BaselineManifestValidator.java`

- **Change Type**: New
- **Detailed Specification**:
  - corpus/scenario/entry ID uniqueness, 5~10 required corpus 조건, source kind/digest/source root, endpoint scope, scenario mapping을 검증한다.
  - disposition/rationale/expectation 조합, delegated hardcoding `PRESERVE` 금지와 unlabeled observation 정책을 검증한다.
  - 모든 오류를 deterministic diagnostic 순서로 반환한다.

### 15. `src/main/java/io/atworks/specscan/analysis/support/evaluation/CorpusAccessGuard.java`

- **Change Type**: New
- **Detailed Specification**:
  - allowed root와 locator를 `toRealPath()`로 resolve하고 descendant, volume/case, symbolic link/junction escape를 검증한다.
  - 검증된 root-relative source 열거/읽기만 제공하는 `ConfinedCorpusView`를 반환한다.
  - write/delete/process/network/class-loading capability를 노출하지 않는다.

### 16. `src/main/java/io/atworks/specscan/analysis/support/evaluation/CorpusIntegrityVerifier.java`

- **Change Type**: New
- **Detailed Specification**:
  - normalized relative path + file bytes를 lexicographic order로 SHA-256 처리한다.
  - manifest에서 승인한 build/generated exclusion만 적용하고 digest/commit metadata mismatch를 보고한다.

### 16a. `src/main/java/io/atworks/specscan/analysis/support/evaluation/EvaluationWorkspaceMaterializer.java`

- **Change Type**: New
- **Detailed Specification**:
  - `ConfinedCorpusView`가 열거·읽은 relative source bytes만 caller-owned unique evaluation workspace에 복사한다.
  - materialized tree에서 symlink/junction이 없고 source digest가 preflight digest와 동일함을 재검증한 뒤 immutable `PreparedEvaluationWorkspace`를 반환한다.
  - original corpus absolute path, unrestricted read, target build/process/network capability를 downstream에 전달하지 않는다.
  - caller-owned workspace를 삭제하거나 재사용하지 않는다.

### 17. `src/main/java/io/atworks/specscan/analysis/support/evaluation/RunDeadline.java`

- **Change Type**: New
- **Detailed Specification**:
  - injectable monotonic nano source, positive duration과 overflow-safe deadline을 사용한다.
  - `remaining()`/`checkpoint(scope)`를 제공하고 만료 시 `EVALUATION_TIMEOUT` diagnostic을 생성한다.
  - interrupt, forced stop, retry와 wall clock을 사용하지 않는다.

### 18. `src/main/java/io/atworks/specscan/analysis/support/evaluation/RunMetricsCollector.java`

- **Change Type**: New
- **Detailed Specification**:
  - injectable nano source와 heap-used supplier를 사용한다.
  - run/corpus/endpoint checkpoint의 elapsed, observed heap maximum, graph/node/edge/candidate count를 수집한다.
  - sampling exception을 `METRICS_COLLECTION_FAILED` warning으로 바꾸고 semantic state를 변경하지 않는다.

### 19. `src/main/java/io/atworks/specscan/analysis/support/evaluation/JavaBaselineAnalysisAdapter.java`

- **Change Type**: New
- **Detailed Specification**:
  - `PreparedEvaluationWorkspace`에서만 `RepositorySource`를 구성하고 기존 `SpringStaticScanService`, `DefaultFactCodeGraphBuilder`, `DefaultGraphRuleEngine`, `RuleOutputService`를 explicit evaluation에서 호출한다.
  - endpoint별 graph diagnostics, candidates와 external `EndpointRuleOutput`을 raw observation으로 반환한다.
  - method scope 계산은 evaluation-only helper에 격리하고 external output 및 graph별 candidate identity/rule/status parity test로 drift를 탐지한다.
  - target build/test/process/network는 호출하지 않는다.

### 20. `src/main/java/io/atworks/specscan/analysis/support/evaluation/CanonicalSnapshotBuilder.java`

- **Change Type**: New
- **Detailed Specification**:
  - current Java raw result를 exact/scenario identity와 canonical semantic projection으로 변환한다.
  - evidence fingerprint, expected value와 diagnostics를 정규화/정렬한다.
  - timestamps, elapsed/memory, host path와 internal trace order를 제외한다.
  - identity collision을 숨기지 않고 실패 diagnostic으로 반환한다.

### 21. `src/main/java/io/atworks/specscan/analysis/support/evaluation/BaselineDiffer.java`

- **Change Type**: New
- **Detailed Specification**:
  - exact identity로 same-source added/removed/changed/unchanged를 계산한다.
  - scenario identity로 holdout semantic shape와 expected rename을 비교한다.
  - disposition-aware verdict와 unlabeled proposal을 생성하며 baseline을 쓰지 않는다.

### 22. `src/main/java/io/atworks/specscan/analysis/support/evaluation/CoverageCalculator.java`

- **Change Type**: New
- **Detailed Specification**:
  - graph coverage와 semantic coverage numerator/denominator/exclusion을 독립 계산한다.
  - required failure, optional exclusion, unclassified/unresolved/partial counts를 분리한다.

### 23. `src/main/java/io/atworks/specscan/analysis/support/evaluation/SingleRunCoordinator.java`

- **Change Type**: New
- **Detailed Specification**:
  - strict corpus read → purpose별 baseline invariant → manifest validation → access/integrity preflight → isolated workspace materialization → sequential corpus execution → canonicalization → purpose별 diff/coverage 순서를 조율한다.
  - `SNAPSHOT_PROPOSAL`은 baseline reader/differ를 호출하지 않고 review proposal을 생성하며 G01 pass를 반환하지 않는다.
  - `G01_DIFF`/`G01_DETERMINISM`은 approved baseline 없이는 preflight 실패한다.
  - preflight fail 시 analyzer invocation 0회를 보장한다.
  - corpus/endpoint 경계에서 deadline/metric checkpoint를 호출하고 diagnostic-preserving terminal result를 만든다.
  - optional corpus만 격리하며 retry와 publication 책임을 갖지 않는다.

### 24. `src/main/java/io/atworks/specscan/analysis/support/evaluation/DeterminismVerifier.java`

- **Change Type**: New
- **Detailed Specification**:
  - 동일 immutable input으로 `SingleRunCoordinator`를 정확히 3회 호출한다.
  - run 1↔2, run 1↔3 canonical semantic snapshot을 비교하고 identity/field diff를 반환한다.
  - suite deadline을 검사하되 failed run을 재시도로 대체하지 않는다.

### 25. `src/main/java/io/atworks/specscan/analysis/support/evaluation/BaselineEvaluationFacade.java`

- **Change Type**: New
- **Detailed Specification**:
  - single/determinism mode를 명시적으로 선택하고 completed `EvaluationResultBundle`을 조립한다.
  - production scan/CLI에 등록하지 않으며 evaluation caller가 직접 구성한다.

### 26. `src/main/java/io/atworks/specscan/analysis/support/evaluation/BaselineArtifactRenderer.java`

- **Change Type**: New
- **Detailed Specification**:
  - 동일 `EvaluationResultBundle`에서 deterministic JSON bytes와 Markdown summary를 생성한다.
  - bundle ID, overall status와 corpus/disposition/coverage count consistency를 검증한다.
  - renderer는 diff/coverage/verdict를 재계산하지 않는다.

### 27. `src/main/java/io/atworks/specscan/analysis/support/evaluation/ArtifactBundlePublisher.java`

- **Change Type**: New
- **Detailed Specification**:
  - `build/reports/specscan/u01/<bundle-id>/` unique directory에 payload/digest를 기록한다.
  - consistency와 on-disk digest 검증 뒤 `completion-manifest.json`을 마지막에 기록한다.
  - existing directory/resource overwrite와 cleanup을 수행하지 않는다.

### 28. `src/main/java/io/atworks/specscan/analysis/support/evaluation/CompletedBundleReader.java`

- **Change Type**: New
- **Detailed Specification**:
  - completion manifest 존재, exact v1 support, payload presence와 digest를 모두 검증한다.
  - incomplete/corrupt/unsupported bundle을 stable diagnostic으로 거부한다.

### 29. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselineTestWorkspace.java`

- **Change Type**: New
- **Detailed Specification**:
  - checked-in corpus resource를 `@TempDir` 아래 allowed root로 materialize하고 manifest locator를 resolve한다.
  - optional `REALESTATE_WORKSPACE`를 별도 local snapshot mapping으로 제공한다.
  - test helper 외부로 unrestricted absolute path capability를 노출하지 않는다.

### 30. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselineManifestContractTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - strict schema/unknown field/version, duplicate ID, 5~10 corpus, source root/scope, scenario mapping과 모든 disposition 조합을 검증한다.
  - legacy delegated hardcoding의 `PRESERVE` 거부와 unlabeled proposal을 검증한다.

### 31. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselinePreflightSecurityTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - traversal, symlink와 가능한 Windows junction/out-of-root, missing required/optional, digest mismatch를 검증한다.
  - invalid required preflight에서 injected analyzer invocation count가 0인지 확인한다.

### 32. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselineCanonicalDiffTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - shuffled input/metrics/timestamp/host path 변화가 canonical result를 바꾸지 않음을 검증한다.
  - identity collision, exact four-way diff, disposition verdict와 scenario rename/mutation mapping을 검증한다.

### 33. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselineExecutionControlTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - sequential order, 30초/10분 profile validation, cooperative timeout, no retry와 partial diagnostic을 fake clock/analyzer로 검증한다.
  - heap supplier failure가 semantic snapshot/diff를 변경하지 않음을 검증한다.

### 34. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselineDeterminismTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - coordinator exactly-three invocation, 1↔2/1↔3 equality와 injected nondeterminism field report를 검증한다.
  - coordinator가 verifier에 의존하지 않는 construction boundary를 확인한다.

### 35. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/BaselineArtifactPublicationTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - deterministic JSON, JSON/Markdown count consistency, last completion write, existing bundle non-overwrite를 검증한다.
  - incomplete/corrupt/digest mismatch/unsupported version reader 거부와 missing migration path를 검증한다.

### 36. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/JavaBaselineAdapterParityTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - 동일 scan/graph에서 adapter의 external endpoint output과 graph별 candidate identity/rule/status가 기존 `RuleOutputService`/rule engine 결과와 일치하는지 검증한다.
  - target build/process/network 호출 없이 checked-in source만 분석한다.

### 37. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/U01CorpusEvaluationTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - required 5개 corpus의 약 6개 endpoint discovery, graph/semantic coverage 분리, diagnostic 보존과 holdout isolation을 검증한다.
  - generated proposal/evidence를 configured build report root에만 쓴다.
  - `SNAPSHOT_PROPOSAL`은 approved baseline 없이 실행되지만 G01을 통과시키지 않고 review proposal만 생성함을 검증한다.
  - `G01_DIFF`/`G01_DETERMINISM`은 approved baseline 부재 또는 pending state에서 analyzer invocation 0회로 실패함을 검증한다.

### 38. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/NormalScanIsolationTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - invalid baseline manifest 또는 unavailable corpus가 기존 `ScanPreparationService`/`RuleOutputService` 정상 경로의 생성과 결과에 영향을 주지 않음을 검증한다.
  - production package에서 U01 facade/bootstrap 정적 참조가 생기지 않았음을 의존 방향으로 확인한다.

### 39. `src/test/java/io/atworks/specscan/analysis/evaluation/baseline/RealEstateOptionalCorpusTest.java`

- **Change Type**: New
- **Detailed Specification**:
  - `REALESTATE_WORKSPACE` 부재 시 `EXCLUDED_OPTIONAL`이고 required evidence는 동일함을 검증한다.
  - 환경변수와 별도 expected digest가 모두 있으면 confinement/digest를 거쳐 optional observation만 추가한다.
  - path만 있고 digest/optional commit binding이 없으면 unavailable로 취급한다.

### 40. `src/test/resources/rule-based-static-analysis/baseline/v1/corpus-manifest.json`

- **Change Type**: New
- **Detailed Specification**:
  - required checked-in corpus 5개, separated holdout과 optional RealEstate logical entry를 선언한다.
  - checked-in entry는 exact digest를 포함하고 RealEstate의 expected digest/commit은 host-independent manifest에 쓰지 않고 explicit local binding으로 요구한다.
  - package/controller allowlist 대신 expected endpoint count/operation expectation과 diversity tags를 기록한다.

### 41. `src/test/resources/rule-based-static-analysis/baseline/v1/baseline-manifest.json`

- **Change Type**: New after manual labeling checkpoint
- **Detailed Specification**:
  - generated current observation을 자동 복사하지 않고 사람이 검토한 모든 entry의 disposition/rationale/expectation을 기록한다.
  - delegated domain hardcoding은 `REPLACE` 또는 `UNSUPPORTED`로만 기록한다.
  - 최초 생성은 pending review이며 별도 사용자 label 승인을 받은 뒤 approved metadata를 확정한다.

### 42. `src/test/resources/rule-based-static-analysis/baseline/v1/profiles/smoke.json`

- **Change Type**: New
- **Detailed Specification**: 30초 single-run explicit profile을 선언한다.

### 43. `src/test/resources/rule-based-static-analysis/baseline/v1/profiles/g01.json`

- **Change Type**: New
- **Detailed Specification**: 10분 single-run/30분 three-run suite explicit profile을 선언한다.

### 44. Checked-in required corpus resource tree

- **Change Type**: New
- **Root**: `src/test/resources/rule-based-static-analysis/baseline/v1/corpora/`
- **Detailed Specification**:
  - `catalog/`: `CatalogController.java`, `CatalogService.java`, `CatalogRepository.java`
  - `registry/`: `RegistryController.java`, `RegistryService.java`, `RegistryRepository.java`
  - `billing/`: `InvoiceController.java`, `InvoiceService.java`, `InvoiceRepository.java`
  - `membership/`: `MemberController.java`, `MemberService.java`, `MemberRepository.java`
  - `shipping/`: `ParcelController.java`, `ParcelService.java`, `ParcelRepository.java`
  - 각 controller는 6개 Spring MVC endpoint를 가지며 서로 다른 package/aggregate/field/method naming과 binary/composite/null-empty/Optional/delegated observation shape를 포함한다.

### 45. Checked-in holdout resource tree

- **Change Type**: New
- **Root**: `src/test/resources/rule-based-static-analysis/baseline/v1/holdout/catalog-renamed/`
- **Detailed Specification**:
  - `ArchiveController.java`, `ArchiveService.java`, `ArchiveRepository.java`를 둔다.
  - catalog base와 같은 semantic roles를 보존하되 package/type/method/field/parameter 이름과 일부 equivalent control-flow shape를 변경한다.
  - corpus/manifest/recipe tuning에서는 holdout source를 읽지 않고 final isolation evaluation에서만 사용한다.

### 46. Existing legacy files

- **Change Type**: No Change; regression-only
- **Files**:
  - `src/main/java/io/atworks/specscan/analysis/domain/evaluation/GoldenRuleLabel.java`
  - `src/main/java/io/atworks/specscan/analysis/domain/evaluation/EvaluationReport.java`
  - `src/main/java/io/atworks/specscan/analysis/support/evaluation/RuleEvaluationService.java`
  - `src/test/resources/rule-based-static-analysis/evaluation/golden-labels.json`
  - `src/test/java/io/atworks/specscan/analysis/evaluation/RuleEvaluationServiceTest.java`
- **Detailed Specification**: 의미, schema와 test를 그대로 유지하고 full regression에서 통과시킨다.

## Execution Sequence

1. Domain/version contracts와 strict validation을 구현한다.
2. confinement, integrity, deadline과 metrics preflight를 구현한다.
3. confined view→isolated evaluation workspace bridge와 Java observation adapter를 구현한다.
4. canonicalization, diff와 coverage를 구현한다.
5. purpose-aware single-run/three-run orchestration을 구현한다.
6. deterministic rendering, append-only publication과 completed reader를 구현한다.
7. checked-in corpus/holdout/profile manifest와 test workspace adapter를 추가한다.
8. unit/security/determinism/publication/legacy/normal-scan regression을 실행한다.
9. `SNAPSHOT_PROPOSAL`로 current Java snapshot과 review-only proposal을 `build/reports`에 생성한다.
10. corpus 대표성과 각 disposition/rationale에 대한 별도 사용자 승인을 받는다.
11. 승인된 baseline manifest를 확정하고 `G01_DETERMINISM` three-run evidence를 생성한다.

## Verification Commands

- `./gradlew.bat test --tests "io.atworks.specscan.analysis.evaluation.baseline.*"`
- `./gradlew.bat u01BaselineEvaluation`
- `./gradlew.bat test --tests "io.atworks.specscan.analysis.evaluation.RuleEvaluationServiceTest"`
- `./gradlew.bat test --tests "io.atworks.specscan.analysis.migration.RealEstateGoldenRegressionTest"` when `REALESTATE_WORKSPACE` is available
- `./gradlew.bat test`

## Requirement Trace

| Requirement | File Plan / Evidence |
| :--- | :--- |
| R2-YAML-009 | #3, #14~16, #19, #22~24, #29, #31, #37, #39~45 |
| R2-YAML-010 | #4, #7, #14, #20~21, #32, #37, #41 |
| NFR-R2-001 | #6, #20, #24, #34 |
| NFR-R2-003 | #4, #19~21, #32, #41 |
| NFR-R2-004 | #40, #44~45, holdout isolation evidence |
| NFR-R2-005 | #2, #8, #23, diagnostic tests |
| NFR-R2-007 | #17~18, #33, G01 report |
| NFR-R2-008 | #30~39 and full regression |
| NFR-R2-009 | #15~16, #31, #36 |
