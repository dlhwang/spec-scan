# U01 Generalization Baseline Final Plan & Verification Specification

## Metadata

- **Unit Slug**: `u01-generalization-baseline`
- **Date Finalized**: 2026-07-22T12:44:18.7604280+09:00
- **Review Pipeline**: Stage 1 ✅ (iteration 2) | Stage 2 ✅ OKAY | Stage 3 ✅ APPROVE | Stage 4 ✅ PASS
- **Spec Reference**: `../../specs/deep-interview-u01-generalization-baseline.md`
- **Executable Plan**: `../u01-generalization-baseline-code-generation-plan.md`
- **Code Status**: 사용자 승인 완료 — Steps 1~9 실행 허가

## Architecture Decision Record

### Decision

기존 evaluation package를 additive 확장하여 versioned corpus/baseline/observation contract와 explicit evaluation harness를 구현한다. 5개 checked-in realistic Spring MVC corpus와 separated holdout을 required evidence로 사용하고 RealEstate local snapshot은 optional로 유지한다. 원본 corpus는 real-path confinement 후 isolated evaluation workspace로 materialize하여 legacy path-based Java analyzer에 전달한다. current Java observation은 approved baseline과 분리하고, proposal→manual label approval→exact three-run G01 evidence 순서로 확정한다.

### Drivers

1. U02~U06가 소비할 안정적인 candidate/coverage/baseline identity가 먼저 필요하다.
2. 현재 Java 결과에는 보존 대상과 DelegatedGuard 같은 교정 대상이 섞여 있다.
3. normal production scan은 PoC evaluation failure와 독립적이어야 한다.
4. 외부/local source는 실행하지 않고 path escape와 host-specific artifact를 차단해야 한다.
5. baseline update는 자동화보다 review/audit 가능성이 우선이다.
6. 같은 pinned input의 semantic output이 결정적이어야 후속 YAML/Java diff가 의미를 가진다.

### Alternatives Considered

- **Chosen — Additive evaluation contract + checked-in required corpus**: CI 재현성과 후속 Unit 재사용성이 높고 legacy regression을 유지한다.
- **Rejected — U01 전부 test-only**: U02~U06에서 main contract 이동이 발생하여 G01 identity가 흔들린다.
- **Rejected — 실제 local repo 5~10개 required**: 대표성은 높지만 local path/permission/CI 재현성이 G01의 단일 장애점이 된다.
- **Rejected — 기존 synthetic resource만 사용**: 독립 Spring MVC corpus와 endpoint 규모 acceptance를 충족하지 못한다.
- **Rejected — raw validated path를 legacy analyzer에 전달**: confinement capability와 TOCTOU 경계가 불충분하다.
- **Rejected — legacy `golden-labels.json`을 v0 migration**: 신규 baseline aggregate와 의미가 달라 손실 변환을 schema migration으로 오인한다.

## Final Plan

### 1. Contracts and strict input

- versioned corpus/baseline/observation/diff/result/completion records를 기존 evaluation domain에 추가한다.
- exact v1 Jackson reader, version registry, migration contract와 manifest validator를 구현한다.
- 기존 `GoldenRuleLabel`/`EvaluationReport`/`RuleEvaluationService`는 변경하지 않는다.

### 2. Secure preflight and controlled execution

- allowed-root real-path confinement, canonical digest와 required/optional policy를 analysis 전에 검증한다.
- confined bytes를 caller-owned isolated workspace에 materialize하고 digest를 재검증한다.
- Java adapter는 prepared workspace만 분석하며 target build/process/network를 호출하지 않는다.
- monotonic deadline과 checkpoint heap observation을 사용한다.

### 3. Canonical truth separation

- current Java output을 fact snapshot으로 수집하고 exact/scenario identity로 canonicalize한다.
- metrics/timestamps/host paths는 semantic equality에서 제외한다.
- graph/semantic coverage와 disposition-aware diff를 독립 계산한다.

### 4. Purpose-aware orchestration

- `SNAPSHOT_PROPOSAL`은 baseline 없이 실행하되 `REVIEW_REQUIRED`만 반환한다.
- `G01_DIFF`/`G01_DETERMINISM`은 approved exact-version baseline 없이는 analyzer를 호출하지 않는다.
- determinism은 동일 input exact 3회와 1↔2/1↔3 canonical comparison이다.

### 5. Immutable evidence

- JSON/Markdown/digest를 unique build report bundle에 쓰고 completion manifest를 마지막에 기록한다.
- incomplete/corrupt/unsupported bundle은 reader가 거부한다.
- generated output은 approved resource를 덮어쓰지 않는다.

### 6. Corpus and manual label checkpoint

- 5개 required realistic Spring MVC mini-repository, 1개 separated holdout과 optional RealEstate를 manifest에 고정한다.
- proposal bundle을 생성한 뒤 corpus 대표성과 모든 disposition/rationale를 별도 사용자 review에 제출한다.
- label 승인 후에만 approved baseline v1과 final G01 evidence를 만든다.

## Verification Plan

- **Unit Tests**: strict schema/manifest, identity, disposition, canonicalization, diff, coverage, deadline, metrics, migration registry.
- **Security Tests**: out-of-root/symlink/junction, digest mismatch, materialized recheck, analyzer invocation 0.
- **Assembly Tests**: 5 corpus endpoint/graph/rule observation, optional RealEstate exclusion, adapter parity.
- **Determinism Tests**: exact three-run canonical equality와 injected nondeterminism report.
- **Artifact Tests**: same-model JSON/Markdown consistency, completion-last, non-overwrite, corrupt/incomplete reader rejection.
- **Regression Tests**: legacy evaluation, migration suite, normal scan isolation과 full Gradle test.
- **Manual Verification**: corpus 대표성 및 initial disposition/rationale label approval. 자동화 N/A가 아니라 의도적인 G01 human gate다.

## Consensus Outcomes

- Critic iteration 1은 raw path bridge와 initial proposal/baseline 순환 때문에 REJECT했다.
- revised Planner는 isolated materialization과 run-purpose input invariant를 도입했다.
- Critic iteration 2는 OKAY, Architect는 8개 조건부 APPROVE, Reconciliation은 100% PASS했다.
- residual risk는 sampled heap의 근사성, realistic fixture의 대표성 한계와 optional real-repo availability이며 semantic verdict와 G01 scope에 명시적으로 격리된다.

## Approval Boundary

이 문서와 code generation plan은 2026-07-22T12:48:35.0725639+09:00에 사용자 응답 `승인`으로 승인되었다. Steps 1~9의 구현 및 proposal 생성을 허가한다. Step 10의 baseline label 확정은 generated proposal을 본 뒤 별도 승인을 받아야 한다.
