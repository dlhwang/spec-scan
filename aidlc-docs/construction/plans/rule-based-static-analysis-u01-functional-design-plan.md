# Unit 01 Fact Code Graph Functional Design 계획

## Metadata

- **Unit**: `rule-based-static-analysis-u01`
- **Stage**: Functional Design
- **Created**: `2026-07-14T01:59:29.736Z`
- **Status**: Functional Design 생성 완료, 승인 대기
- **Source Design**: `docs/rule-based-static-analysis/01-fact-code-graph.md`

## 단계 계획

- [x] Unit 00 완료 승인 기록
- [x] Unit 01 책임과 비목표 확인
- [x] 기존 graph domain model 확인
- [x] 기존 Application Design 의존성 확인
- [x] 구현 계약에 영향을 주는 모호성 식별
- [x] 트레이드오프 질문 작성
- [x] 사용자 답변 검증
- [x] `business-logic-model.md` 생성
- [x] `business-rules.md` 생성
- [x] `domain-entities.md` 생성
- [x] Functional Design 내용 검증
- [ ] Functional Design 승인

## 확정된 설계 경계

- 기존 `ValidationEvidenceGraph`와 `ValidationEvidenceGraphBuilder`는 Unit 01에서 제거하지 않는다.
- 신규 Fact Code Graph는 병렬 모델과 builder로 도입한다.
- Fact Graph에는 비즈니스 category와 `BUSINESS_RULE` 의미를 저장하지 않는다.
- 조건식 operand tree, 분기 극성, 호출 receiver/arguments, source range, 타입 해석 상태를 보존한다.
- AST snippet을 다시 문자열 파싱해 구조를 복원하지 않는다.
- production Rule Matching과 output migration은 후속 Unit 범위다.

## Trade-off Question 1

Fact node의 타입별 payload를 어떤 Java 모델로 표현할지 선택한다.

### Option A: 공통 record와 타입별 payload record 조합 (Recommended)

`FactNode`는 공통 identity/source/type-resolution 필드를 가지고, `FactNodePayload` sealed interface 아래에 조건·호출·필드 등 payload record를 둔다.

- **Pros**: 공통 그래프 저장과 타입별 필수 필드 검증을 함께 제공하며 pattern matching이 명확함
- **Cons**: 클래스 파일 수가 늘고 직렬화 설정을 고려해야 함
- **Impact Analysis**:
  - Compatibility: 기존 graph와 병렬 도입하므로 호환 유지
  - Performance: 작은 payload 객체 비용이 있으나 PoC 범위에서는 경미
  - Maintainability: 타입 추가와 필수 필드 관리가 명확해짐
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 단일 record와 nullable 필드

모든 노드 정보를 하나의 `FactNode` record에 nullable 필드로 둔다.

- **Pros**: 파일 수와 초기 구현량이 적음
- **Cons**: 잘못된 필드 조합을 컴파일 시 방지하지 못하고 nullable 계약이 커짐
- **Impact Analysis**:
  - Compatibility: 병렬 모델이라 직접 영향 없음
  - Performance: 객체 수가 적으나 사용하지 않는 필드가 많음
  - Maintainability: 노드 종류가 늘수록 저하
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 모델을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: 공통 record와 타입별 payload record 조합을 사용한다.

## Trade-off Question 2

동일 소스에서 재실행해도 유지되는 node ID의 기본 구성을 선택한다.

### Option A: 소스 좌표와 AST role 기반 구조 ID (Recommended)

`relativePath + ownerSignature + AST kind + start/end position + semantic role`을 정규화한 뒤 읽을 수 있는 prefix와 hash를 결합한다.

- **Pros**: 동일 입력에서 결정적이며 같은 줄의 복수 operand도 구분 가능
- **Cons**: 소스 줄 이동 시 ID가 바뀜
- **Impact Analysis**:
  - Compatibility: 기존 graph ID와 별도 namespace 사용
  - Performance: 생성 시 소규모 hash 비용
  - Maintainability: 충돌 규칙과 정규화 함수를 한곳에서 관리 가능
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: AST 순회 순번 기반 ID

파일별 또는 메서드별 방문 순번으로 ID를 만든다.

- **Pros**: 구현이 단순하고 짧은 ID 제공
- **Cons**: visitor 순서나 AST 구조가 조금만 바뀌어도 관련 없는 ID까지 변함
- **Impact Analysis**:
  - Compatibility: 기존 graph와 별도지만 결과 재현성이 visitor 구현에 의존
  - Performance: 가장 저렴
  - Maintainability: 추출기 변경 시 snapshot 변동이 큼
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 ID 정책을 `[Rationale]`에 기술한다.

[Answer]: A
[Rationale]: 소스 좌표와 AST role 기반 구조 ID를 사용한다.

## Trade-off Question 3

Unit 01에서 Fact Graph가 탐색할 메서드 호출 깊이의 기본값을 선택한다.

### Option A: 기본 3, 설정으로 조정 가능 (Recommended)

API method를 깊이 0으로 보고 직접 서비스와 제한된 domain/helper 호출까지 최대 깊이 3으로 탐색한다.

- **Pros**: 일반적인 controller-service-domain guard를 포함하면서 순환과 그래프 폭증을 통제
- **Cons**: 더 깊은 helper chain은 부분 그래프로 남음
- **Impact Analysis**:
  - Compatibility: 기존 builder와 병렬이라 영향 없음
  - Performance: bounded traversal로 예측 가능
  - Maintainability: 설정과 diagnostic 계약 필요
  - Implementation Cost: 중간
  - Risk: 낮음

### Option B: 현재 legacy builder budget을 그대로 공유

기존 `ValidationEvidenceGraphBuilder`의 탐색 budget 정책을 신규 builder에서도 재사용한다.

- **Pros**: 기존 분석 도달 범위와 결과 비교가 쉬움
- **Cons**: legacy 내부 정책과 신규 Fact Graph가 결합되고 범용 계약이 불명확해짐
- **Impact Analysis**:
  - Compatibility: legacy 비교에는 유리
  - Performance: 기존 정책에 종속
  - Maintainability: 신규 모델 독립성 저하
  - Implementation Cost: 낮음
  - Risk: 중간

### Option X: Other

다른 깊이 또는 정책을 `[Rationale]`에 기술한다.

[Answer]: X
[Rationale]: 독립 복합 Traversal Budget을 사용한다. 기본값은 max-depth 5, API별 최대 방문 메서드 100, 최대 edge 300이다. 프로젝트 내부의 도달 가능한 service/domain/validator/policy/helper 중 조건식·반환·예외 흐름과 연결된 메서드만 내부 탐색한다. 외부/JDK/Spring/generated/repository 구현과 단순 접근자는 호출 사실만 기록하며 budget 중단 시 diagnostic을 남긴다.

## PBT 적용 계획

- 결정적 node ID는 동일 입력 반복 생성에 대한 idempotency property 후보로 지정한다.
- operand tree와 edge 참조 무결성은 생성된 작은 조건식 조합에 대한 invariant property 후보로 지정한다.
- jqwik 추가 여부는 Functional Design 답변 확정 후 NFR 필요성 평가에서 결정한다.

## 답변 검증 결과

- 세 질문 모두 유효한 선택으로 확인했다.
- Option X는 기본값, 포함·제외 정책, 순환 방지, diagnostic 계약을 모두 포함하므로 추가 모호성이 없다.
- 상위 설계와 충돌하지 않으며 legacy builder와 독립적인 신규 builder 원칙을 강화한다.

