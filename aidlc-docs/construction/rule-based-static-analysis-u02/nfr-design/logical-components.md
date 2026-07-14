# Unit 02 후보와 해석 모델 Logical Components

## 1. PredicateCandidateExtractor

### 책임

- Fact Graph condition node 단일 순회
- condition당 정확히 하나의 구조 후보 생성
- extraction status와 predicate type 결정 협력
- condition evidence 구성
- Fact truncation 및 type resolution diagnostic 연결

### 의존성

- `PredicateTypeClassifier`
- `CandidateIdGenerator`
- `EvidenceMapper`
- `CandidateInvariantValidator`

Rule registry나 기존 output model에는 의존하지 않는다.

## 2. PredicateTypeClassifier

### 책임

- Fact node payload와 operand/call edge에서 구조 type 분류
- 비교, null check, membership, lookup chain, composite 구분
- 제한된 사실은 `UNKNOWN`으로 반환

### 제약

- snippet 문자열 패턴에 의존하지 않는다.
- business category나 ruleId를 생성하지 않는다.

## 3. BusinessRuleCandidateFactory

### 책임

- 후속 Rule evaluation을 immutable 의미 후보로 변환
- semantic, category, target status 조합 구성
- normalized constraint와 confidence 연결
- 미매칭 및 비비즈니스 status discriminator 처리

Unit 02에서는 factory 계약과 검증 경계를 제공하며 실제 Rule 매칭 실행은 후속 Unit 책임이다.

## 4. CandidateIdGenerator

### 책임

- predicate와 business rule canonical input 정규화
- 결정적 hash ID 생성
- 미해석 및 비비즈니스 예약 discriminator 관리

### 테스트 계약

- 동일 canonical input은 동일 ID
- condition node 또는 ruleId 변경은 다른 ID
- collection 순서, locale, OS에 독립적
- blank identity 입력 거부

## 5. EvidenceMapper

### 책임

- Fact node source range를 `EvidenceRef`로 변환
- evidence role 부여
- repository 상대 경로 유지
- 제한된 snippet 복사

임의 좌표 fallback이나 source 의미 해석을 수행하지 않는다.

## 6. CandidateDiagnosticCollector

### 책임

- extraction, semantic, target 실패 diagnostic 수집
- stable diagnostic code 사용
- 동일 `code + nodeId + candidateId` 중복 제거
- deterministic ordering 제공

성공 후보를 위해 형식적인 INFO diagnostic을 만들지 않는다.

## 7. CandidateInvariantValidator

### 책임

- semantic status와 ruleId/category 조합
- target status와 targetPath 조합
- evidence non-empty
- 실패·불확실 상태의 diagnostic non-empty
- identity nonblank와 confidence 범위
- null/empty collection 계약

단일 candidate 수준의 검증을 담당한다.

## 8. CandidateResolutionAccumulator

### 책임

- 유효 predicate와 business rule 후보 추가
- duplicate candidate ID 조기 검출
- 조건 단위 실패를 aggregate diagnostic으로 격리
- 안정 정렬된 immutable snapshot 생성

전역 mutable state를 공유하지 않고 graph 처리마다 새 accumulator를 사용한다.

## 9. CandidateResultIntegrityValidator

### 책임

- business rule의 predicate 참조 존재 확인
- evidence node의 input Fact Graph 존재 확인
- predicate와 business rule ID uniqueness
- aggregate diagnostic과 partial failure 일치
- immutable result 경계 검증

참조 무결성 실패 후보는 downstream으로 전달하지 않고 diagnostic으로 전환한다.

## 10. PBT Test Components

### CandidateArbitraries

- valid와 invalid status/category/target 조합
- valid evidence와 source range
- canonical identity input
- 작은 유효 candidate aggregate
- dangling reference가 있는 invalid aggregate

### Property Classes

- `CandidateIdGeneratorProperties`
- `CandidateInvariantProperties`
- `CandidateResolutionResultProperties`

## Component Dependency Rules

| Source | Allowed Target |
|:---|:---|
| Predicate extractor | Fact domain, classifier, ID, evidence, validator |
| Predicate classifier | Fact domain only |
| Business rule factory | candidate domain, Rule evaluation contract, validator |
| Evidence mapper | Fact node와 candidate domain |
| Accumulator | candidate domain, diagnostic collector, integrity validator |
| Validators | immutable domain과 identity index |
| PBT | public/domain contracts와 test arbitraries |

candidate domain은 JavaParser, Spring, legacy graph와 기존 output model에 의존하지 않는다. Unit 01 Fact domain 의존은 extractor와 evidence support 계층에 한정한다.

## Infrastructure Assessment

- cache: 불필요
- queue: 불필요
- database: 불필요
- remote service: 불필요
- deployment change: 없음

모든 component는 현재 로컬 Java 프로세스 안에서 동작한다.
