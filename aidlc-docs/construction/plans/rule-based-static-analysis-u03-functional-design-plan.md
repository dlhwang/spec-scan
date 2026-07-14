# Unit 03 Graph Rule Engine Functional Design 계획

## Metadata

- **Unit**: `rule-based-static-analysis-u03`
- **Stage**: Functional Design
- **Created**: `2026-07-14T13:25:39+09:00`
- **Status**: Functional Design 생성 완료, 승인 대기
- **Source Design**: `docs/rule-based-static-analysis/03-graph-rule-engine.md`

## 단계 계획

- [x] Unit 02 완료 승인 기록
- [x] Unit 03 책임과 비목표 확인
- [x] Unit 01 Fact Graph와 Unit 02 candidate 계약 확인
- [x] 기존 Rule 관련 직접 의존성 확인
- [x] 구현 계약에 영향을 주는 모호성 식별
- [x] 트레이드오프 질문 작성
- [x] 사용자 답변 검증
- [x] Business Logic Model 작성
- [x] Business Rules 작성
- [x] Domain Entities 작성
- [x] Functional Design 내용 검증
- [ ] Functional Design 승인

## 확정된 설계 경계

- candidate detector와 의미 Rule 실행은 분리한다.
- 엔진은 Rule 구현의 중앙 `if-else` 또는 `switch`를 갖지 않고 등록된 `GraphRule` SPI만 실행한다.
- Rule은 하나의 predicate에서 0개 이상의 의미 후보를 반환할 수 있다.
- 등록 Rule이 없거나 모두 미매칭인 predicate는 `UNRESOLVED`로 보존한다.
- simple method name 하나만으로 business meaning을 확정하지 않는다.
- Rule 예외는 해당 Rule과 predicate에 격리하고 다른 Rule 및 predicate 실행을 계속한다.
- 구체 Rule Pack과 output migration은 후속 Unit 범위다.

## Trade-off Question 1

기본 `ValidationCandidateDetector`가 실패 outcome과 연결된 조건을 판별하는 범위를 선택한다.

### Option A: 명시적 throw 기본 + 확장 가능한 failure outcome policy (Recommended)

기본 detector는 Fact Graph에서 조건 branch와 직접 연결된 `THROW`를 실패 outcome으로 인정한다. `return false`, error result, custom exception factory 같은 반환 패턴은 `FailureOutcomePolicy` SPI가 명시적으로 판정할 때만 포함한다.

- **Pros**: 일반 return을 검증 실패로 오인하지 않고 프로젝트·프레임워크별 실패 표현을 engine 수정 없이 확장 가능
- **Cons**: policy가 없는 custom return 기반 validation은 초기에는 후보에서 빠질 수 있음
- **Impact Analysis**:
  - Compatibility: legacy detector와 병렬 실행 가능
  - Performance: branch와 outcome edge 중심의 제한된 탐색
  - Maintainability: failure 판정 책임이 독립 policy로 명확함
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: throw 또는 return을 제어하는 모든 조건 포함

조건 branch가 `THROW`나 `RETURN`과 연결되면 반환 의미와 관계없이 후보로 생성한다.

- **Pros**: return 기반 custom validation의 recall이 높음
- **Cons**: 정상적인 조기 반환과 값 계산 분기까지 후보가 되어 false positive가 증가함
- **Impact Analysis**:
  - Compatibility: 기존보다 후보 수가 크게 늘 수 있음
  - Performance: Rule 평가 대상 증가
  - Maintainability: 후속 Rule이 비검증 후보를 반복 제외해야 함
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 failure outcome 판정 정책을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: 명시적 throw를 기본으로 하고 return 기반 실패는 확장 가능한 FailureOutcomePolicy가 판정한다.

## Trade-off Question 2

Rule 실행 결과와 통계를 어떤 aggregate로 반환할지 선택한다.

### Option A: Candidate 결과와 실행 report를 조합한 engine result (Recommended)

`GraphRuleEngineResult`가 기존 `CandidateResolutionResult`와 `RuleExecutionReport`를 함께 가진다. report에는 등록·실행·매칭·실패 Rule 수, predicate 수, 중복 제거 수와 Rule별 failure diagnostic을 포함한다.

- **Pros**: candidate 의미와 실행 관찰 정보를 분리하면서 한 번의 실행 결과로 함께 전달 가능
- **Cons**: engine 전용 결과 record가 하나 추가됨
- **Impact Analysis**:
  - Compatibility: Unit 02 aggregate를 수정하지 않고 조합함
  - Performance: 작은 counter와 diagnostic 집계 비용
  - Maintainability: output 후보와 engine 운영 정보의 책임이 명확함
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: CandidateResolutionResult만 반환

Rule 실패와 실행 정보도 candidate aggregate의 diagnostic 목록에 넣고 별도 통계를 제공하지 않는다.

- **Pros**: public 결과 타입 수가 적음
- **Cons**: Rule이 실행되지 않은 상황과 미매칭을 구분하기 어렵고 실행 품질 측정이 제한됨
- **Impact Analysis**:
  - Compatibility: 기존 Unit 02 결과만 사용
  - Performance: 가장 단순
  - Maintainability: candidate와 execution concern이 섞임
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 결과 및 통계 계약을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: CandidateResolutionResult와 RuleExecutionReport를 조합한 GraphRuleEngineResult를 사용한다.

## Trade-off Question 3

Rule Pack이 명시한 precedence가 복수 매칭 결과에 미치는 동작을 선택한다.

### Option A: 후보 보존 + precedence diagnostic 표시 (Recommended)

pack은 ruleId의 명시적 tier 또는 partial order를 선언한다. exact duplicate만 제거하고 서로 다른 Rule 후보는 모두 보존한다. precedence가 있으면 낮은 후보에 `LOWER_PRECEDENCE_MATCH` diagnostic을, category 충돌에는 ambiguity diagnostic을 추가한다. 등록 순서나 confidence로 제거하지 않는다.

- **Pros**: 분석 근거를 잃지 않으면서 consumer가 선호 후보를 식별할 수 있음
- **Cons**: downstream이 복수 후보와 precedence diagnostic을 처리해야 함
- **Impact Analysis**:
  - Compatibility: 신규 candidate 결과에 diagnostic으로 추가
  - Performance: predicate별 소규모 중복 및 precedence 비교
  - Maintainability: Rule 순서와 의미 우선순위를 분리
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 높은 precedence 후보만 유지

같은 predicate에서 precedence가 높은 Rule이 매칭되면 낮은 후보를 결과에서 제거한다.

- **Pros**: downstream 결과가 간단함
- **Cons**: 유효한 복수 의미와 evidence를 잃고 precedence 설정 오류가 조용한 누락을 만듦
- **Impact Analysis**:
  - Compatibility: 결과 수가 pack 설정에 따라 달라짐
  - Performance: 결과 객체 수 감소
  - Maintainability: precedence 변경이 분석 의미를 직접 삭제함
  - Implementation Cost: 낮음
  - Risk: 높음

### Option X: Other

다른 precedence 동작을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: exact duplicate만 제거하고 precedence와 ambiguity를 diagnostic으로 표시하며 복수 후보를 보존한다.

## 답변 후 진행

세 답변을 검증한 뒤 Unit 03 Functional Design 산출물을 작성한다. Functional Design 승인 후 NFR 필요성을 평가하고 Micro-Loop Consensus Planning으로 이동한다.

## 답변 검증 결과

- 세 질문 모두 유효한 Option A 선택으로 확인했다.
- 기본 detector는 명시적 throw만 보수적으로 인식하고 return 기반 failure는 독립 policy로 확장한다.
- Unit 02 candidate aggregate를 변경하지 않고 engine 실행 report를 조합한다.
- precedence는 결과 삭제가 아닌 선호도와 ambiguity의 명시적 근거로만 사용한다.
