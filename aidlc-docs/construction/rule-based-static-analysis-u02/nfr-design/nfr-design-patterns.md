# Unit 02 후보와 해석 모델 NFR Design Patterns

## 1. Two-Stage Candidate Pattern

### 목적

관찰 가능한 조건 구조와 Rule 의미 해석을 분리해 Rule 미매칭, 복수 매칭, target 미해석을 손실 없이 표현한다.

### 적용

- `PredicateCandidateExtractor`는 Fact condition당 하나의 구조 후보만 생성한다.
- `BusinessRuleCandidateFactory`는 Rule 평가당 하나의 의미 후보를 생성한다.
- 구조 후보에는 ruleId, category, target을 넣지 않는다.
- 의미 후보는 `predicateCandidateId`로 구조 후보를 참조한다.
- 미매칭과 명시적 비비즈니스 판정은 각각 예약된 ID discriminator를 사용한다.

## 2. Deterministic Candidate Identity Pattern

### 목적

동일 Fact와 Rule 평가의 재실행 결과를 안정적으로 비교하고 evidence 및 후속 output 참조를 유지한다.

### 적용

구조 후보 canonical input 순서는 다음으로 고정한다.

1. candidate kind `PREDICATE`
2. graph ID
3. condition node ID

의미 후보 canonical input 순서는 다음으로 고정한다.

1. candidate kind `BUSINESS_RULE`
2. predicate candidate ID
3. normalized rule ID 또는 status discriminator

canonical input은 UTF-8로 hash하며 readable prefix를 결합한다. collision이나 duplicate ID에는 suffix를 붙이지 않고 diagnostic과 검증 실패로 처리한다.

## 3. Validated Construction Pattern

### 목적

잘못된 status, category, ruleId, target, diagnostic 조합이 downstream으로 전파되지 않게 한다.

### 적용

- record compact constructor는 null collection, blank identity, confidence 범위 같은 지역 불변식을 검증한다.
- 여러 객체가 필요한 참조 무결성은 `CandidateResultIntegrityValidator`가 aggregate 완성 시 검증한다.
- 상태 조합 규칙은 `CandidateInvariantValidator`의 단일 정책으로 유지해 factory와 test가 같은 계약을 사용한다.
- 검증 실패를 필드 자동 보정으로 숨기지 않는다.

## 4. Partial Result with Typed Diagnostics Pattern

### 목적

한 조건의 추출 또는 의미 해석 실패가 다른 유효 후보를 제거하지 않으면서, 누락 원인을 명시적으로 공개한다.

### 적용

- 조건마다 독립적인 extraction outcome을 만든다.
- 유효 후보는 accumulator에 보존한다.
- 생성 불가능하거나 참조가 깨진 후보는 aggregate diagnostic으로 전환한다.
- `PARTIAL`, `UNSUPPORTED`, `UNRESOLVED`, `NOT_BUSINESS_RULE`, target `UNRESOLVED`에는 typed diagnostic을 강제한다.
- Fact Graph truncation diagnostic은 관련 predicate evidence를 통해 후보 diagnostic으로 연결한다.

## 5. Evidence Reference Integrity Pattern

### 목적

후보가 소스 근거로 역추적 가능하고 다른 graph나 존재하지 않는 node를 근거로 삼지 않게 한다.

### 적용

- `EvidenceMapper`는 Fact node ID와 source range를 직접 복사한다.
- `EvidenceRef`는 구조를 복원하기 위한 AST 대체물이 아니다.
- aggregate 검증 시 input Fact node index에 evidence node ID가 존재하는지 검사한다.
- source range가 없는 node를 다른 node 좌표로 보정하지 않는다.
- snippet은 설정된 최대 길이로 제한하고 전체 소스를 저장하지 않는다.

## 6. Immutable Snapshot Pattern

### 목적

후속 Rule 또는 adapter가 candidate와 constraint 결과를 변경해 분석 결과를 오염시키지 못하게 한다.

### 적용

- 모든 collection은 `List.copyOf`로 방어적 복사한다.
- `NormalizedConstraint.expectedValues`도 독립 복사한다.
- mutable AST node나 resolver 객체를 domain record에 저장하지 않는다.
- aggregate는 검증이 끝난 immutable candidate만 노출한다.

## 7. Linear Indexing Pattern

### 목적

candidate와 evidence 참조 검사에서 반복 중첩 검색을 피하고 Fact와 Rule 평가 수에 비례하는 처리를 유지한다.

### 적용

- condition node는 node type별 단일 순회로 수집한다.
- predicate ID 및 Fact node ID는 `Map` 또는 `Set` index로 검증한다.
- duplicate 검출은 insertion 시 수행한다.
- 결과 정렬은 aggregate 경계에서 candidate ID 기준 한 번만 수행한다.

복잡도 목표는 후보 생성과 검증 `O(F + R)`, 안정 정렬이 필요할 때 `O(C log C)`다. `F`는 관련 Fact 수, `R`은 Rule 평가 수, `C`는 결과 후보 수다.

## 8. Explicit Unknown Serialization Boundary Pattern

### 목적

core의 null, 빈 목록, `UNKNOWN` 의미를 output adapter가 추측값이나 `GENERIC`으로 바꾸지 않게 한다.

### 적용

- core domain은 serialization annotation에 의존하지 않는다.
- 후속 adapter는 null/empty/UNKNOWN mapping을 명시적으로 정의한다.
- core 테스트는 JSON 형태보다 도메인 의미를 검증한다.
- serializer 통합 테스트는 output migration Unit에서 추가한다.

## 9. Static-Analysis Safety Pattern

- Fact Graph와 immutable metadata만 입력으로 사용한다.
- 대상 class loading, reflection, build tool, annotation processor, script 실행을 금지한다.
- repository 기준 상대 경로만 evidence에 저장한다.
- 진단과 evidence에 전체 source dump를 포함하지 않는다.

## 10. Complementary PBT Pattern

example test는 대표 상태 조합과 diagnostic code를 문서화한다. jqwik property는 넓은 status/category/target 조합에서 생성 규칙과 참조 invariant를 검증한다.

PBT 범위:

- `CandidateIdGeneratorProperties`
- `CandidateInvariantProperties`
- `CandidateResolutionResultProperties`

generator는 무작위 문자열을 무제한 생성하지 않고 유효 identity, source range, 상태 조합 domain을 제공한다. invalid 조합 generator는 거부 계약 검증에 별도로 사용한다.

## NFR Design Compliance

- 성능: linear indexing과 단일 안정 정렬로 반영
- 신뢰성: validated construction과 partial result로 반영
- 결정성: canonical candidate identity로 반영
- 추적성: evidence reference integrity로 반영
- 안전성: static-analysis-only와 source 최소화로 반영
- 테스트: example/PBT 상호 보완으로 반영
