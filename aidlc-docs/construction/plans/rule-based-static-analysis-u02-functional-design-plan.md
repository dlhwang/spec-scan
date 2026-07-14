# Unit 02 후보와 해석 모델 Functional Design 계획

## Metadata

- **Unit**: `rule-based-static-analysis-u02`
- **Stage**: Functional Design
- **Created**: `2026-07-14T12:36:35+09:00`
- **Status**: Functional Design 생성 완료, 승인 대기
- **Source Design**: `docs/rule-based-static-analysis/02-candidate-and-resolution-model.md`

## 단계 계획

- [x] Unit 01 완료 승인 기록
- [x] Unit 02 책임과 비목표 확인
- [x] 기존 `ValidationCandidate`, `ApiCondition`, `NormalizedResult` 계약 확인
- [x] Unit 01 Fact Graph와의 경계 확인
- [x] 구현 계약에 영향을 주는 모호성 식별
- [x] 트레이드오프 질문 작성
- [x] 사용자 답변 검증
- [x] Business Logic Model 작성
- [x] Business Rules 작성
- [x] Domain Entities 작성
- [x] Functional Design 내용 검증
- [ ] Functional Design 승인

## 확정된 설계 경계

- Unit 02는 후보, 단계별 해석 상태, constraint, evidence, diagnostic의 도메인 계약만 도입한다.
- 기존 `ValidationCandidate`, `ApiCondition`, `NormalizedResult`는 이 Unit에서 제거하거나 출력 스키마로 즉시 교체하지 않는다.
- `PredicateType`은 관찰한 코드 구조만 표현하고 프레임워크나 비즈니스 의미를 포함하지 않는다.
- 구체적인 비즈니스 정체성은 확장 가능한 `ruleId`에 두고 `BusinessRuleCategory`는 상위 분류로만 사용한다.
- Rule 매칭 구현, target path 추론 알고리즘, 기존 출력 변환은 후속 Unit 범위다.
- 소스에서 확인하지 못한 target, operator, expected value를 사실처럼 채우지 않는다.

## Trade-off Question 1

의미를 아직 해석하지 못한 후보의 `BusinessRuleCategory`를 어떻게 표현할지 선택한다.

### Option A: `UNKNOWN` category 추가 (Recommended)

`BusinessRuleCategory.UNKNOWN`을 추가해 category를 항상 non-null로 유지한다. `OTHER`는 의미는 해석됐지만 상위 taxonomy에 전용 항목이 없는 Rule에만 사용한다.

- **Pros**: 미해석과 해석된 기타 Rule을 명확히 구분하고 직렬화 계약이 단순함
- **Cons**: taxonomy enum에 기술적 상태값 하나가 추가됨
- **Impact Analysis**:
  - Compatibility: 신규 병렬 모델이라 기존 출력 영향 없음
  - Performance: 영향 없음
  - Maintainability: null 분기 없이 상태 조합 검증 가능
  - Implementation Cost: 낮음
  - Risk: 낮음

### Option B: 미해석 category는 nullable

`semanticStatus=UNRESOLVED`일 때 category를 null로 두고 `OTHER`는 해석된 기타 Rule에만 사용한다.

- **Pros**: category enum이 순수 비즈니스 taxonomy로 유지됨
- **Cons**: null 허용 조합과 직렬화 규칙을 모든 소비자가 처리해야 함
- **Impact Analysis**:
  - Compatibility: 신규 모델이라 직접 영향 없음
  - Performance: 영향 없음
  - Maintainability: 상태와 null의 결합 규칙이 증가
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 표현 방식을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: `UNKNOWN`을 미해석 category로 사용하고 `OTHER`는 해석된 기타 Rule에만 사용한다.

## Trade-off Question 2

후보의 기본 생성 단위와 결정적 `candidateId` 계약을 선택한다.

### Option A: 조건 Fact별 구조 후보, Rule별 의미 후보 (Recommended)

각 Fact Graph `CONDITION` node에서 하나의 구조 후보를 만들고 `graphId + conditionNodeId`로 ID를 결정한다. 후속 Rule Pack이 의미 후보를 만들 때에는 `predicateCandidateId + ruleId` 조합을 사용해 하나의 조건이 복수 Rule 후보를 가질 수 있게 한다.

- **Pros**: 코드 evidence와 후보가 일대일로 추적되고 복수 의미 해석을 잃지 않음
- **Cons**: 구조 후보와 의미 후보의 관계를 모델에 명시해야 함
- **Impact Analysis**:
  - Compatibility: 기존 method 단위 결과와 병렬 유지 가능
  - Performance: 조건 수에 비례하며 중복 Rule 후보가 늘 수 있음
  - Maintainability: ID와 provenance가 결정적이고 단계 경계가 명확함
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: API method별 후보 집계

한 API method 안의 관련 조건을 하나의 후보로 묶고 method signature를 중심으로 ID를 만든다.

- **Pros**: 후보 수와 초기 모델이 단순함
- **Cons**: 여러 조건과 Rule의 evidence 경계가 흐려지고 부분 해석 상태를 정확히 표현하기 어려움
- **Impact Analysis**:
  - Compatibility: 기존 endpoint 중심 출력과 연결하기 쉬움
  - Performance: 객체 수가 적음
  - Maintainability: 복합 조건 변경 시 ID와 결과 변동 범위가 큼
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 후보 단위와 ID 계약을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: 조건 Fact별 구조 후보와 Rule별 의미 후보를 분리하고 결정적 ID를 사용한다.

## Trade-off Question 3

완전히 해석된 성공 후보에도 `Diagnostic`을 최소 하나 강제할지 선택한다.

### Option A: Evidence는 항상 필수, Diagnostic은 상태에 따라 필수 (Recommended)

모든 후보는 최소 하나의 Evidence를 가진다. `PARTIAL`, `UNSUPPORTED`, `UNRESOLVED`, `NOT_BUSINESS_RULE`, target `UNRESOLVED`인 후보는 최소 하나의 Diagnostic을 가져야 하고 완전히 해석된 성공 후보는 빈 diagnostic 목록을 허용한다.

- **Pros**: 성공을 경고처럼 표현하지 않으면서 실패와 불확실성의 원인을 강제함
- **Cons**: 상태 조합에 따른 invariant 검증이 필요함
- **Impact Analysis**:
  - Compatibility: 신규 모델의 검증 규칙으로만 적용
  - Performance: 성공 후보의 불필요한 진단 객체를 만들지 않음
  - Maintainability: diagnostic의 의미가 문제와 불확실성으로 유지됨
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 모든 후보에 Diagnostic 최소 하나 필수

성공 후보에도 정보 수준의 성공 Diagnostic을 추가한다.

- **Pros**: 모든 후보를 동일한 목록 처리 방식으로 검증 가능
- **Cons**: diagnostic이 정상 상태 로그까지 포함해 의미와 출력량이 커짐
- **Impact Analysis**:
  - Compatibility: 신규 모델이라 기존 출력 영향 없음
  - Performance: 후보마다 객체와 직렬화 크기 증가
  - Maintainability: 성공 정보와 문제 진단을 구별해야 함
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 Evidence와 Diagnostic 필수 조건을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: Evidence는 항상 필수로 하고 Diagnostic은 실패 또는 불확실 상태에서만 필수로 한다.

## 답변 후 진행

세 답변을 검증한 뒤 Unit 02 Functional Design 산출물을 작성한다. Functional Design 승인 후 NFR 필요성을 평가하고, 필요한 경우 NFR Requirements와 NFR Design을 거쳐 Micro-Loop Consensus Planning으로 이동한다.

## 답변 검증 결과

- 세 질문 모두 유효한 Option A 선택으로 확인했다.
- `UNKNOWN`과 `OTHER`를 분리해 미해석 상태를 해석된 기타 분류로 오인하지 않는다.
- 구조 후보와 의미 후보를 분리해 하나의 조건에 대한 복수 Rule 평가와 미매칭 결과를 모두 보존할 수 있다.
- 성공 후보의 빈 diagnostic 목록은 허용하되 실패와 불확실 상태에는 원인 diagnostic을 강제한다.
