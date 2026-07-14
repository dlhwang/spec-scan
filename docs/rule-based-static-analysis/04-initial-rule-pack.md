# Work Unit 04: 초기 Rule Pack

## 목적

서로 다른 코드베이스에서 재사용할 수 있는 소수의 구조 기반 `GraphRule`을 구현한다. 각 Rule은 관찰 가능한 그래프 구조와 타입 정보만으로 매칭하며, 지원 범위와 미매칭 조건을 명시한다.

이 Unit의 결과물은 다음 단계의 출력 마이그레이션에서 사용할 초기 Rule Pack과 Rule별 회귀 테스트다. 새로운 그래프 모델이나 Rule 엔진을 다시 설계하는 작업은 포함하지 않는다.

이 Unit에서 구현하는 Rule은 최종 제품의 전체 기본 지원 범위를 의미하지 않는다. 현재 Rule은 Rule 엔진, Evidence 계약, 극성 계산, 타입 및 origin 해석, 충돌 정책이 실제로 동작하는지 검증하기 위한 **초기 제공 Rule**이다. 이후 표준 Java 및 지원 프레임워크에서 반복적으로 나타나는 검증 구조를 기본 제공 Rule Pack으로 지속 확장하고, 프로젝트 고유 규칙은 사용자가 별도 Rule로 추가할 수 있어야 한다.

## Rule 제공 전략

최종 Rule 구성은 다음 두 공급원을 함께 사용한다.

1. **기본 제공 Rule Pack**
   - 분석기가 공식적으로 지원하는 언어, 표준 라이브러리 및 프레임워크의 검증 구조를 제공한다.
   - 사용자가 별도 Rule을 등록하지 않아도 일반적인 검증 조건을 유의미하게 추출할 수 있을 정도로 확장한다.
   - 현재 Unit의 Rule을 출발점으로 사용하되, 현재 목록을 완성된 기본 지원 범위로 간주하지 않는다.

2. **사용자 정의 Rule Pack**
   - 조직, 프로젝트, 도메인 또는 자체 프레임워크에만 존재하는 검증 규칙을 사용자가 추가할 수 있게 한다.
   - 기본 제공 Rule을 제거하거나 대체하는 기능으로 한정하지 않고, 기본 분석 결과를 확장하거나 더 구체적인 의미로 정제할 수 있어야 한다.
   - 사용자 Rule도 기본 Rule과 동일하게 Fact Code Graph, resolved metadata, 상태 및 Evidence 계약을 따라야 한다.

기본 Rule과 사용자 Rule은 동일한 Rule 실행 경로와 결과 모델을 사용해야 한다. Rule 공급 방식이 다르다는 이유로 별도의 분석 의미 체계나 출력 계약을 만들지 않는다.

### 예시와 목록의 해석 원칙

이 문서에 기재된 Rule, API, 프레임워크 또는 코드 구조는 구현 방향과 테스트 기준을 설명하기 위한 것이며 전체 지원 대상을 열거한 폐쇄 목록이 아니다.

- 예시에 등장한 이름, 호출 형태 또는 도메인 표현만 지원하도록 구현하지 않는다.
- 신규 기본 Rule의 우선순위는 예시와의 유사성이 아니라 실제 코드베이스에서의 반복성, 구조적 식별 가능성, 오탐 위험, Validation Condition으로의 변환 가능성을 기준으로 결정한다.
- 사용자 정의 Rule 기능도 특정 예시 문장을 직접 실행 규칙으로 사용하지 않고, 검증 가능한 공통 Rule 모델로 정규화한 뒤 실행한다.
- 문서의 예시가 Fact Code Graph 또는 Rule DSL의 전체 표현 능력을 제한하는 근거가 되어서는 안 된다.

## 선행 조건

- Unit 01의 Fact Code Graph가 predicate, call, value origin, resolved type/signature, control-flow outcome을 제공한다.
- Unit 02의 `BusinessRuleCandidate`, 상태, constraint, Evidence 모델을 사용할 수 있다.
- Unit 03의 `ValidationCandidateDetector`, `GraphRule`, Rule registry와 충돌 정책이 구현돼 있다.
- 실패 outcome은 조건과 직접 연결된 `throw` 또는 실패로 분류된 `return`으로 제한한다.

선행 정보가 일부 없으면 Rule 내부에서 이를 추측해 보완하지 않는다. 구조는 얻었지만 타입이나 origin이 부족한 경우 Unit 02의 상태와 diagnostic으로 표현한다.

## 결과물

- 아래 5개 Rule 구현과 Rule registry 등록
- Rule별 양성, 유사 음성, 극성 반전, 타입 해석 실패 테스트
- 서로 다른 이름과 도메인을 사용하는 최소 2개 fixture 묶음
- Rule ID, 계층, 지원 구조, 필요한 Evidence를 설명하는 짧은 registry 문서 또는 코드 메타데이터
- Rule별 매칭 수와 fallback 사용 여부를 확인할 수 있는 실행 통계

## 공통 매칭 계약

### 판정 순서

각 Rule은 다음 순서로 후보를 평가한다.

1. candidate가 실패 outcome과 연결돼 있는지 확인한다.
2. predicate와 호출 체인이 Rule의 지원 구조인지 확인한다.
3. resolved type과 signature가 있으면 이를 최우선 근거로 사용한다.
4. 필요한 값의 origin과 조건의 성공·실패 극성을 계산한다.
5. category, constraint, 상태와 Evidence를 구성한다.
6. 필수 의미 근거가 부족하면 매칭하지 않거나 더 일반적인 결과를 반환하고 diagnostic을 남긴다.

### 공통 금지 사항

- snippet 전체의 단어 포함 여부를 주 판단 근거로 사용하지 않는다.
- simple method name 하나만으로 receiver의 역할이나 비즈니스 의미를 확정하지 않는다.
- 실패 outcome과 연결되지 않은 호출을 검증으로 단정하지 않는다.
- 변수명이나 필드명만으로 request/domain origin을 추측하지 않는다.
- 소스 또는 resolved metadata에 없는 `targetPath`, `operator`, `expectedValues`를 생성하지 않는다.
- Rule 구현에 fixture의 클래스명, 변수명, Enum 상수명을 하드코딩하지 않는다.

### 공통 결과 계약

- 매칭 결과는 Unit 02의 세 상태 축을 각각 설정한다.
- 모든 결과는 `PREDICATE`와 `FAILURE_OUTCOME` Evidence를 포함한다.
- 호출이 의미 판정의 근거이면 `CALL` Evidence를 포함한다.
- origin이 constraint 판정의 근거이면 `INPUT_ORIGIN` 또는 `DOMAIN_ORIGIN` Evidence를 포함한다.
- 타입 해석 없이 AST 구조나 제한된 이름 fallback을 사용한 결과는 `ExtractionStatus.PARTIAL`과 diagnostic을 가진다.
- target을 연결하지 못해도 의미가 확인됐다면 후보를 버리지 않고 `TargetResolutionStatus.UNRESOLVED`로 반환한다.
- 동일 predicate에서 복수 Rule이 매칭되면 Unit 03의 중복 제거와 충돌 정책을 따른다.

## Rule 1: Optional 실패 조회

| 항목 | 값 |
|---|---|
| Rule ID | `JDK_OPTIONAL_LOOKUP_FAILURE` |
| Layer | `JDK_IDIOM` |
| Predicate type | `LOOKUP_CHAIN` |
| Category | `EXISTENCE` |
| Constraint kind | `CONTROL_FLOW_ONLY` |

### 매칭 조건

- 값 생산 호출의 반환값이 `java.util.Optional<T>`로 해석된다.
- 그 값에 `Optional.orElseThrow(...)`가 호출된다.
- 값이 없을 때의 throw가 candidate의 실패 outcome이다.

`findById`라는 이름, receiver 변수명 또는 반환 타입의 simple name만으로 매칭하지 않는다. `Optional.orElse(...)`, `orElseGet(...)`, `isPresent()` 단독 호출도 이 Rule의 대상이 아니다.

### 결과와 Evidence

- lookup 대상이 입력값인지 확인할 수 있으면 해당 인자의 `INPUT_ORIGIN`을 포함한다.
- lookup 결과가 연결되는 domain value가 확인되면 `DOMAIN_ORIGIN`을 포함한다.
- 일반 Rule은 JDK Optional 실패 관용구까지만 의미화하며, Spring Data repository라고 단정하지 않는다.

Spring Data의 receiver assignability와 resolved signature까지 요구하는 엄격한 변형은 별도 Rule로 둔다.

| 항목 | 값 |
|---|---|
| Rule ID | `SPRING_DATA_FIND_BY_ID_OR_ELSE_THROW` |
| Layer | `SPRING_DATA_JPA` |
| 추가 조건 | receiver가 지원 대상 Spring Data repository 타입에 assignable하고 조회 signature가 해석됨 |

두 Rule이 동시에 매칭될 수 있으므로 pack precedence 또는 ambiguity diagnostic을 테스트로 고정한다.

## Rule 2: PasswordEncoder 일치 실패

| 항목 | 값 |
|---|---|
| Rule ID | `SPRING_SECURITY_PASSWORD_MATCH_FAILURE` |
| Layer | `SPRING` |
| Predicate type | `BOOLEAN_CALL` 또는 부정을 포함한 `COMPOSITE` |
| Category | `AUTHENTICATION` |
| Constraint kind | `RUNTIME_DEPENDENT` |

### 매칭 조건

- resolved receiver가 Spring Security `PasswordEncoder`에 assignable하다.
- 호출 signature가 `matches(CharSequence, String)`와 일치한다.
- `matches(...) == false`, `!matches(...)` 또는 이에 동등한 지원 표현이 실패 outcome으로 이어진다.
- 첫 번째 인자와 두 번째 인자의 origin을 각각 추적할 수 있다.

`String.matches`, 동일한 simple name을 가진 사용자 정의 메서드, 성공 분기에서만 실행되는 호출, 결과가 실패 outcome과 연결되지 않은 호출은 매칭하지 않는다.

### 결과와 Evidence

- 런타임 인코딩 결과에 의존하므로 literal expected value를 만들지 않는다.
- 첫 번째 인자가 입력 origin이면 `INPUT_ORIGIN`, 두 번째 인자가 저장 값이면 `DOMAIN_ORIGIN` Evidence를 추가한다.
- signature는 해석됐지만 한쪽 origin을 찾지 못하면 의미는 `RESOLVED`, target은 `UNRESOLVED`로 유지하고 diagnostic을 남긴다.
- receiver/signature를 해석하지 못한 경우 simple name fallback으로 `AUTHENTICATION`을 확정하지 않는다.

## Rule 3: Enum 허용 상태 guard

| 항목 | 값 |
|---|---|
| Rule ID | `JAVA_ENUM_ALLOWED_VALUE_GUARD` |
| Layer | `JAVA_LANGUAGE` |
| Predicate type | `ENUM_COMPARISON` 또는 `COMPOSITE` |
| Category | `STATE_PRECONDITION`, 의미 미확정 시 `INVARIANT` |
| Constraint kind | `INPUT_LITERAL` 또는 `CONTROL_FLOW_ONLY` |

### 매칭 조건

- 동일한 Enum 값 origin이 하나 이상의 resolved Enum constant와 비교된다.
- 비교식은 `==`, `!=`, `&&`, `||`, `!`의 지원 조합으로 구성된다.
- 조건의 실패 극성을 반영했을 때 성공 경로에서 허용되는 상수 집합을 계산할 수 있다.
- 비교식이 candidate의 실패 outcome을 지배한다.

Rule은 `state`, `status`, `PAYMENT_WAITING`, `PREPARING` 같은 이름을 알면 안 된다. Enum 여부는 resolved type 또는 상수 선언과의 연결로 판단한다.

### 정규화 예

```java
if (state != READY) throw failure;
```

성공 경로의 허용 값은 `READY`다.

```java
if (state != READY && state != RETRYABLE) throw failure;
```

성공 경로의 허용 값은 `READY`, `RETRYABLE`이다.

```java
if (state == CLOSED || state == CANCELLED) throw failure;
```

위 식은 금지 집합은 알 수 있지만 전체 Enum 정의 없이 허용 집합을 열거할 수 없다. 이 경우 허용 값인 것처럼 `expectedValues`를 만들지 않고 `CONTROL_FLOW_ONLY` 또는 부분 추출 결과와 diagnostic을 사용한다.

### 제한

- 서로 다른 origin이 섞인 식은 하나의 허용 집합으로 합치지 않는다.
- method call을 포함한 복합 논리식, collection membership, switch는 초기 범위 밖이다.
- 식 전체를 안전하게 정규화할 수 없으면 `PARTIAL` 또는 `UNSUPPORTED`로 남기며, 추출된 일부 상수를 전체 허용 집합으로 표현하지 않는다.

## Rule 4: 입력 값과 도메인 값 불일치

| 항목 | 값 |
|---|---|
| Rule ID | `INPUT_DOMAIN_VALUE_MISMATCH_GUARD` |
| Layer | `JAVA_LANGUAGE` |
| Predicate type | `COMPARISON` |
| Category | `INVARIANT` |
| Constraint kind | `INPUT_TO_DOMAIN` |

### 매칭 조건

- 비교 양쪽 origin 중 하나는 API 입력, 다른 하나는 domain 또는 저장 값으로 해석된다.
- 지원 비교 연산은 `==`, `!=`이며, 실패 극성을 계산했을 때 두 값의 불일치가 실패로 이어진다.
- 비교식이 candidate의 실패 outcome과 직접 연결된다.

두 값이 모두 입력 origin이거나 모두 domain origin인 경우, origin을 찾지 못한 경우, 리터럴과 비교하는 경우는 이 Rule로 매칭하지 않는다. 객체의 `equals`와 null-safe equality helper는 별도의 지원 계약이 생기기 전까지 초기 범위에서 제외한다.

### Category refinement

기본 category는 `INVARIANT`다. `VERSION_CONSISTENCY`는 다음과 같은 명시적 근거가 있을 때만 별도 Rule 또는 refinement로 적용한다.

- JPA `@Version`이 연결된 domain member
- 지원 대상으로 등록된 version contract annotation
- resolved API binding과 domain version metadata

필드명 `version`, `revision`, `etag`만으로 category를 바꾸지 않는다. refinement가 기본 Rule과 함께 결과를 내는지 대체하는지는 pack precedence로 선언하고 테스트한다.

## Rule 5: Null 실패 guard

| 항목 | 값 |
|---|---|
| Rule ID | `JAVA_NULL_REJECTION_GUARD` |
| Layer | `JAVA_LANGUAGE` |
| Predicate type | `NULL_CHECK` |
| Category | 기본 `INVARIANT`, 추가 근거가 있으면 `EXISTENCE` |
| Constraint kind | `CONTROL_FLOW_ONLY` |

### 매칭 조건

- `value == null`, `null == value` 또는 논리 부정까지 포함한 동등한 지원 표현이 실패 outcome으로 이어진다.
- 검사 대상 value origin을 그래프에서 식별할 수 있다.
- then/else 구조를 고려해 null인 경로가 실제 실패 경로인지 확인한다.

null check 자체만으로 DB resource existence라고 해석하지 않는다. 조회 결과, Optional 변환, repository metadata처럼 존재 의미를 뒷받침하는 근거가 있을 때만 `EXISTENCE` refinement를 허용한다. 단순 방어적 null 검사와 성공 경로의 null 검사는 일반 `INVARIANT` 또는 미매칭으로 남긴다.

## Rule Pack 등록과 우선순위

초기 pack은 최소 다음 그룹으로 분리해 활성화할 수 있어야 한다.

- `java-language`: Enum guard, input/domain mismatch, null rejection
- `jdk-idiom`: Optional lookup failure
- `spring`: PasswordEncoder match failure
- `spring-data-jpa`: Spring Data Optional lookup refinement

프레임워크 dependency가 없는 분석에서는 해당 pack을 등록하지 않아도 Java/JDK Rule이 동작해야 한다. 범용 Rule과 refinement Rule이 같은 predicate에 매칭될 때의 정책은 registry 설정에 명시하고, 등록 순서에 따라 결과가 달라지지 않게 한다.

현재 그룹과 Rule 목록은 초기 등록 구조를 검증하기 위한 최소 구성이다. 후속 기본 Rule은 책임과 dependency 경계를 기준으로 독립적인 pack에 추가할 수 있어야 하며, 신규 pack 추가 때문에 기존 Rule 구현이나 중앙 분류 로직을 수정하도록 강제하지 않는다.

사용자 정의 Rule Pack이 도입되면 다음 원칙을 따른다.

- 기본 제공 Rule Pack은 제품 기본값으로 유지한다.
- 사용자 Rule은 명시적인 적용 범위와 버전을 가진다.
- 기본 Rule과 사용자 Rule의 동시 매칭은 공통 충돌 정책과 diagnostic으로 처리한다.
- 사용자 Rule의 등록 순서나 저장 순서가 최종 결과를 암묵적으로 바꾸지 않는다.
- Rule의 활성화, 비활성화 및 정제 관계는 명시적인 메타데이터로 표현한다.

## 테스트 전략

### Rule별 필수 매트릭스

| 사례 | 기대 결과 |
|---|---|
| 정상 매칭 | Rule ID, category, constraint와 필수 Evidence가 일치함 |
| 이름 변경 | 변수명·메서드 주변 이름을 바꿔도 동일하게 매칭함 |
| 메서드명만 같은 유사 음성 | resolved receiver/signature가 다르면 매칭하지 않음 |
| 실패 outcome 없음 | validation candidate 또는 Rule 결과를 만들지 않음 |
| then/else 반전 | 실제 실패 경로의 극성을 기준으로 같은 의미를 계산함 |
| 논리 부정 | 지원 표현에서 정규화 결과가 동일함 |
| 복수 후보 | 한 메서드의 독립된 검증을 모두 반환함 |
| target origin 실패 | 의미 후보를 보존하고 target 상태와 diagnostic을 기록함 |
| type resolution 실패 | 허용된 fallback만 사용하고 `PARTIAL` 또는 미매칭으로 처리함 |
| 미지원 복합식 | 값을 추측하지 않고 `UNSUPPORTED` 또는 부분 추출 diagnostic을 남김 |

### Rule별 핵심 유사 음성

- Optional: `orElse`, 사용자 정의 Optional 유사 타입, 실패와 무관한 `orElseThrow`
- PasswordEncoder: `String.matches`, 사용자 정의 `matches`, 성공 시 throw하는 반대 극성
- Enum guard: 서로 다른 Enum origin 혼합, non-Enum 상수 비교, 금지 집합을 허용 집합으로 오인하는 식
- Input/domain mismatch: input-input 비교, domain-domain 비교, literal 비교
- Null guard: 성공 경로의 null 허용, 실패와 무관한 null 비교, origin 미확인 값

### Fixture 원칙

- 최소 두 fixture는 패키지명, 클래스명, 변수명과 도메인 용어가 달라야 한다.
- production Rule 코드가 fixture 문자열을 참조하지 않는지 검증한다.
- 가능하면 한 fixture는 타입 해석 성공, 다른 fixture는 의도적 타입 해석 실패를 포함한다.
- fixture별 기대 candidate 수를 명시해 누락과 중복을 함께 탐지한다.

## 구현 순서

1. Java language pack의 null guard와 단순 input/domain mismatch를 구현해 공통 극성 계산을 검증한다.
2. Enum guard를 구현하고 허용 집합과 금지 집합의 혼동을 회귀 테스트로 고정한다.
3. JDK Optional Rule을 구현한 뒤 Spring Data refinement와 충돌 정책을 추가한다.
4. PasswordEncoder Rule로 resolved signature와 origin 요구 사항을 검증한다.
5. 전체 fixture에서 pack 조합, 중복 제거, 등록 순서 독립성을 검증한다.
6. 현재 Unit의 완료 후 별도 후속 작업으로 기본 제공 Rule Pack의 범위를 평가하고 확장한다.
7. 기본 Rule 확장과 독립된 후속 작업으로 사용자 정의 Rule의 작성, 검증, 등록 및 실행 기능을 추가한다.

후속 작업은 현재 5개 Rule에 개별 사례를 계속 덧붙이는 방식이 아니라, 공통 구조를 재사용할 수 있는 Rule 단위와 pack 경계를 먼저 정의한 뒤 진행한다.

## 후속 확장 요구사항

### 기본 제공 Rule Pack 확장

기본 제공 Rule Pack은 다음 원칙으로 지속 확장한다.

- 표준화되었거나 여러 코드베이스에서 반복되는 검증 구조를 우선한다.
- 타입, signature, annotation, predicate, value origin 및 control-flow outcome처럼 관찰 가능한 근거로 식별할 수 있어야 한다.
- 지원 범위를 늘리기 위해 신뢰도가 낮은 이름 기반 추론을 일반화하지 않는다.
- 하나의 공통 구조로 처리할 수 있는 변형을 불필요하게 여러 하드코딩 Rule로 복제하지 않는다.
- 지원 여부는 Rule 수가 아니라 fixture 및 holdout 코드에서의 정밀도, 재현율, 미지원 사유의 설명 가능성으로 평가한다.
- 특정 프로젝트의 요구는 기본 Rule에 섞지 않고 사용자 정의 Rule 후보로 분리한다.

### 사용자 정의 Rule 기능

사용자는 UI를 통해 정해진 작성 계약을 따르는 Rule 정의를 등록할 수 있어야 한다. 입력 형식은 YAML과 구조화된 텍스트를 지원할 수 있으나, 실행 전에는 반드시 검증 가능한 공통 Rule Definition으로 정규화한다.

사용자 정의 Rule 기능은 최소한 다음 책임을 가진다.

- Rule 문법 및 schema 검증
- Rule ID, 버전, 적용 범위와 활성 상태 관리
- Fact Code Graph에서 참조 가능한 조건만 허용
- 실행 전 dry-run과 매칭 결과 확인
- 미매칭, 미지원, 타입 및 origin 해석 실패 diagnostic 제공
- 생성되는 category, constraint 및 Evidence의 출처 검증
- 기본 Rule과의 중복, 정제 및 충돌 처리
- 동일 입력과 동일 Rule 버전에 대한 결정적 실행 결과 보장

자연어에 가까운 텍스트 입력을 지원하더라도 해당 텍스트를 분석 실행 시마다 직접 해석하지 않는다. 텍스트는 검토 가능한 Rule Definition으로 변환하고 schema 검증을 통과한 결과만 저장하고 실행한다.

사용자 Rule은 Fact Code Graph에 없는 사실을 만들어내거나, 근거가 없는 `targetPath`, `operator`, `expectedValues`를 선언적으로 주입하는 우회 수단이 되어서는 안 된다.

## 변경 예상 파일

실제 경로는 현재 패키지 구조를 따르되, 변경 범위는 다음 책임으로 제한한다.

- Java/JDK/Spring/Spring Data `GraphRule` 구현
- Rule pack registry 또는 dependency injection 구성
- 조건 극성 및 지원 논리식 정규화 helper
- Rule별 단위 테스트와 독립 fixture
- Rule 메타데이터 또는 registry 문서

Unit 01~03의 공개 계약이 구현을 막는 경우 해당 Unit의 설계를 조용히 확장하지 않는다. 필요한 계약 변경과 이유를 별도 후속 항목으로 기록한다.

## 완료 기준

- 5개 초기 Rule과 명시한 refinement Rule이 registry를 통해 독립적으로 활성화된다.
- production Rule 코드에 예제 프로젝트의 클래스명, 변수명, 필드명과 Enum 상수명이 없다.
- 동일 Rule이 이름과 도메인이 다른 최소 두 fixture에서 통과한다.
- 각 Rule에 최소 하나의 false-positive 방지 테스트와 극성 반전 테스트가 있다.
- 모든 `expectedValues`는 source Evidence를 가지며, 계산할 수 없는 값은 생성하지 않는다.
- 모든 매칭은 `PREDICATE`와 `FAILURE_OUTCOME` Evidence를 포함한다.
- 타입 또는 origin 해석 실패가 상태와 diagnostic에 드러난다.
- refinement 충돌 결과가 등록 순서와 무관하다.
- 의미를 확정할 근거가 부족하면 더 일반적인 category, `UNRESOLVED` 또는 미매칭을 사용한다.
- Unit 03의 다중 매칭, 중복 제거, Rule 실패 격리 테스트가 초기 pack에서도 통과한다.
- 현재 Rule 목록이 최종 기본 제공 범위가 아니라 후속 확장의 출발점임이 registry 문서 또는 작업 문서에 명시된다.
- 기본 Rule과 향후 사용자 Rule이 동일한 Evidence 및 결과 계약을 사용하도록 확장 경계가 문서화된다.

## 비목표

- 권한 정책 전체 의미 해석
- helper method 내부 도메인 의미 자동 추론
- 무제한 Enum 논리식 정규화
- collection membership, switch, stream predicate 지원
- 이 Unit 안에서 프로젝트별 Rule DSL과 관리 UI를 구현하는 작업
- 이 Unit 안에서 기본 제공 Rule의 전체 제품 범위를 완성하는 작업
- 프레임워크 dependency 자동 탐색과 동적 plugin loading
