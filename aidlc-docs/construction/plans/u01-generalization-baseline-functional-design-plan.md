# U01 Generalization Baseline — Functional Design 계획

> **Unit**: U01 Generalization Baseline
> **상태**: Functional Design 완료 — 승인 대기
> **상위 Unit 정의**: `../../inception/application-design/yaml-rule-engine-poc/unit-of-work.md`

## 1. 설계 범위

- 5~10개 Spring MVC corpus의 manifest와 endpoint scope
- 현재 Java engine snapshot과 사람이 승인한 baseline의 관계
- `PRESERVE`, `REPLACE`, `UNSUPPORTED` disposition lifecycle
- 동일 source regression과 rename/mutation holdout의 identity model
- graph coverage와 semantic coverage의 계산 입력·출력
- baseline runner의 실패·누락·재승인 규칙

YAML schema, primitive runtime, recipe 구현과 최종 Go/No-Go report는 U02 이후 Unit의 책임이다.

## 2. Functional Design 체크리스트

### Context

- [x] U01 책임, 입출력, DoD와 Exit Gate 로드
- [x] R2-YAML-009, R2-YAML-010 및 보조 requirement 분석
- [x] Business Logic, Domain Model, Business Rules, Data Flow, Integration, Error Handling, Scenario category 평가
- [x] Frontend Components 비적용 판단
  - **Rationale**: 내부 정적 분석 evaluation baseline Unit이며 UI가 없음
- [x] context-specific 질문 작성
- [x] 모든 `[Answer]:` 수신
- [x] 누락·모순·모호성 검증

### Mandatory Artifacts

- [x] `../u01-generalization-baseline/functional-design/business-logic-model.md`
- [x] `../u01-generalization-baseline/functional-design/business-rules.md`
- [x] `../u01-generalization-baseline/functional-design/domain-entities.md`
- [x] Functional Design 완전성 및 일관성 검증
- [x] U01 Functional Design 승인

## 3. 설계 질문

## Question 1: Baseline source of truth

### Option A: Java snapshot + 사람 승인 disposition/expectation의 이중 계층 (권장)

현재 Java engine 결과는 자동 수집한 `ObservedSnapshot`으로 보존한다. 사람이 검토한 `BaselineManifest`가 각 observation을 `PRESERVE`, `REPLACE`, `UNSUPPORTED`로 분류하고 필요한 corrected expectation을 정의한다.

- 장점: 현재 동작을 사실로 보존하면서 기존 오탐을 정답으로 고정하지 않음
- 단점: 초기 labeling review가 필요

### Option B: 현재 Java output을 모두 baseline 정답으로 사용

- 장점: 자동 생성이 간단
- 단점: DelegatedGuard 같은 알려진 잘못된 결과까지 보존

### Option C: 사람이 기대 결과를 처음부터 전부 작성

- 장점: 현재 구현 편향이 적음
- 단점: 누락과 labeling 비용이 크며 관찰 결과와 연결이 약함

### Option X: 기타

[Answer]: A
[Rationale]: Java 관찰값과 사람이 승인한 disposition/corrected expectation을 분리한다.

## Question 2: 동일 source와 rename holdout의 candidate identity

### Option A: Exact identity와 Scenario identity를 분리 (권장)

- 동일 source regression은 operation key, predicate candidate ID, rule/constraint와 evidence fingerprint로 `ObservationIdentity`를 만든다.
- rename/mutation 비교는 manifest가 부여한 안정적인 `ScenarioId`와 semantic role로 대응시킨다.
- source line, concrete field name 또는 raw node ID를 cross-variant identity로 사용하지 않는다.

- 장점: 정밀 snapshot과 rename 일반성 검증을 동시에 만족
- 단점: manifest에 scenario/role metadata가 추가됨

### Option B: predicate candidate ID 하나로 모두 비교

- 장점: 구현 단순
- 단점: source rename/line 변화에 취약

### Option C: normalized target/operator/value만으로 비교

- 장점: source 구조에 덜 민감
- 단점: 같은 endpoint의 유사 candidate가 충돌할 수 있음

### Option X: 기타

[Answer]: A
[Rationale]: 동일 source의 정확한 회귀 identity와 rename/mutation의 안정적 scenario identity를 별도로 사용한다.

## Question 3: Disposition 승인 규칙

### Option A: 모든 entry에 rationale, `REPLACE`에는 corrected expectation 필수 (권장)

`PRESERVE`는 기존 결과를 유지할 근거, `REPLACE`는 교정 이유와 expected observation, `UNSUPPORTED`는 명시적 diagnostic expectation을 가져야 한다. 빈 disposition이나 자동 추론은 허용하지 않는다.

- 장점: 회귀와 교정의 감사 가능성이 높음
- 단점: manifest 작성량 증가

### Option B: disposition만 기록하고 rationale은 선택

- 장점: 작성이 빠름
- 단점: 변경 이유와 승인 근거가 약함

### Option C: rule/category 단위 기본 disposition 상속

- 장점: 반복 입력 감소
- 단점: 개별 예외가 숨겨질 수 있음

### Option X: 기타

[Answer]: A
[Rationale]: 모든 disposition에 근거를 요구하고 REPLACE/UNSUPPORTED에는 교정 또는 diagnostic expectation을 필수화한다.

## Question 4: Corpus와 fixture 저장 방식

### Option A: Hybrid manifest (권장)

작은 deterministic unit/integration fixture는 test resources에 체크인한다. 실제·현실적 독립 corpus는 local snapshot 경로와 commit/content digest를 manifest에 고정하고, 평가 전에 availability/integrity를 검사한다.

- 장점: 빠른 hermetic test와 현실적인 cross-repository 평가를 함께 제공
- 단점: 전체 평가에는 corpus 준비 절차가 필요

### Option B: 모든 corpus를 test resources에 체크인

- 장점: 완전한 hermetic 실행
- 단점: 저장소 크기, 라이선스와 유지보수 부담

### Option C: 평가 시 remote repository를 clone

- 장점: local 저장 공간 감소
- 단점: network/branch 변경으로 재현성과 안전성이 낮음

### Option X: 기타

[Answer]: A
[Rationale]: 작은 fixture는 체크인하고 현실적 corpus는 local snapshot과 digest로 고정하는 hybrid manifest를 사용한다.

## Question 5: U01 runner integration

### Option A: Test/evaluation harness 전용 (권장)

U01 runner는 test 또는 explicit evaluation entry point로만 실행하며 production CLI와 정상 scan pipeline에 연결하지 않는다.

- 장점: 정상 scan 격리와 PoC rollback 원칙에 부합
- 단점: 운영 CLI 형태는 U06 또는 후속 단계에서 결정

### Option B: 신규 production CLI command 추가

- 장점: 사용 시연이 쉬움
- 단점: U01이 production interface까지 변경

### Option C: 수동 script와 문서만 제공

- 장점: 구현량 최소
- 단점: 결정적 자동 evidence gate를 만들기 어려움

### Option X: 기타

[Answer]: A
[Rationale]: runner는 test/explicit evaluation 전용이며 정상 scan과 production CLI에 연결하지 않는다.

## Question 6: Corpus 누락·무결성 실패 처리

### Option A: required/optional 구분, required 실패는 gate 실패 (권장)

manifest의 required corpus가 없거나 digest가 다르면 전체 G01을 실패시킨다. optional corpus는 명시적 `CORPUS_UNAVAILABLE` diagnostic과 coverage 제외 사유를 남기고 계속한다.

- 장점: 최소 신뢰 기준을 보장하면서 선택 corpus의 일시적 부재를 관찰 가능
- 단점: required/optional 선정 규칙이 필요

### Option B: 하나라도 누락되면 전체 실패

- 장점: 가장 엄격
- 단점: 선택 corpus 하나가 전체 개발을 차단

### Option C: 누락 corpus는 모두 경고 후 계속

- 장점: 실행 성공률이 높음
- 단점: corpus 축소가 조용히 합격 결과를 만들 수 있음

### Option X: 기타

[Answer]: A
[Rationale]: required corpus 누락·무결성 실패는 G01 실패, optional corpus는 명시적 diagnostic 후 제외한다.

## Question 7: Re-baseline 정책

### Option A: 자동 갱신 금지, semantic diff와 명시적 승인 필수 (권장)

Java engine 또는 graph 결과가 변하면 기존 manifest를 자동 덮어쓰지 않는다. 신규/소실/변경 observation과 영향받는 disposition을 제시하고 승인된 변경만 새 baseline version으로 저장한다.

- 장점: 회귀를 snapshot 갱신으로 숨길 수 없음
- 단점: 의도된 대규모 변경 시 review 비용 증가

### Option B: 테스트 실행 옵션으로 자동 갱신 허용

- 장점: 개발 편의성
- 단점: 잘못된 결과를 무심코 승인할 위험

### Option C: Java 결과 변화는 자동, corrected expectation만 수동

- 장점: 일부 관리 비용 감소
- 단점: `PRESERVE` 회귀가 자동 흡수될 수 있음

### Option X: 기타

[Answer]: A
[Rationale]: baseline 자동 갱신을 금지하고 semantic diff와 명시적 승인을 새 version의 조건으로 한다.

## 4. 답변 방법

각 `[Answer]:`에 `A`, `B`, `C` 또는 `X`를 작성한다. `X` 또는 조합 선택은 `[Rationale]:`에 정확한 적용 조건을 작성한다.

모든 답변을 검증한 뒤 Functional Design 산출물을 생성한다. 명시적 승인 전 NFR Requirements로 진행하지 않는다.
