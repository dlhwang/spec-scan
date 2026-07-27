# U01 Generalization Baseline — Implementation Deep Interview

> **상태**: 답변 완료 — 모든 질문 Option A
> **목적**: 승인된 Functional/NFR Design을 concrete package, corpus, profile과 artifact layout으로 고정한 뒤 Consensus Planning에 진입한다.
> **코드 변경**: 본 질문 승인 전 금지

## 1. Deep Interview Trigger 판정

| Trigger | 판정 | 근거 |
| :--- | :---: | :--- |
| T1 Unresolved architectural choice | Yes | reusable evaluation contract와 JUnit-only runner의 source/package 경계가 보류됨 |
| T2 Undefined non-functional target | Yes | profile별 concrete timeout과 heap aggregation 방식이 보류됨 |
| T3 Multiple valid interpretations | Yes | 5~10 corpus를 checked-in fixture와 local snapshot으로 구성하는 비율이 미결정 |
| T4 Cross-cutting concern | Yes | immutable artifact의 physical layout, staging retention과 legacy artifact 공존 정책이 미결정 |
| T5 User signal | No | 사용자는 구현 세부 질문을 별도로 요청하지 않았지만 승인된 설계가 code design에서 결정을 요구함 |

## 2. 확인된 기존 구현 경계

- 기존 evaluation domain은 `io.atworks.specscan.analysis.domain.evaluation`에 있다.
- 기존 pure evaluation service/writer는 `io.atworks.specscan.analysis.support.evaluation`에 있다.
- 기존 `RuleEvaluationService`, `EvaluationReport`, `QualityGateConfig`, `golden-labels.json`은 이미 regression test에서 사용된다.
- `RealEstateGoldenRegressionTest`는 `REALESTATE_WORKSPACE`가 있을 때만 실행되는 local snapshot precedent다.
- 현재 production 정상 scan은 U01 baseline runner에 의존하지 않는다.

## Question 1: Source/package 경계와 기존 evaluation 호환성

### Option A: 기존 evaluation package를 additive 확장 (권장)

- 재사용 가능한 immutable contract와 pure service는 기존 `analysis.domain.evaluation`, `analysis.support.evaluation` 아래에 둔다.
- corpus filesystem adapter와 실행 harness만 test source set에 둔다.
- production 정상 scan wiring은 변경하지 않는다.
- 기존 `RuleEvaluationService`, `EvaluationReport`, `golden-labels.json`은 삭제·의미 변경하지 않고 legacy evidence로 유지한다.
- 신규 versioned corpus/baseline manifest는 별도 v1 contract로 추가한다.

장점: U02~U06이 U01 contract를 재사용할 수 있고 기존 regression을 깨지 않는다.

단점: evaluation package 안에 legacy metric model과 신규 baseline model이 당분간 공존한다.

### Option B: U01 구현 전체를 test source set에 둠

장점: production artifact에 신규 class가 포함되지 않는다.

단점: 후속 Unit에서 contract를 main으로 이동해야 하므로 G01 identity가 흔들릴 수 있다.

### Option C: 신규 `analysis.domain.baseline`/`analysis.support.baseline` top-level package 생성

장점: legacy evaluation과 물리적으로 명확히 분리된다.

단점: 기존 evaluation 책임과 중복 경계가 생기고 후속 U06의 package 선택이 복잡해진다.

### Option X: 기타

[Answer]: A
[Rationale]: 기존 evaluation package를 additive 확장하고 legacy evaluation evidence를 유지한다.

## Question 2: G01 corpus 구성

### Option A: 재현 가능한 5개 checked-in 현실형 corpus + optional RealEstate (권장)

- 서로 다른 package/aggregate/field/method 이름을 가진 독립 Spring MVC mini-repository fixture 5개를 checked-in resource로 둔다.
- 각 corpus는 약 6개 endpoint와 현재 Java graph/rule engine이 관찰할 수 있는 현실적인 controller/service/repository 구조를 가진다.
- base corpus와 이름·구조를 바꾼 rename/mutation holdout 1개를 tuning 입력에서 분리한다.
- `REALESTATE_WORKSPACE`는 여섯 번째 optional local snapshot으로 유지한다. 부재는 명시적 `EXCLUDED_OPTIONAL` diagnostic이며 required G01 evidence를 막지 않는다.

장점: CI와 로컬에서 외부 clone 없이 5~10 corpus 조건과 결정론 검증이 가능하다.

단점: 실제 대형 repository 다양성은 optional RealEstate와 후속 corpus 확장에 의존한다.

### Option B: 실제 local Git snapshot 5~10개를 required corpus로 사용

장점: 실제 repository 대표성이 가장 높다.

단점: 승인 전에 각 repository의 local root, pinned revision과 사용 허가가 필요하고 CI 재현성이 낮다.

### Option C: 기존 synthetic/evaluation resource만 재사용

장점: 파일 추가가 가장 적다.

단점: 독립 Spring MVC corpus와 endpoint 규모 acceptance를 충족하지 못한다.

### Option X: 기타

[Answer]: A
[Rationale]: 5개 checked-in 현실형 Spring MVC corpus와 optional RealEstate local snapshot을 사용한다.

## Question 3: Reviewed baseline과 generated evidence의 물리 배치

### Option A: reviewed input과 generated output을 분리 (권장)

- 승인 대상 v1 manifest/baseline은 `src/test/resources/rule-based-static-analysis/baseline/v1/`에 둔다.
- 실행 중 proposal/evidence bundle은 `build/reports/specscan/u01/<bundle-id>/`에 생성한다.
- publisher는 JSON/Markdown/digest를 쓴 뒤 completion manifest를 마지막에 기록한다.
- 자동 baseline 갱신이나 resource directory overwrite는 금지한다.
- 예상치 못한 publish 실패의 incomplete unique directory는 자동 삭제하지 않고 diagnostic과 함께 남긴다. completed reader는 이를 무시한다.

장점: review-controlled baseline과 일회성 evidence가 섞이지 않고 실패 조사 자료를 보존한다.

단점: 실패가 반복되면 `build/reports` 정리가 필요하다.

### Option B: generated bundle도 test resource 옆에 생성

장점: 비교 파일을 찾기 쉽다.

단점: test 실행이 version-controlled input을 오염시킬 수 있다.

### Option C: OS temp directory만 사용

장점: workspace가 깨끗하다.

단점: review와 실패 후 진단 보존이 어렵다.

### Option X: 기타

[Answer]: A
[Rationale]: reviewed v1 resource와 generated build report를 분리하고 incomplete evidence를 보존한다.

## Question 4: Timeout profile과 memory observation

### Option A: checked-in explicit profile + checkpoint sampled heap (권장)

- hidden default는 두지 않고 모든 실행이 immutable `EvaluationProfile`을 요구한다.
- checked-in smoke profile: single run 30초.
- G01 full profile: single run 10분.
- determinism suite: 동일 full profile 3회, suite guard 30분.
- 시간은 `System.nanoTime()` 기반 monotonic deadline으로 측정한다.
- memory는 `MemoryMXBean.getHeapMemoryUsage().getUsed()`를 run/corpus/endpoint checkpoint에서 표본화하여 `observedHeapPeakBytes` 최댓값으로 기록한다.
- memory sampling 실패는 diagnostic만 남기고 semantic verdict를 바꾸지 않는다.

장점: 환경 독립적인 JDK-only reference profile과 검증 가능한 metric 의미를 가진다.

단점: checkpoint 사이의 순간적인 실제 peak는 놓칠 수 있다.

### Option B: timeout은 system property로만 주입하고 checked-in 값 없음

장점: 환경별 조정이 쉽다.

단점: 같은 profile 재실행의 정체성과 CI 재현성이 약해진다.

### Option C: executor interrupt와 JVM memory-pool peak reset 사용

장점: 더 강한 취소와 peak 관찰이 가능하다.

단점: shared JVM test의 interrupt/peak state에 영향을 줄 수 있다.

### Option X: 기타

[Answer]: A
[Rationale]: explicit 30초/10분/30분 profile과 checkpoint-sampled heap observation을 사용한다.

## Question 5: U01 최초 schema migration 범위

### Option A: strict v1 registry와 migration contract만 구현 (권장)

- normal reader는 artifact kind + exact schema version으로 v1 reader만 선택한다.
- unsupported version과 migration path 부재를 stable diagnostic으로 검증한다.
- `ArtifactMigrator`/migration registry contract와 원본 보존 규칙은 구현한다.
- 실제 v1→v2 concrete migrator는 v2 schema가 생길 때 해당 Unit에서 추가한다. 존재하지 않는 과거 schema를 임의로 만들지 않는다.

장점: silent conversion을 막으면서 가짜 migration logic을 만들지 않는다.

단점: U01 시점에는 성공하는 concrete migration demo가 없다.

### Option B: legacy `golden-labels.json`을 v0으로 선언하고 v0→v1 migrator 구현

장점: concrete migration evidence를 즉시 만들 수 있다.

단점: legacy label과 신규 corpus/baseline aggregate의 의미가 달라 손실성 변환을 migration으로 오인할 수 있다.

### Option C: migration API도 v2까지 전부 보류

장점: U01 구현량이 줄어든다.

단점: 승인된 strict registry + explicit migrator 설계의 확장 경계를 code로 고정하지 못한다.

### Option X: 기타

[Answer]: A
[Rationale]: strict v1 reader와 migration contract를 구현하되 실제 concrete migration은 v2 schema가 생길 때 추가한다.

## 3. 답변 후 절차

1. 모든 답변의 누락·모순을 검증한다.
2. 확정 결정을 `specs/deep-interview-u01-generalization-baseline.md`에 영속화한다.
3. 4-stage Consensus Planning을 수행한다.
4. `pending-approval.md`에 최종 implementation plan과 ADR을 제시한다.
5. 별도 명시적 승인 전까지 application code와 test/resource corpus를 수정하지 않는다.
