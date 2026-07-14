# Unit 01 Fact Code Graph Logical Components

## 1. FactCodeGraphBuilder

### 책임

- API root별 graph build orchestration
- traversal context 생성
- method visitor 호출
- API별 result와 diagnostic 조립

### 의존성

- `FactMethodVisitor`
- `MethodTraversalPolicy`
- `FactGraphTraversalBudget`
- `FactGraphIntegrityValidator`

기존 `ValidationEvidenceGraphBuilder`에는 의존하지 않는다.

## 2. FactMethodVisitor

### 책임

- method declaration에서 parameter, local variable, field, call, condition, throw, return 추출
- JavaParser AST 관계를 node와 edge로 직접 변환
- 하위 내부 호출 후보를 traversal context에 전달

### 제약

- snippet 문자열을 구조 판단에 사용하지 않는다.
- 비즈니스 category를 생성하지 않는다.

## 3. FactExpressionVisitor

### 책임

- condition operand tree 추출
- receiver와 argument 관계 추출
- 비교, 논리 부정, 논리 결합 연산 구조 보존
- field 및 Enum constant access 생성

## 4. FactGraphAccumulator

### 책임

- node/edge의 원자적 추가
- duplicate identity 검출
- edge budget 검사
- dangling edge가 생기지 않는 추가 순서 보장
- immutable graph snapshot 생성

## 5. FactNodeIdGenerator

### 책임

- canonical identity input 정규화
- 결정적 SHA-256 기반 ID 생성
- type prefix 생성

### 테스트 계약

- 동일 입력은 동일 ID
- role 또는 range 변경은 다른 ID
- 경로 separator 정규화
- locale과 OS에 독립적

## 6. MethodTraversalPolicy

### 책임

- 내부 본문 방문 여부 결정
- application base package 검사
- project source availability 검사
- framework/external/generated/repository/accessor 제외

### 반환 모델

```java
enum TraversalDecision {
    VISIT_BODY,
    RECORD_CALL_ONLY,
    SKIP_GENERATED
}
```

## 7. FactGraphTraversalContext

### 책임

- 현재 depth
- active path method identity
- analyzed method set
- visited method count
- edge count
- configured budget
- API root identity

API마다 새 context를 사용하며 전역 mutable state를 공유하지 않는다.

## 8. FactGraphDiagnosticCollector

### 책임

- budget truncation 기록
- parse와 type resolution 실패 기록
- node collision과 integrity 실패 기록
- API별 diagnostic 정렬

같은 원인의 반복 diagnostic은 `reason + sourceRange + relatedIdentity` 기준으로 중복 제거할 수 있다.

## 9. FactGraphIntegrityValidator

### 책임

- node ID uniqueness
- edge endpoint 존재
- node type/payload 조합
- resolved type 상태 invariant
- budget stats 상한
- truncation과 diagnostic 일치

검증 실패는 graph를 downstream으로 전달하지 않고 해당 API build diagnostic으로 전환한다.

## 10. PBT Test Components

### FactGraphArbitraries

- valid `SourceRange`
- owner method identity
- semantic role
- small valid graph
- valid traversal budget

generator는 line과 column을 양수 범위로 제한하고 graph 생성 시 node identity와 edge endpoint 제약을 지킨다.

### Property Classes

- `FactNodeIdGeneratorProperties`
- `FactCodeGraphProperties`
- `FactGraphTraversalBudgetProperties`

## Component Dependency Rules

| Source | Allowed Target |
|:---|:---|
| Builder | visitor, policy, accumulator, validator |
| Visitor | domain model, ID generator, accumulator |
| Policy | source/type metadata only |
| Accumulator | domain model, budget, diagnostic collector |
| Validator | immutable graph and stats |
| PBT | public/domain contracts and test arbitraries |

domain model은 JavaParser와 legacy graph에 의존하지 않는다. JavaParser 의존은 builder와 visitor support 계층에 한정한다.

## Infrastructure Assessment

- cache: 불필요
- queue: 불필요
- database: 불필요
- remote service: 불필요
- deployment change: 없음

모든 component는 현재 로컬 Java 프로세스 안에서 동작한다.

