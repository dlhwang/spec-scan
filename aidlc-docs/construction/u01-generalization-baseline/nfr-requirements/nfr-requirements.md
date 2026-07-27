# U01 Generalization Baseline — NFR Requirements

## 1. Scope and Quality Priority

U01은 local/offline evaluation harness다. 품질 우선순위는 다음과 같다.

1. 의미 정확성과 baseline 감사 가능성
2. 결정론과 종료 보장
3. untrusted repository에 대한 안전성
4. 반복 가능한 failure/recovery
5. 성능 관찰 가능성

고정 latency, high availability, horizontal scaling과 production service SLO는 U01 범위가 아니다.

## 2. Capacity and Scalability

### U01-NFR-SCAL-001 — Bounded batch capacity

- 한 reference evaluation run은 5~10개 corpus를 처리해야 한다.
- 각 corpus는 약 6~10개 Spring MVC endpoint 또는 manifest가 동등 복잡도로 승인한 scope를 가진다.
- 이 범위를 초과한 실행은 허용할 수 있으나 U01 acceptance evidence의 보장 범위에는 포함하지 않는다.

**Evidence**: 최대 승인 profile인 10개 corpus manifest load/validation 및 batch orchestration test.

### U01-NFR-SCAL-002 — Corpus isolation

- corpus별 observation accumulator, diagnostic과 coverage state는 공유 가변 상태에 의존하지 않아야 한다.
- 한 optional corpus의 실패가 다른 corpus observation을 손상시키면 안 된다.
- required corpus failure는 batch gate를 실패시키되 이미 수집한 diagnostic을 보존해야 한다.

**Evidence**: optional failure isolation test, required failure diagnostic preservation test.

### U01-NFR-SCAL-003 — No scaling infrastructure

U01은 scheduler, queue, worker fleet, distributed cache나 database를 요구하지 않는다. 향후 범위가 10개 corpus를 크게 초과하면 별도 NFR revision을 수행한다.

## 3. Performance and Termination

### U01-NFR-PERF-001 — Observed performance metrics

각 run은 다음을 기록해야 한다.

- repository/corpus 및 endpoint elapsed time
- 전체 run elapsed time
- peak 또는 관찰 가능한 process memory
- endpoint/graph/node/edge/candidate count

이 metric은 U01 semantic pass/fail에 사용하지 않고 trend/diagnostic으로 제공한다.

**Evidence**: metric completeness test와 metric 제외 semantic-diff test.

### U01-NFR-PERF-002 — Explicit finite timeout

- 모든 evaluation profile은 양의 유한 timeout을 명시해야 한다.
- timeout 값은 환경/fixture에 주입 가능해야 하며 hidden infinite default를 허용하지 않는다.
- timeout 초과는 `EVALUATION_TIMEOUT`으로 실패하고 부분 observation/diagnostic을 보존해야 한다.
- timeout은 자동 retry를 유발하지 않는다.

**Evidence**: missing/zero/negative timeout validation, injected short-timeout integration test.

### U01-NFR-PERF-003 — No fixed speed gate

U01에서는 repository별 runtime 또는 memory threshold를 Go/No-Go 합격선으로 사용하지 않는다. 동일 input에서 비정상적 추세는 report하되 수치 판정은 후속 performance baseline 결정으로 남긴다.

## 4. Availability and Recoverability

### U01-NFR-AVL-001 — Offline batch availability model

- uptime, failover, active-active와 disaster recovery SLO는 적용하지 않는다.
- run은 local pinned inputs만으로 시작 가능해야 한다.
- remote service/network availability에 의존하지 않아야 한다.

### U01-NFR-AVL-002 — Fail-fast before analysis

required corpus availability/integrity, manifest/schema와 timeout validation은 Java analysis 시작 전에 완료해야 한다.

**Evidence**: invalid input에서 analysis invocation count 0 test.

### U01-NFR-AVL-003 — Reproducible rerun

실패 run은 manifest version, source digest, engine revision, evaluation profile과 diagnostic을 남겨 동일 조건으로 재실행할 수 있어야 한다.

**Evidence**: failure metadata completeness 및 rerun identity test.

## 5. Security

### U01-NFR-SEC-001 — Untrusted corpus

모든 target corpus는 untrusted input으로 취급한다. 신뢰 여부에 따른 보안 우회 옵션을 제공하지 않는다.

### U01-NFR-SEC-002 — Static read-only analysis

U01 runner는 다음을 수행하면 안 된다.

- target Gradle/Maven/build script 실행
- target binary/class/script 실행
- target repository에 파일 생성·수정·삭제
- reflection 또는 dynamic class loading으로 target code 실행

**Evidence**: process execution adapter 부재/차단 test, corpus filesystem unchanged check.

### U01-NFR-SEC-003 — Network denial

evaluation 중 dependency download, repository clone과 외부 API 호출을 수행하면 안 된다. corpus는 사전에 준비된 local source여야 한다.

**Evidence**: network adapter dependency review 및 offline integration run.

### U01-NFR-SEC-004 — Path confinement

- manifest locator를 canonical/real path로 해석해야 한다.
- 해석된 corpus path는 승인된 corpus root 아래에 있어야 한다.
- `..`, absolute-path substitution과 symbolic-link/junction escape를 거부해야 한다.
- report에는 host absolute path 대신 corpus-relative path를 사용해야 한다.

**Evidence**: traversal, symlink/junction escape, out-of-root negative tests.

### U01-NFR-SEC-005 — Report content safety

diagnostic은 source snippet을 포함할 수 있으나 credential/token으로 의심되는 값을 전체 출력하지 않아야 한다. host username과 absolute workspace path는 artifact에 기록하지 않는다.

**Evidence**: path redaction test와 synthetic secret fixture review.

## 6. Reliability and Determinism

### U01-NFR-REL-001 — Three-run semantic determinism

동일한 manifest, source digest, engine revision과 evaluation profile을 3회 연속 실행했을 때 canonical semantic snapshot diff가 0건이어야 한다.

다음은 determinism comparison에서 제외한다.

- timestamps
- elapsed time와 memory metrics
- host absolute paths
- non-semantic log order

**Evidence**: approved corpus profile의 3-run comparison report.

### U01-NFR-REL-002 — Stable ordering

JSON과 Markdown은 `corpusId → operationKey → scenario/exact identity → semanticRole → ruleId → canonical constraint` 순서로 정렬해야 한다.

**Evidence**: shuffled-input ordering tests.

### U01-NFR-REL-003 — No implicit retry or recovery

manifest, digest, identity, labeling과 timeout failure를 자동 retry 또는 auto-correction으로 숨기면 안 된다. 오류를 수정하고 명시적으로 rerun한다.

### U01-NFR-REL-004 — Diagnostic preservation

gate failure에서도 실패까지 수집된 manifest validation, corpus availability와 observation diagnostic을 report해야 한다.

## 7. Maintainability

### U01-NFR-MNT-001 — Explicit artifact schema version

다음 artifact는 `schemaVersion`을 가져야 한다.

- corpus manifest
- observed snapshot
- baseline manifest
- baseline diff
- baseline change proposal
- coverage snapshot/report

### U01-NFR-MNT-002 — Unsupported version fail-fast

loader는 지원하지 않는 schema version을 `UNSUPPORTED_SCHEMA_VERSION`으로 거부해야 한다. 가장 가까운 version으로 추측하거나 silent conversion하지 않는다.

### U01-NFR-MNT-003 — Explicit migration only

schema migration은 source version, target version, 변경 의미와 생성된 diff를 명시하는 별도 절차여야 한다. loader의 정상 read path에서 in-place migration을 수행하지 않는다.

### U01-NFR-MNT-004 — Stable diagnostic codes

machine consumer가 diagnostic message 문자열이 아니라 stable code에 의존할 수 있어야 한다. code 의미 변경은 schema/version change로 취급한다.

### U01-NFR-MNT-005 — Single ownership and documentation

U01 artifact model과 rules는 U01이 소유한다. U02~U06가 필드를 추가로 필요로 할 경우 U01 contract 및 traceability를 먼저 갱신한다.

## 8. Usability and Reviewability

### U01-NFR-USE-001 — Single result model, two renderings

deterministic JSON과 Markdown summary는 같은 immutable result model에서 생성해야 한다. renderer가 별도 판정 로직을 가져서는 안 된다.

### U01-NFR-USE-002 — Machine-readable JSON

JSON은 다음을 포함해야 한다.

- artifact/schema/version metadata
- corpus availability/integrity
- observations와 identities
- disposition verdict/diff
- graph/semantic coverage
- diagnostics와 metrics

### U01-NFR-USE-003 — Human-readable Markdown

Markdown은 최소 다음을 요약해야 한다.

- overall G01 status
- required/optional corpus 상태
- disposition별 pass/fail/change count
- unlabeled/corrected/unsupported 항목
- graph/semantic coverage numerator/denominator
- 실패 reason과 re-baseline proposal link/reference

### U01-NFR-USE-004 — Actionable failure

모든 gate failure는 stable code, scope(corpus/endpoint/identity), 사람이 이해할 설명과 필요한 다음 조치를 포함해야 한다.

## 9. Testing and Evidence Requirements

| Quality area | Required level | Evidence |
| :--- | :--- | :--- |
| Capacity | integration | 10-corpus bounded profile |
| Timeout | unit + integration | invalid values, forced timeout |
| Availability | unit + integration | preflight fail-fast, rerun metadata |
| Security | negative + integration | path escape, no execution/network, read-only |
| Determinism | end-to-end evaluation | 3-run semantic diff 0 |
| Schema maintainability | unit + migration review | unsupported version, explicit migration fixture |
| Usability | snapshot | JSON/Markdown same-model consistency |

## 10. Traceability

| Upstream NFR | U01 realization |
| :--- | :--- |
| NFR-R2-001 결정론 | REL-001, REL-002 |
| NFR-R2-002 종료 보장 | PERF-002 |
| NFR-R2-003 무추측 | Functional Design disposition/expectation 및 diagnostic 보존 |
| NFR-R2-004 저장소 독립성 | bounded diverse corpus와 scenario identity |
| NFR-R2-005 진단 가능성 | REL-004, USE-004 |
| NFR-R2-006 스키마 안전성 | MNT-001~003 |
| NFR-R2-007 성능 관찰 | PERF-001, PERF-003 |
| NFR-R2-008 테스트 전략 | Section 9 evidence matrix |
| NFR-R2-009 보안 경계 | SEC-001~005 |

## 11. U01 NFR Exit Criteria

- 모든 필수 evidence scenario가 자동화 계획에 배정됨
- 3-run determinism acceptance가 명시됨
- timeout은 유한하고 profile에 명시되며 invalid value가 거부됨
- required corpus failure가 fail-fast하고 diagnostic이 보존됨
- target code/build/network access 금지와 path confinement가 검증 가능함
- artifact schema/version과 explicit migration 경계가 정의됨
- JSON/Markdown이 같은 result model에서 생성됨
