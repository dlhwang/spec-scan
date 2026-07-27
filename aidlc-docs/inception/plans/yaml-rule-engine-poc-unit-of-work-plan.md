# YAML Rule Engine PoC — Unit of Work 계획

> **기준 요구사항**: `../requirements/yaml-rule-engine-poc/requirements-v2.md`
> **기준 설계**: `../application-design/yaml-rule-engine-poc/application-design.md`
> **상태**: Units Generation 승인 완료 (2026-07-22)
> **프로젝트 유형**: Brownfield 단일 Java/Gradle application

## 1. 목적

승인된 Application Design을 구현 순서와 검증 책임이 명확한 Unit of Work로 분해한다. Unit은 독립 배포 서비스가 아니라 동일 application 내부의 논리적 개발·검증 단위다.

## 2. Part 1 — Planning 체크리스트

- [x] Requirements v2, Workflow Planning과 Application Design 로드
- [x] User Stories 생략 결정 확인
- [x] 예비 U01~U06과 critical path 분석
- [x] Story Grouping, Dependencies, Team Alignment, Technical Considerations, Business Domain 질문 작성
- [x] Greenfield code organization 질문 비적용 판단
  - **Rationale**: brownfield 단일 Gradle module이며 Application Design에서 package 경계를 승인함
- [x] 모든 `[Answer]:` 수신
- [x] 답변의 누락·모순·모호성 검증
- [x] Unit of Work 계획 승인

## 3. Part 2 — Generation 체크리스트

- [x] `../application-design/yaml-rule-engine-poc/unit-of-work.md` 생성
- [x] `../application-design/yaml-rule-engine-poc/unit-of-work-dependency.md` 생성
- [x] `../application-design/yaml-rule-engine-poc/unit-of-work-story-map.md` 생성
- [x] 모든 요구사항과 Acceptance Contract를 Unit에 배정
- [x] Unit 경계와 순환 의존성 검증
- [x] per-unit Functional/NFR Design 진입 순서 확정
- [x] Units Generation 산출물 승인

## 4. 예비 분해안

| Unit | 예비 책임 | 선행 Unit |
| :--- | :--- | :--- |
| U01 Generalization Baseline | corpus manifest, disposition, golden snapshot, graph/semantic coverage 계약 | 없음 |
| U02 YAML Configuration & Schema | 세 YAML schema, strict validation, immutable bootstrap plan | U01 |
| U03 Typed Primitive Runtime | registry, binding, execution, budget/cycle, evaluation graph 준비 | U02 |
| U04 Core Semantic Recipes | binary, composite, standard guard, JDK Optional family | U03 |
| U05 Framework Packs & Delegated Cleanup | Spring Data/Security/JPA pack, DelegatedGuard correction | U03, U04 |
| U06 Cross-Repository Evaluation Gate | YAML primary/Java comparison, fallback, diff, Go/Partial/No-Go report | U01~U05 |

## 5. 분해 질문

## Question 1: Unit grouping 수준

### Option A: 예비 U01~U06을 6개 논리 Unit으로 유지 (권장)

안전망, schema, runtime, core recipe, framework/delegated, 최종 evaluation을 각각 독립 완료 조건으로 관리한다.

- 장점: 실패 위치와 rollback 경계가 명확하고 각 Unit의 검증 evidence가 분리됨
- 단점: 문서·승인 및 per-unit design 횟수가 늘어남

### Option B: 3개 Unit으로 통합

`Baseline`, `Engine(schema+runtime+recipes)`, `Evaluation`으로 묶는다.

- 장점: 관리 단위 감소
- 단점: engine Unit이 커지고 schema/runtime/recipe 실패가 한 경계에 섞임

### Option C: 단일 PoC Unit

전체를 하나의 Unit으로 취급한다.

- 장점: 관리가 단순
- 단점: 승인된 단계적 gate와 rollback/evidence 책임이 약해짐

### Option X: 기타

원하는 Unit 수와 합치거나 나눌 경계를 `[Rationale]:`에 작성해 주세요.

[Answer]: A
[Rationale]: 예비 U01~U06을 각각 독립 완료 조건과 검증 evidence를 갖는 논리 Unit으로 유지한다.

## Question 2: Unit 의존성과 진행 gate

### Option A: 선행 Unit evidence를 hard gate로 사용 (권장)

각 Unit의 필수 test/evaluation evidence가 승인되어야 다음 의존 Unit을 시작한다. 문서·fixture 준비만 계약 승인 후 제한적으로 병렬화한다.

- 장점: schema나 baseline 결함이 후속 runtime/recipe에 누적되지 않음
- 단점: critical path가 순차적임

### Option B: 구현은 병렬 진행하고 통합 시 gate 적용

U02~U05 구현을 가능한 범위에서 병렬화한 뒤 U06 전에 통합한다.

- 장점: 여러 팀이 있을 때 진행 속도 향상 가능
- 단점: 계약 변경과 재작업 위험이 큼

### Option C: U01과 U02만 hard gate, 이후 유연하게 진행

baseline/schema 이후 runtime과 recipe 작업을 부분 병렬화한다.

- 장점: 안전망을 유지하면서 일부 병렬화
- 단점: primitive 계약 변경 시 recipe 재작업 가능

### Option X: 기타

원하는 dependency gate를 `[Rationale]:`에 작성해 주세요.

[Answer]: A
[Rationale]: 선행 Unit의 필수 evidence 승인을 다음 Unit의 hard gate로 사용한다.

## Question 3: User Stories 생략에 따른 story map 기준

### Option A: Requirements와 Acceptance Criteria를 story 대체 단위로 사용 (권장)

R2-YAML-001~010 및 NFR-R2-001~009를 Unit에 매핑하고, 각 acceptance/test evidence를 추적한다.

- 장점: 이미 승인된 계약과 직접 연결되고 누락 검사가 가능
- 단점: 사용자 관점 story가 아닌 기술 capability map이 됨

### Option B: 내부 사용자 story를 새로 작성한 뒤 매핑

룰 작성자, 엔진 개발자, 평가 담당자 관점의 story를 추가한다.

- 장점: 사용 흐름과 가치 표현이 선명해짐
- 단점: Workflow Planning에서 생략한 User Stories 단계를 사실상 재도입

### Option C: Story map artifact를 N/A로 처리

Unit 정의와 dependency만 작성한다.

- 장점: 문서량 감소
- 단점: mandatory artifact와 요구사항 누락 검증이 약해짐

### Option X: 기타

원하는 mapping 기준을 `[Rationale]:`에 작성해 주세요.

[Answer]: A
[Rationale]: 생략된 User Stories 대신 승인된 Requirements와 Acceptance Criteria를 추적 단위로 사용한다.

## Question 4: Team alignment와 Unit ownership

### Option A: 단일 팀·순차 ownership을 기본 가정 (권장)

Unit은 조직 경계가 아니라 완료·검증 경계로 사용하고, 한 팀이 critical path 순서로 소유한다.

- 장점: 현재 확인되지 않은 조직 구조를 설계에 하드코딩하지 않음
- 단점: 실제 복수 팀 병렬 계획은 별도 조정 필요

### Option B: Package별 ownership을 Unit에 명시

domain recipe, support recipe, evaluation/test 책임을 서로 다른 owner로 가정한다.

- 장점: 코드 리뷰 책임이 명확
- 단점: 실제 팀 정보 없이 인위적 조직 경계를 만들 수 있음

### Option C: Unit별 독립 팀 ownership

U01~U06을 각각 독립 팀이 소유하는 것으로 설계한다.

- 장점: 대규모 조직에서 병렬화 가능
- 단점: 단일 application의 높은 계약 결합도와 맞지 않음

### Option X: 기타

실제 팀 또는 owner 제약이 있다면 `[Rationale]:`에 작성해 주세요.

[Answer]: A
[Rationale]: 확인되지 않은 조직 구조를 가정하지 않고 단일 팀의 순차 ownership을 기본으로 한다.

## Question 5: 배포 및 code organization 경계

### Option A: 기존 단일 Gradle module 내 논리 Unit (권장)

Application Design에서 승인한 `analysis.domain.recipe`와 `analysis.support.recipe` package를 사용하고 별도 deployable/module은 만들지 않는다.

- 장점: brownfield 구조와 PoC rollback 전략에 부합
- 단점: build-level 격리는 제공하지 않음

### Option B: YAML recipe engine을 별도 Gradle submodule로 분리

- 장점: build dependency와 API 경계가 강함
- 단점: PoC 범위를 넘어 module/build 구조 변경이 커짐

### Option C: evaluation/test source set에만 구현

- 장점: production artifact 격리가 가장 강함
- 단점: 향후 실제 engine 전환 가능성과 runtime integration 검증이 제한됨

### Option X: 기타

원하는 module/source-set 경계를 `[Rationale]:`에 작성해 주세요.

[Answer]: A
[Rationale]: 기존 단일 Gradle module과 승인된 recipe package 경계를 유지한다.

## Question 6: Business/domain 분해 축

### Option A: 기술 capability 기준 분해 유지 (권장)

schema/runtime/core/framework/evaluation을 Unit 경계로 삼고 특정 업무 domain이나 repository 이름으로 나누지 않는다.

- 장점: repository-independent PoC 목적과 일치
- 단점: 개별 classifier family의 독립 일정은 Unit 내부에서 관리

### Option B: Semantic family별 Unit으로 재분해

binary, composite, standard guard, Optional, framework family를 각각 Unit으로 만든다.

- 장점: family별 진척과 품질이 선명
- 단점: 공통 runtime 이후 작은 Unit이 과도하게 늘어남

### Option C: Framework pack을 각각 독립 Unit으로 분해

Spring Data, Spring Security, JPA, Delegated cleanup을 별도 Unit으로 만든다.

- 장점: optional capability별 Go/No-Go 판단 가능
- 단점: Phase 2 PoC의 문서와 dependency 수가 크게 증가

### Option X: 기타

원하는 capability/domain 분해 축을 `[Rationale]:`에 작성해 주세요.

[Answer]: A
[Rationale]: 특정 업무 domain이 아니라 baseline/schema/runtime/core/framework/evaluation 기술 capability를 분해 축으로 사용한다.

## 6. 답변 방법

각 질문의 `[Answer]:`에 `A`, `B`, `C` 또는 `X`를 작성한다. `X` 또는 조합 선택 시 `[Rationale]:`에 적용 기준을 구체적으로 작성한다.

모든 답변을 검증한 뒤 Unit of Work 계획 승인 게이트를 별도로 진행한다. 계획 승인 전 Unit 산출물은 생성하지 않는다.
