# Unit 02 후보와 해석 모델 비즈니스 로직 모델

## 목적

Unit 01의 `FactCodeGraph`에서 관찰된 조건을 구조 후보로 만들고, 후속 Rule Pack이 생성할 의미 후보가 구조 추출 상태, 의미 해석 상태, target 해석 상태를 독립적으로 표현할 수 있는 계약을 정의한다.

## 입력

- `FactCodeGraph`
- 조건 node와 연결된 operand, call, input/domain origin, failure outcome 사실
- Fact Graph diagnostic과 type resolution 상태
- 후속 Unit에서 제공할 Rule 평가 결과

## 출력

- 조건 Fact별 `PredicateCandidate`
- Rule 평가별 `BusinessRuleCandidate`
- 정규화할 수 있는 경우의 `NormalizedConstraint`
- 최소 하나 이상의 `EvidenceRef`
- 실패 또는 불확실성 원인을 설명하는 `CandidateDiagnostic`

## 처리 흐름

### 1. 구조 후보 생성

각 `CONDITION` node를 하나의 구조 후보로 취급한다. `predicateCandidateId`는 `graphId + conditionNodeId`의 canonical representation에서 결정적으로 만든다.

- 조건 구조를 확인했으면 `EXTRACTED`다.
- 일부 operand 또는 type을 확인하지 못했지만 조건 자체를 보존했으면 `PARTIAL`이다.
- 지원 범위 밖 AST로 조건 구조를 만들 수 없으면 `UNSUPPORTED`다.
- 구조 후보는 비즈니스 category, ruleId 또는 targetPath를 추측하지 않는다.

### 2. PredicateType 분류

AST와 Fact edge에서 직접 관찰한 구조만 분류한다.

- 비교 연산: `COMPARISON`
- boolean 반환 호출: `BOOLEAN_CALL`
- null 비교: `NULL_CHECK`
- contains 또는 membership 구조: `COLLECTION_MEMBERSHIP`
- 연결된 조회 호출: `LOOKUP_CHAIN`
- enum constant 비교: `ENUM_COMPARISON`
- 논리 결합 조건: `COMPOSITE`
- 제한된 사실로 특정할 수 없음: `UNKNOWN`

`LOOKUP_CHAIN`을 존재 검증으로 해석하는 작업은 여기서 수행하지 않는다.

### 3. 의미 후보 생성

후속 Rule 평가 하나마다 하나의 `BusinessRuleCandidate`를 생성한다. ID는 `predicateCandidateId + ruleId`를 canonical input으로 사용한다.

Rule이 매칭되지 않은 결과도 누락하지 않는다. 이때 `ruleId`는 null, category는 `UNKNOWN`, semantic status는 `UNRESOLVED`이며 ID에는 예약된 `UNRESOLVED` discriminator를 사용한다.

명시적인 비비즈니스 판정은 `NOT_BUSINESS_RULE` discriminator를 사용한다. 단순 미매칭은 이 상태로 바꾸지 않는다.

### 4. 세 상태 축 독립 결정

- `ExtractionStatus`는 코드 구조 확보 수준만 나타낸다.
- `SemanticStatus`는 Rule 의미 해석 결과만 나타낸다.
- `TargetResolutionStatus`는 요청 또는 domain target 연결 결과만 나타낸다.

따라서 의미 Rule은 해석됐지만 target을 찾지 못한 후보는 `RESOLVED + UNRESOLVED target`으로 유지한다. 한 축의 실패를 다른 축의 성공 또는 실패로 덮지 않는다.

### 5. Constraint 정규화

소스 사실로 확인할 수 있을 때만 `NormalizedConstraint`를 생성한다.

- 입력과 literal 비교: `INPUT_LITERAL`
- 입력과 domain 값 비교: `INPUT_TO_DOMAIN`
- 현재 코드 사실만으로 값이 정해지지 않음: `RUNTIME_DEPENDENT`
- 값 제약 없이 제어 흐름만 확인됨: `CONTROL_FLOW_ONLY`

정규화 자체가 불가능하면 `constraint`는 null이다. target이 미해석이면 `targetPath`는 null이고, expected value를 관찰하지 못했으면 `expectedValues`는 빈 목록이다. null이나 빈 목록을 추측값으로 채우지 않는다.

### 6. Evidence와 Diagnostic 구성

모든 후보는 최소 하나의 `EvidenceRef`를 가진다. Evidence는 Fact node ID와 source range를 참조하며 가능한 경우 snippet을 포함한다.

다음 상태에는 최소 하나의 diagnostic이 필요하다.

- extraction `PARTIAL` 또는 `UNSUPPORTED`
- semantic `UNRESOLVED` 또는 `NOT_BUSINESS_RULE`
- target `UNRESOLVED`

완전히 해석된 성공 후보는 빈 diagnostic 목록을 가질 수 있다.

### 7. 불변식 검증과 immutable snapshot

- candidate ID는 결과 집합 안에서 유일해야 한다.
- 모든 의미 후보의 `predicateCandidateId`는 존재하는 구조 후보를 참조해야 한다.
- `RESOLVED` 의미 후보는 nonblank `ruleId`를 가져야 한다.
- category `OTHER`는 `RESOLVED` 의미 후보에서만 허용한다.
- category `UNKNOWN`은 미해석 또는 비비즈니스 결과에 사용한다.
- collection은 방어적으로 복사하고 null collection을 허용하지 않는다.

## 부분 성공 및 오류 처리

- 하나의 후보 생성 실패가 다른 조건 후보 생성을 중단시키지 않는다.
- Fact Graph가 truncated여도 관찰된 조건은 후보화하고 truncation diagnostic을 연결한다.
- type resolution 실패는 가능한 구조 후보를 `PARTIAL`로 보존한다.
- 지원하지 않는 구조를 조용히 제외하지 않고 `UNSUPPORTED` 후보로 남긴다.

## 비목표

- Rule Pack과 구체 Rule 구현
- target path 추론 알고리즘
- 기존 `ValidationCandidate`, `ApiCondition`, `NormalizedResult` 교체
- API 출력 직렬화 스키마 마이그레이션
