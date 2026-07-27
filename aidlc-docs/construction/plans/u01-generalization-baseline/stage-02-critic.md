# Stage 2: Critic — U01 Generalization Baseline

## Iteration 1

## Critique Items

### [C1] Confined view와 legacy path-based analyzer 사이의 불완전한 bridge

- **Target Section**: File Plan #15, #19, #29
- **Problem**: `SpringStaticScanService`와 graph builder는 `RepositorySource.workspacePath`를 통해 raw filesystem path를 다시 순회한다. `ConfinedCorpusView`를 검증했다는 사실만으로 기존 analyzer에 read-only capability만 전달된다고 볼 수 없고, preflight 후 symlink 변경 같은 TOCTOU 위험도 남는다.
- **Requested Action**: confined view가 열거·읽은 byte만 caller-owned isolated evaluation workspace로 materialize하고 digest를 다시 확인하는 bridge를 계획에 추가한다. Java adapter는 original corpus path가 아니라 prepared evaluation workspace만 받아야 한다. materializer는 target build/process를 호출하지 않고 caller-owned temp lifecycle을 존중해야 한다.
- **Severity**: HIGH

### [C2] 최초 proposal run과 approved baseline 의존성의 순환

- **Target Section**: Principles #9, File Plan #23, #37, #41, Execution Sequence #8~10
- **Problem**: 최초 approved baseline을 만들기 위해 current observation/proposal이 필요하지만 coordinator 계획은 strict baseline read를 analysis 전 필수로 둔다. 이대로면 proposal을 만들 baseline이 없어 최초 run이 불가능하다.
- **Requested Action**: explicit run purpose와 purpose별 input invariant를 도입한다. `SNAPSHOT_PROPOSAL`은 corpus/profile만 요구하고 baseline을 읽지 않으며 항상 non-passing proposal을 생성한다. `G01_DIFF`/`G01_DETERMINISM`은 approved baseline을 필수로 요구한다.
- **Severity**: HIGH

### [C3] Optional RealEstate digest identity

- **Target Section**: File Plan #39~40
- **Problem**: machine-local RealEstate snapshot의 exact digest를 checked-in manifest에 고정하면 사용자의 pinned checkout과 쉽게 불일치한다. 반대로 digest를 생략하면 integrity requirement를 위반한다.
- **Requested Action**: optional local snapshot entry는 checked-in logical entry와 별도 local binding file/system property를 사용하고, 실행 시 supplied expected digest와 optional commit ref가 모두 있어야 AVAILABLE로 처리하도록 명시한다. 값이 없으면 `EXCLUDED_OPTIONAL`로 처리한다.
- **Severity**: MEDIUM

### [C4] Legacy output parity의 비교 범위

- **Target Section**: File Plan #19, #36
- **Problem**: adapter가 graph rule engine을 별도로 평가하면 `RuleOutputService` 내부 method-scope traversal과 drift할 수 있다. endpoint output equality만으로 raw candidate scope drift를 식별하기 어렵다.
- **Requested Action**: external `EndpointRuleOutput` parity와 함께 graph ID별 candidate identity/rule/status를 비교하고, 차이가 있으면 adapter parity diagnostic으로 실패하도록 테스트 범위를 명시한다.
- **Severity**: MEDIUM

## Verdict

- **Decision**: REJECT
- **Rationale**: C1과 C2는 각각 보안 경계와 최초 baseline 생성 가능성을 깨는 high-severity 결함이다. Planner 수정 후 재검토가 필요하다.

## Residual Risks

| ID | Risk Item | Probability | Impact | Mitigation Strategy |
| :--- | :--- | :---: | :---: | :--- |
| R1 | JavaParser/static parser의 환경별 memory 변동 | High | Low | metric을 semantic equality에서 제외하고 observed sample로 명명 |
| R2 | 현실형 mini-repository가 실제 대형 repo 다양성을 완전히 대변하지 못함 | Medium | Medium | optional RealEstate와 후속 U06 corpus 확장, G01을 Go 판정으로 사용하지 않음 |
| R3 | Windows junction 탐지 API의 filesystem별 차이 | Medium | High | real path descendant + re-materialization + platform-conditional negative test |

---

## Iteration 2

## Resolution Audit

### [C1] Confined view bridge

- **Result**: RESOLVED
- **Evidence**: Planner Principle #5, File Plan #16a/#19/#29과 Execution Sequence #3이 original corpus를 confined bytes로 읽고 caller-owned isolated workspace에 재구성한 뒤 legacy analyzer에는 prepared workspace만 전달하도록 고정했다.

### [C2] Initial proposal bootstrap

- **Result**: RESOLVED
- **Evidence**: Planner Principle #8, File Plan #8a/#23/#37과 Execution Sequence #9~11이 `SNAPSHOT_PROPOSAL`과 approved-baseline-required G01 mode를 분리했다.

### [C3] Optional RealEstate identity

- **Result**: RESOLVED
- **Evidence**: File Plan #39~40이 logical entry와 local expected digest/commit binding을 분리하고 binding 부재를 optional exclusion으로 처리한다.

### [C4] Adapter parity

- **Result**: RESOLVED
- **Evidence**: File Plan #19/#36이 external endpoint output과 graph별 candidate identity/rule/status를 함께 비교한다.

## Additional Audit

### [C5] Manual labeling hard gate

- **Target Section**: Execution Sequence #9~11
- **Assessment**: proposal 생성과 approved baseline 확정 사이에 별도 사용자 승인 지점이 있어 no-auto-rebaseline 원칙과 일치한다. plan approval 자체가 label approval로 오인되지 않는다.
- **Severity**: LOW

### [C6] Test task isolation

- **Target Section**: File Plan #1, #38, #46
- **Assessment**: 신규 Gradle task는 opt-in이며 기존 `test`/`ruleEvaluation` 의미와 production entry point를 변경하지 않는다. full `test`는 legacy/normal-scan regression을 계속 실행한다.
- **Severity**: LOW

## Verdict

- **Decision**: OKAY
- **Rationale**: 1차 high-severity 보안 bridge와 bootstrap 순환이 제거됐고, file plan은 run-purpose invariant, manual labeling gate, legacy parity와 failure evidence를 구현 가능한 수준으로 명시한다.

## Residual Risks

| ID | Risk Item | Probability | Impact | Mitigation Strategy |
| :--- | :--- | :---: | :---: | :--- |
| R1 | checkpoint sampling이 실제 순간 heap peak를 놓침 | High | Low | field를 `observedHeapPeakBytes`로 명명하고 semantic verdict에서 제외 |
| R2 | mini-repository가 대형 실제 repository 다양성을 완전히 대변하지 못함 | Medium | Medium | optional RealEstate와 U06 확장; G01은 Go 판정이 아님을 report에 명시 |
| R3 | source가 confinement read와 materialization 사이에 변경됨 | Low | High | materialized digest 재검증, mismatch abort, original path를 analyzer에 전달하지 않음 |
| R4 | Java adapter의 duplicate rule evaluation이 future engine change와 drift | Medium | Medium | output + candidate parity regression과 stable adapter diagnostic |
| R5 | baseline manual review가 구현 완료를 지연 | Medium | Medium | proposal bundle을 actionable diff/rationale template로 출력하고 G01을 의도적으로 대기 |
