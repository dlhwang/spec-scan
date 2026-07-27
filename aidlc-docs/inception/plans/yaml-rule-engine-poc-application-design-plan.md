# YAML Rule Engine PoC — Application Design 계획

> **기준 요구사항**: `../requirements/yaml-rule-engine-poc/requirements-v2.md`
> **기준 실행 계획**: `yaml-rule-engine-poc-execution-plan.md`
> **상태**: 설계 산출물 작성 완료 — 승인 대기

## 1. 설계 범위

- engine configuration, semantic recipe와 framework catalog의 컴포넌트 경계
- YAML load/validate/compile 계약
- typed primitive registry와 runtime binding environment
- 기존 Java classifier와의 shadow dual-run 및 differential evaluation
- unclassified/unresolved/partial diagnostic 출력 경계
- DelegatedGuard의 domain hardcoding 제거 전략

상세 primitive 알고리즘과 구현 로직은 Units Generation 및 Functional Design에서 다룬다.

## 2. Application Design 실행 체크리스트

### Context and Decisions

- [x] Requirements Revision 2와 Workflow Planning 로드
- [x] 기존 semantic/rule/yaml package 경계 확인
- [x] Application Design에 필요한 미결정 항목 식별
- [x] 설계 질문 6개 답변 수신
- [x] 6개 답변의 누락 및 선택값 유효성 검증
- [x] Q3 실행 권위와 Q5 출력 경계의 충돌 식별
- [x] 후속 질문 Q7 답변 수신 및 모순 해소

### Mandatory Artifacts

- [x] `application-design/yaml-rule-engine-poc/components.md` — 컴포넌트 정의와 상위 책임
- [x] `application-design/yaml-rule-engine-poc/component-methods.md` — 인터페이스와 상위 method signature
- [x] `application-design/yaml-rule-engine-poc/services.md` — orchestration service와 실행 흐름
- [x] `application-design/yaml-rule-engine-poc/component-dependency.md` — 의존성, 통신 방식과 data flow
- [x] `application-design/yaml-rule-engine-poc/application-design.md` — 통합 설계 문서
- [x] 설계 완전성 및 일관성 검증
- [x] Review correction — 정상 scan의 YAML bootstrap 비의존성 명시
- [x] Review correction — default-budget normal graph와 YAML-budget evaluation graph 분리
- [x] Application Design 승인

## 3. 설계 질문

## Trade-off Question 1: 신규 컴포넌트의 package 경계

### Option A: 기존 모듈 내부의 독립 recipe package (권장)

`analysis.domain.recipe`와 `analysis.support.recipe`를 만들고 기존 `rule`, `semantic`, `rule.yaml`과 명시적 adapter로 연결한다.

- **Pros**: 기존 Gradle 구조를 유지하면서 새 책임을 격리; rollback과 PoC 제거가 쉬움
- **Cons**: 기존 classifier와 신규 recipe 사이 adapter가 필요
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 영향 낮음
  - Maintainability: 높음
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 기존 `support.rule.yaml`과 `support.semantic` 직접 확장

신규 model/runtime을 기존 package에 배치한다.

- **Pros**: 파일 이동과 adapter 수가 적음
- **Cons**: 기존 책임과 PoC runtime이 혼합되어 경계가 흐려짐
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 영향 낮음
  - Maintainability: 중간 이하
  - Implementation Cost: 낮음
  - Risk: 중간

### Option C: 별도 Gradle module

YAML recipe engine을 독립 module로 구성한다.

- **Pros**: 물리적 격리가 가장 강함
- **Cons**: 현재 단일 module PoC에 과도한 build/module 변경
- **Impact Analysis**:
  - Compatibility: 중간
  - Performance: 영향 낮음
  - Maintainability: 장기적으로 높으나 초기 복잡도 큼
  - Implementation Cost: 높음
  - Risk: 중간 이상

### Option X: 기타

원하는 package/module 경계를 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Trade-off Question 2: YAML load와 evaluation 실행 방식

### Option A: PoC evaluation 시작 시 compile-once immutable plan (권장)

YAML을 load/validate한 뒤 typed `CompiledRecipePlan`으로 한 번 변환하고 명시적 PoC evaluation의 모든 endpoint에서 재사용한다. Q7 Option A에 따라 정상 scan은 이 plan에 의존하지 않는다.

- **Pros**: 실행 중 schema/type 오류 제거; 결정론과 성능 측정이 쉬움
- **Cons**: compile model과 diagnostic 설계가 필요
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 가장 예측 가능
  - Maintainability: 높음
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: Candidate 평가마다 YAML AST 해석

각 predicate/candidate마다 YAML tree를 직접 해석한다.

- **Pros**: 초기 구현이 단순해 보임
- **Cons**: 반복 parsing/type 검사, 런타임 오류와 성능 편차가 발생
- **Impact Analysis**:
  - Compatibility: 중간
  - Performance: 낮음
  - Maintainability: 낮음
  - Implementation Cost: 초기 낮음, 후속 높음
  - Risk: 높음

### Option C: Version별 lazy compile cache

처음 사용 시 compile하고 rule-pack version별 cache를 유지한다.

- **Pros**: 향후 hot-reload에 유리
- **Cons**: Phase 4가 아닌 PoC에 cache/version 동시성 복잡도를 유입
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 높음
  - Maintainability: 중간
  - Implementation Cost: 높음
  - Risk: 중간

### Option X: 기타

원하는 load/compile 방식을 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Trade-off Question 3: Java와 YAML 경로의 실행 권위

### Option A: Java authoritative + YAML shadow dual-run (권장)

Phase 2 동안 기존 Java 결과를 정상 출력으로 유지하고 YAML 결과는 비교 report에만 사용한다. `REPLACE` 항목은 corrected expectation으로 별도 평가한다.

- **Pros**: 기존 출력 안정성 및 rollback 용이; diff 근거가 명확
- **Cons**: 동일 graph를 두 경로에서 평가하는 PoC 비용 발생
- **Impact Analysis**:
  - Compatibility: 가장 높음
  - Performance: 평가 비용 증가
  - Maintainability: 전환 근거가 명확
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: Rule별 YAML/Java 선택 switch

이관 완료된 recipe만 YAML이 authoritative가 되고 나머지는 Java를 사용한다.

- **Pros**: 점진적 전환을 실제로 시연 가능
- **Cons**: Phase 2 중 결과 권위가 혼합되고 rollback/충돌 규칙이 복잡
- **Impact Analysis**:
  - Compatibility: 중간 이상
  - Performance: 중간
  - Maintainability: 중간
  - Implementation Cost: 중간 이상
  - Risk: 중간

### Option C: YAML primary + Java fallback

YAML 결과가 없거나 실패할 때만 Java classifier를 실행한다.

- **Pros**: YAML 중심 구조를 빠르게 확인
- **Cons**: silent semantic regression을 fallback이 가릴 수 있음
- **Impact Analysis**:
  - Compatibility: 낮음
  - Performance: 예측 어려움
  - Maintainability: 중간 이하
  - Implementation Cost: 중간
  - Risk: 높음

### Option X: 기타

Phase 2 실행 권위를 `[Rationale]:`에 설명해 주세요.

[Answer]: C
[Rationale]: 

## Trade-off Question 4: YAML 물리 파일 구성

### Option A: 책임별 3개 파일 (권장)

`engine-config.yaml`, `semantic-recipes.yaml`, `framework-catalog.yaml`을 각각 load하고 하나의 immutable runtime snapshot으로 조립한다.

- **Pros**: 변경 책임과 schema가 분명; domain rule 혼입 검사가 쉬움
- **Cons**: 파일 간 version/reference 일관성 검증 필요
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 영향 낮음
  - Maintainability: 높음
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 하나의 rule-pack YAML

단일 root 아래 `engine`, `recipes`, `catalog` section을 둔다.

- **Pros**: 배포 및 fixture 관리가 단순
- **Cons**: 독립 변경과 schema 책임이 한 파일에 결합
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 영향 낮음
  - Maintainability: 중간
  - Implementation Cost: 낮음
  - Risk: 중간

### Option C: Engine config 분리 + recipe/catalog 결합

runtime config만 별도 파일로 두고 semantic 지식은 하나의 rule-pack으로 묶는다.

- **Pros**: 운영 설정과 의미 규칙을 분리하면서 파일 수를 줄임
- **Cons**: recipe와 library catalog 변경 수명주기가 결합
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 영향 낮음
  - Maintainability: 중간 이상
  - Implementation Cost: 낮음~중간
  - Risk: 낮음~중간

### Option X: 기타

원하는 물리 구성을 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Trade-off Question 5: PoC diagnostic 출력 경계

### Option A: 별도 evaluation report에만 신규 상태 출력 (권장)

기존 production candidate/output은 변경하지 않고 shadow run의 `UNCLASSIFIED`, `UNRESOLVED`, `PARTIAL_ANALYSIS`와 diff를 별도 report에 기록한다.

- **Pros**: 기존 출력 계약 무변경; PoC rollback이 쉬움
- **Cons**: 실제 exporter 계약 반영은 후속 단계로 남음
- **Impact Analysis**:
  - Compatibility: 가장 높음
  - Performance: 낮은 추가 비용
  - Maintainability: PoC 격리가 명확
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 기존 candidate/output diagnostic 모델 확장

신규 상태를 현재 pipeline 결과와 exporter에 직접 포함한다.

- **Pros**: 최종 운영 형태와 가까움
- **Cons**: PoC가 기존 JSON/OpenAPI 출력 계약까지 변경
- **Impact Analysis**:
  - Compatibility: 낮음~중간
  - Performance: 영향 낮음
  - Maintainability: 장기적으로 높음
  - Implementation Cost: 높음
  - Risk: 높음

### Option C: 기존 모델 확장 + 별도 evaluation report

두 출력 모두 제공한다.

- **Pros**: 운영 계약과 상세 평가를 모두 검증
- **Cons**: Phase 2 범위와 구현량 증가
- **Impact Analysis**:
  - Compatibility: 중간
  - Performance: 추가 비용
  - Maintainability: 중간
  - Implementation Cost: 매우 높음
  - Risk: 중간 이상

### Option X: 기타

원하는 diagnostic 출력 경계를 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Trade-off Question 6: DelegatedGuard의 Phase 2 처리

### Option A: 기본 pack에서 제거하고 unresolved evidence만 보고 (권장)

현재 domain-specific 결과를 중단하고 generic target/operator가 증명되지 않으면 evaluation report에 `UNRESOLVED_DELEGATED_GUARD`를 남긴다.

- **Pros**: 잘못된 의미 생성을 즉시 차단; 무추측 원칙 준수
- **Cons**: 기존 legacy output 일부가 corrected expectation으로 변경
- **Impact Analysis**:
  - Compatibility: 의도된 breaking correction
  - Performance: 영향 낮음
  - Maintainability: 높음
  - Implementation Cost: 낮음~중간
  - Risk: 낮음

### Option B: Phase 2에서 완전 일반화 구현

argument/parameter/return/domain origin을 연결해 resolved delegated semantics를 생성한다.

- **Pros**: 가장 높은 탐지 범위
- **Cons**: U05 복잡도가 커지고 전용 primitive를 만들 위험
- **Impact Analysis**:
  - Compatibility: corrected expectation 필요
  - Performance: graph traversal 증가
  - Maintainability: 증명 성공 시 높음
  - Implementation Cost: 높음
  - Risk: 높음

### Option C: Java legacy path에만 유지하고 YAML 평가에서 제외

현재 출력은 유지하되 baseline을 `REPLACE`로 표시하고 이번 PoC에서는 직접 교정하지 않는다.

- **Pros**: PoC 구현 범위 축소
- **Cons**: 알려진 잘못된 결과가 정상 출력에 계속 남음
- **Impact Analysis**:
  - Compatibility: 높음
  - Performance: 영향 없음
  - Maintainability: 낮음
  - Implementation Cost: 낮음
  - Risk: 중간 이상

### Option X: 기타

DelegatedGuard 처리 방향을 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 

## Follow-up Trade-off Question 7: YAML primary의 적용 경계

Q3의 `YAML primary + Java fallback`은 YAML 결과가 정상 scan 결과를 결정한다는 의미인 반면, Q5의 `별도 evaluation report에만 신규 상태 출력`은 기존 Java production output을 변경하지 않는다는 의미다. 동일한 정상 scan 경로에는 두 결정을 함께 적용할 수 없으므로, Phase 2에서 YAML primary가 적용되는 실행 경계를 확정해야 한다.

### Option A: 격리된 PoC evaluation에서만 YAML primary (권장)

정상 scan은 기존 Java 결과를 계속 authoritative output으로 사용한다. 별도 evaluation runner에서는 YAML을 primary로 실행하고, no-match·compile/runtime failure가 발생하면 Java fallback 결과와 fallback 발생 사실을 함께 기록한다. 두 경로의 결과 차이는 항상 report에 남긴다.

- **Pros**: Q3의 YAML 중심 실행과 Q5의 production output 무변경을 모두 검증 가능; fallback이 가린 결손도 관찰 가능
- **Cons**: 정상 scan과 evaluation runner라는 두 실행 mode의 경계를 명확히 유지해야 함
- **Impact Analysis**:
  - Compatibility: 가장 높음
  - Performance: 정상 scan 영향 없음, evaluation 비용 증가
  - Maintainability: 전환 판단 근거가 명확
  - Implementation Cost: 중간
  - Risk: 낮음~중간

### Option B: 정상 scan에서 YAML primary + Java fallback

Q3을 우선하여 정상 scan 결과도 YAML이 결정한다. 이 선택은 Q5의 Option A를 폐기하고, 기존 candidate/output 계약에 YAML 결과 및 fallback diagnostic을 반영하는 방향으로 설계를 변경한다.

- **Pros**: 실제 전환 구조를 직접 시연
- **Cons**: PoC가 production output을 변경하며 silent regression과 rollback 위험이 커짐
- **Impact Analysis**:
  - Compatibility: 낮음
  - Performance: fallback 빈도에 따라 변동
  - Maintainability: fallback 정책과 결과 권위가 복잡
  - Implementation Cost: 높음
  - Risk: 높음

### Option C: Q3을 Java authoritative shadow dual-run으로 변경

Q5를 우선하여 정상 scan과 evaluation 모두 Java를 기준 결과로 두고 YAML은 비교 결과만 생성한다. 기존 Q3 선택 C를 Option A로 변경하는 결정이다.

- **Pros**: 가장 단순하고 안전한 differential evaluation
- **Cons**: YAML이 실제 authoritative path로 동작하는 장면은 이번 Phase에서 검증하지 못함
- **Impact Analysis**:
  - Compatibility: 가장 높음
  - Performance: dual-run 비용 증가
  - Maintainability: 단순
  - Implementation Cost: 중간
  - Risk: 가장 낮음

### Option X: 기타

YAML primary와 production output의 적용 경계를 `[Rationale]:`에 설명해 주세요.

[Answer]: A
[Rationale]: 정상 scan의 외부 출력 계약은 그대로 유지하고, 별도 PoC evaluation에서 YAML authoritative 동작과 Java fallback을 관찰한다.
