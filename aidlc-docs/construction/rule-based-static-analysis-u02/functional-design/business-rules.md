# Unit 02 후보와 해석 모델 규칙

## 단계 분리 규칙

### CMR-01 상태 축 독립성

구조 추출, 의미 해석, target 해석 상태를 서로 독립적으로 저장한다. 한 상태의 실패를 다른 상태값으로 대신 표현하지 않는다.

### CMR-02 Rule 미매칭 보존

구조를 추출했으나 Rule이 매칭되지 않으면 `EXTRACTED + UNRESOLVED`로 보존한다. 이를 `NOT_BUSINESS_RULE`이나 `UNSUPPORTED`로 바꾸지 않는다.

### CMR-03 명시적 비비즈니스 판정

`NOT_BUSINESS_RULE`은 제외 Rule 또는 동등한 명시적 근거가 있을 때만 허용한다. 판정 근거 diagnostic이 반드시 존재해야 한다.

## 분류 규칙

### CMR-04 구조와 의미 분리

`PredicateType`에는 AST에서 관찰한 구조만 저장한다. repository, authentication, authorization 같은 프레임워크 또는 비즈니스 의미를 넣지 않는다.

### CMR-05 UNKNOWN과 OTHER 구분

`BusinessRuleCategory.UNKNOWN`은 의미 미해석 상태에 사용한다. `OTHER`는 Rule 의미는 해석됐으나 상위 taxonomy에 전용 category가 없는 경우에만 사용한다.

### CMR-06 ruleId 우선 정체성

구체 Rule의 안정적인 정체성은 `ruleId`다. 새 Rule 추가를 위해 `BusinessRuleCategory` enum을 반드시 변경하도록 만들지 않는다.

## Identity 규칙

### CMR-07 구조 후보 단위

각 Fact Graph `CONDITION` node는 하나의 `PredicateCandidate`를 생성한다. 구조 후보 ID는 graph와 condition node identity에서 결정적으로 생성한다.

### CMR-08 의미 후보 단위

각 Rule 평가는 별도 `BusinessRuleCandidate`를 생성한다. 매칭된 후보 ID는 `predicateCandidateId + ruleId`, 미매칭 결과는 예약된 상태 discriminator를 사용한다.

### CMR-09 복수 Rule 보존

하나의 구조 후보가 여러 Rule과 매칭될 수 있다. 최초 매칭만 남기거나 category 단위로 덮어쓰지 않는다.

## Constraint 규칙

### CMR-10 확인되지 않은 값 금지

소스 Fact에서 확인하지 않은 target, operator, expected value를 constraint에 사실처럼 저장하지 않는다.

### CMR-11 null과 빈 목록 계약

- 정규화 불가능한 constraint: `constraint = null`
- target 미해석 또는 적용 불가: `targetPath = null`
- 확인된 expected value 없음: `expectedValues = []`
- diagnostic 없음: `diagnostics = []`
- evidence 없음: 허용하지 않음

### CMR-12 런타임 의존성 공개

값이 DB, 시간, 인증 principal 또는 다른 런타임 상태에 의존하면 `RUNTIME_DEPENDENT`로 표시한다. 현재 분석에서 값을 모른다는 이유로 literal을 생성하지 않는다.

## Evidence와 Diagnostic 규칙

### CMR-13 Evidence 필수

모든 구조 후보와 의미 후보는 최소 하나의 Evidence를 가진다. Evidence node ID는 원본 Fact Graph node를 참조해야 한다.

### CMR-14 불확실 상태 Diagnostic 필수

`PARTIAL`, `UNSUPPORTED`, `UNRESOLVED`, `NOT_BUSINESS_RULE`, target `UNRESOLVED` 중 하나라도 해당하면 원인을 설명하는 diagnostic이 최소 하나 있어야 한다.

### CMR-15 성공 Diagnostic 선택

구조, 의미, target이 모두 성공한 후보는 빈 diagnostic 목록을 허용한다. 정상 상태를 만족시키기 위한 형식적 성공 diagnostic을 만들지 않는다.

### CMR-16 Source Range 위조 금지

Evidence 좌표는 참조 Fact node의 source range와 일치해야 한다. 좌표가 없는 경우 임의의 0 또는 주변 node 좌표를 사용하지 않고 별도 diagnostic을 남긴다.

## 불변식 규칙

### CMR-17 immutable collection

candidate의 evidence, diagnostics, expectedValues는 null을 허용하지 않고 방어적 복사 후 변경 불가능해야 한다.

### CMR-18 의미 성공 조건

`semanticStatus=RESOLVED`이면 `ruleId`는 nonblank여야 하며 category는 `UNKNOWN`일 수 없다.

### CMR-19 target 상태 조건

`targetStatus=RESOLVED`이면 nonblank `targetPath`가 필요하다. `NOT_APPLICABLE` 또는 `UNRESOLVED`이면 `targetPath`는 null이어야 한다.

## Property-Based Testing 후보

- 동일 graph와 condition node에서 동일 구조 후보 ID가 생성된다.
- 동일 predicate와 ruleId에서 동일 의미 후보 ID가 생성된다.
- 모든 후보의 evidence 목록은 비어 있지 않다.
- 실패 또는 불확실 상태 조합은 빈 diagnostic 목록을 거부한다.
- `OTHER`와 `UNKNOWN`은 각 허용 상태 밖에서 거부된다.
- candidate collection 순서를 바꿔도 정규화된 결과의 identity 집합은 동일하다.
