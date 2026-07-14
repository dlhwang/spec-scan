# Unit 03 Graph Rule Engine 비즈니스 로직 모델

## 목적

Fact Code Graph에서 실패 outcome과 연결된 조건을 구조 후보로 탐지하고, 독립 등록된 `GraphRule`을 실행해 0개 이상의 의미 후보를 생성한다. 엔진은 Rule 계층, 등록 순서 또는 특정 프로젝트 이름에 결합하지 않는다.

## 입력

- Unit 01 `FactCodeGraph`
- API 또는 method `MethodScope`
- 활성화된 `RulePack` 목록
- `FailureOutcomePolicy` 목록
- Unit 02 candidate ID, factory, invariant 및 aggregate 계약

## 출력

`GraphRuleEngineResult`는 다음을 조합한다.

- `CandidateResolutionResult`: predicate와 business rule 후보 및 candidate diagnostic
- `RuleExecutionReport`: 등록·실행·매칭·실패 Rule 수, 중복 제거 수, predicate 수, Rule 실행 diagnostic

## 처리 흐름

### 1. Method scope 구성

- API root 또는 요청된 method node를 scope root로 사용한다.
- scope에 속한 condition과 직접 연결된 branch outcome만 detector 입력으로 제공한다.
- 다른 method의 동일 source range나 이름을 근거로 scope에 포함하지 않는다.

### 2. 실패 outcome 탐지

기본 detector는 `THEN_OUTCOME` 또는 `ELSE_OUTCOME` edge로 condition과 직접 연결된 `THROW` node를 실패 outcome으로 인정한다.

`RETURN`은 기본적으로 실패가 아니다. 등록된 `FailureOutcomePolicy`가 Fact 구조와 resolved type/signature를 근거로 명시적으로 인정할 때만 실패 outcome으로 취급한다. policy가 이름 heuristic만 사용하면 결과를 `PARTIAL`로 표시하고 diagnostic을 남긴다.

### 3. 구조 후보 생성

- 실패 outcome 하나 이상과 연결된 condition마다 하나의 `PredicateCandidate`를 생성한다.
- 같은 condition이 then과 else의 여러 실패 outcome에 연결돼도 구조 후보는 하나다.
- Evidence에는 predicate와 failure outcome을 모두 포함한다.
- ID는 Unit 02 `CandidateIdGenerator` 계약을 재사용한다.

### 4. Rule Pack 선택

- engine은 생성 시 주입된 pack만 실행한다.
- pack 활성화 여부와 layer는 실행 계획과 report에 기록한다.
- engine core는 `JAVA_LANGUAGE`, `JDK_IDIOM`, `SPRING`, `SPRING_DATA_JPA`, `PROJECT_EXTENSION`을 동일 SPI로 다룬다.
- Rule 추가를 위해 engine의 중앙 분기문을 수정하지 않는다.

### 5. Rule 실행

각 predicate에 활성 Rule을 독립 실행한다.

- Rule은 빈 목록 또는 복수 `BusinessRuleCandidate`를 반환한다.
- null 반환은 contract violation으로 diagnostic 처리한다.
- Rule 예외는 해당 predicate와 Rule에 격리한다.
- 한 Rule 실패 후에도 나머지 Rule과 predicate 실행을 계속한다.
- 반환 후보는 Unit 02 invariant validator를 통과해야 한다.

### 6. 타입 해석 fallback 기록

Rule은 다음 근거 순서를 따른다.

1. fully qualified receiver type과 resolved method signature
2. 상속 또는 interface assignability
3. AST 구조, argument 수, branch outcome
4. 제한된 이름 heuristic

3 또는 4를 사용한 후보는 `ExtractionStatus.PARTIAL`이며 최소 하나의 fallback diagnostic을 가진다. simple method name 하나만으로 semantic `RESOLVED`를 반환할 수 없다.

### 7. 중복 제거

동일 predicate에서 `ruleId + evidence fingerprint`가 같은 후보만 exact duplicate로 제거한다. fingerprint는 정렬된 `nodeId + EvidenceRole` 집합으로 만들며 snippet, diagnostic message, collection 입력 순서에 의존하지 않는다.

### 8. 충돌과 precedence 처리

- 서로 다른 category가 같은 predicate에 매칭되면 모든 후보를 보존하고 `AMBIGUOUS_RULE_MATCH` diagnostic을 추가한다.
- pack의 precedence tier가 다르면 낮은 tier 후보에 `LOWER_PRECEDENCE_MATCH` diagnostic을 추가한다.
- precedence는 후보를 제거하거나 semantic status를 변경하지 않는다.
- 등록 순서와 confidence는 승자 선택에 사용하지 않는다.

### 9. 미매칭 보존

유효 Rule 후보가 0개인 predicate에는 Unit 02 계약에 맞는 하나의 `UNRESOLVED` 의미 후보를 생성한다.

- `ruleId = null`
- `category = UNKNOWN`
- `semanticStatus = UNRESOLVED`
- `targetStatus = UNRESOLVED` 또는 구조상 적용 불가가 명확하면 `NOT_APPLICABLE`
- `RULE_UNRESOLVED` 또는 모든 Rule 실패를 설명하는 diagnostic

### 10. 결과 및 report 확정

- candidate aggregate의 참조 무결성을 검증한다.
- candidate와 diagnostic을 결정적으로 정렬한다.
- report counter가 실제 실행 결과와 일치하는지 검증한다.
- candidate 결과와 report를 immutable snapshot으로 반환한다.

## 오류 및 부분 성공

- detector policy 예외는 해당 condition 또는 policy diagnostic으로 격리한다.
- Rule 하나의 예외 또는 invalid candidate는 전체 실행 실패가 아니다.
- 모든 Rule이 실패한 predicate도 `UNRESOLVED`로 보존한다.
- graph 자체의 참조 무결성이 깨졌으면 engine 실행 전에 거부한다.

## 비목표

- 구체 Java/JDK/Spring/JPA Rule 구현
- 외부 JSON/YAML Rule DSL
- 동적 plugin loading
- confidence 기반 자동 승자 선택
- 기존 output pipeline 연결
