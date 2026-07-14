# Unit 01 Fact Code Graph NFR Design Patterns

## 1. Bounded Traversal Pattern

### 목적

깊이만 제한했을 때 넓은 호출 그래프가 폭증하는 문제와, 방문 개수만 제한했을 때 깊은 helper chain이 분석 시간을 독점하는 문제를 함께 통제한다.

### 적용

`FactGraphTraversalBudget`의 세 차원을 독립적으로 검사한다.

- depth: 내부 메서드 본문에 진입하기 직전 검사
- visited methods: method identity를 visited set에 추가하기 직전 검사
- edges: edge를 graph accumulator에 추가하기 직전 검사

### 경계 동작

- limit과 동일한 값까지 허용하고 다음 추가 시도를 차단한다.
- edge limit으로 필요한 node만 생성되고 edge가 누락되는 상태를 피하기 위해 node와 edge 추가를 하나의 accumulator operation으로 처리한다.
- 차단된 operation은 diagnostic observed count에 반영하되 collection size는 limit을 넘지 않는다.

## 2. Partial Result with Diagnostics Pattern

### 목적

지원 한계와 Rule 미지원 또는 분석 결함을 구분한다.

### 적용

- API마다 독립적인 accumulator와 diagnostic collector를 사용한다.
- budget, parse, type resolution 실패를 typed diagnostic code로 기록한다.
- root API를 만들 수 없는 경우 graph 대신 실패 diagnostic을 반환한다.
- 일부 node의 type resolution 실패는 graph 생성을 중단하지 않는다.

## 3. Deterministic Identity Pattern

### 목적

동일 source 분석 결과를 안정적으로 비교하고 Evidence reference를 유지한다.

### 적용

`FactNodeIdGenerator`가 canonical input을 UTF-8 byte sequence로 정규화한다.

canonical input 순서는 다음으로 고정한다.

1. node type
2. normalized relative path
3. owner method identity
4. AST kind
5. start line/column
6. end line/column
7. semantic role

ID는 readable type prefix와 SHA-256 hash의 충분한 길이 prefix를 결합한다. 같은 graph에서 collision이 발생하면 suffix를 붙이지 않고 `NODE_ID_COLLISION`으로 실패시킨다.

## 4. Immutable Snapshot Pattern

### 목적

builder 완료 후 downstream Rule이 graph를 변형해 분석 결과를 오염시키지 못하게 한다.

### 적용

- record constructor에서 `List.copyOf`를 사용한다.
- payload도 immutable record만 허용한다.
- mutable JavaParser AST node를 domain model에 저장하지 않는다.
- snippet은 제한된 immutable string으로 저장한다.

## 5. Traversal Scope Policy Pattern

### 목적

AST extraction과 방문 정책을 분리해 framework 및 프로젝트별 범위 확장을 가능하게 한다.

### 적용

`MethodTraversalPolicy`가 다음 결과 중 하나를 반환한다.

- `VISIT_BODY`
- `RECORD_CALL_ONLY`
- `SKIP_GENERATED`

판단 입력은 source availability, qualified owner, application base package, method role, generated marker다. Rule 분류나 도메인 이름은 입력에 포함하지 않는다.

## 6. Cycle Guard Pattern

### 목적

직접 재귀와 상호 재귀에서 무한 방문을 방지한다.

### 적용

- active path set: 현재 call stack 내 cycle 판정
- analyzed method set: API scope 내 본문 중복 분석 방지
- call node와 `CALLS` edge는 유지
- cycle로 본문을 방문하지 않은 사실을 debug diagnostic 또는 traversal metadata로 기록할 수 있다.

## 7. Static-Analysis Safety Pattern

- source parser와 symbol solver만 사용한다.
- target build tool, annotation processor, class loading, reflection 실행을 금지한다.
- 절대 workspace path를 graph domain에 저장하지 않는다.
- external dependency source가 없으면 signature resolution 결과만 사용하고 body를 얻기 위한 network fetch를 수행하지 않는다.

## 8. Complementary PBT Pattern

example-based test는 구체적인 AST와 edge 관계를 문서화한다. jqwik property는 넓은 source coordinate와 semantic role 조합에서 identity와 invariant를 검증한다.

PBT 범위:

- `FactNodeIdGeneratorProperties`
- `FactCodeGraphProperties`
- `FactGraphTraversalBudgetProperties`

PBT는 parser 전체를 무작위 source 문자열로 fuzzing하는 용도가 아니다. Unit 01에서는 순수 domain 및 identity 함수를 대상으로 한다.

## NFR Design Compliance

- 성능: bounded traversal과 scope policy로 반영
- 신뢰성: partial result와 diagnostic으로 반영
- 결정성: canonical identity로 반영
- 보안: static-analysis safety로 반영
- 테스트: example/PBT 상호 보완으로 반영

