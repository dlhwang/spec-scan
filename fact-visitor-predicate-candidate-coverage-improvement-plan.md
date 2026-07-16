# Fact Visitor 기반 PredicateCandidate 추출 커버리지 개선 계획

## 1. 문서 목적

이 문서는 `FactMethodVisitor`와 `FactExpressionVisitor`가 생성하는 Code Graph의 커버리지를 점검하고, 값 검증 후보인 `PredicateCandidate`가 GraphRule 평가 단계에 도달하기 전에 누락되거나 의미가 단절되는 문제를 개선하기 위한 방향을 정의한다.

이 문서는 구체 구현 설계서가 아니다. 다음 작업을 위한 기준 문서다.

- 실제 코드베이스를 추가로 읽으며 Unit of Work를 도출한다.
- 각 Unit of Work의 구현 의도와 범위를 합의한다.
- 구현 결과가 값 검증 후보 추출률 개선이라는 목적에 부합하는지 확인한다.
- GraphRule을 추가하기 전에 Visitor와 Graph 생성 단계의 입력 품질을 우선 검증한다.

---

## 2. 분석 범위와 한계

### 분석 범위

현재 확인한 범위는 다음 두 클래스다.

- `FactMethodVisitor`
- `FactExpressionVisitor`

주요 확인 대상은 다음과 같다.

- `FactNodeType.RETURN`
- `FactNodeType.THROW`
- `FactNodeType.CONDITION`
- 반환식과 예외식 내부의 표현식 노드
- 조건과 결과 노드 사이의 Edge
- 지역 변수에 저장된 predicate
- Lambda 및 Switch 내부 실행 범위

### 분석 한계

다음 코드는 아직 전체적으로 확인하지 않은 상태다.

- `PredicateCandidate` 생성 로직
- 11개 `GraphRule`
- `FactGraphAccumulator`
- 노드 ID 생성 정책 전체
- Graph 탐색 방향과 Edge 계약
- 실제 미분류 샘플과 기대 결과
- 테스트 코드와 지원 대상 문법 범위

따라서 이 문서에서 제시하는 문제는 두 종류로 구분한다.

- **확인된 구조적 문제**: 현재 Visitor 코드만으로도 발생 가능성이 명확한 문제
- **검증이 필요한 가설**: 실제 Candidate 생성기와 GraphRule을 함께 확인해야 영향도를 확정할 수 있는 문제

---

## 3. 개선의 최종 목표

### 최종 목표

값 검증 코드가 다음 파이프라인을 안정적으로 통과하도록 한다.

```text
Java Source
  → AST
  → FactNode / FactEdge
  → PredicateCandidate
  → GraphRule
  → 값 검증 분류
```

Visitor 단계에서는 다음 계약을 만족해야 한다.

1. 동일한 AST 의미 단위는 그래프에서 하나의 일관된 노드로 표현된다.
2. 조건과 그 조건이 통제하는 결과가 연결된다.
3. `RETURN`과 `THROW`의 내부 값 또는 예외 표현식이 연결된다.
4. 지역 변수나 Lambda를 거쳐도 predicate의 의미가 보존된다.
5. 지원 대상 Java 문법이 누락 없이 그래프에 반영된다.
6. 지원하지 않는 문법은 조용히 사라지지 않고 관측 가능해야 한다.
7. GraphRule은 소스 문자열 추측보다 구조화된 노드와 Edge를 우선 사용한다.

### 기대 그래프 형태

예를 들어 다음 코드가 있을 때:

```java
if (userId == null) {
    return ValidationError.of("userId");
}
```

최소한 다음 의미 경로가 하나의 그래프 안에서 탐색 가능해야 한다.

```text
METHOD
  ├─ CONTROLS → CONDITION(userId == null)
  │                ├─ LEFT → userId
  │                └─ RIGHT → null
  │
  └─ RETURNS → RETURN
                 └─ VALUE → METHOD_CALL(ValidationError.of)
                                └─ ARGUMENT → "userId"

CONDITION
  └─ THEN_OUTCOME → RETURN
```

중요한 점은 `METHOD → RETURN`과 `CONDITION → RETURN`이 서로 다른 RETURN 노드를 가리키지 않아야 한다는 것이다.

---

## 4. 개선 원칙

### 4.1 GraphRule보다 Graph 입력 품질을 먼저 개선한다

GraphRule을 추가해도 필요한 노드와 Edge가 생성되지 않으면 분류율은 개선되지 않는다.

개선 순서는 다음을 원칙으로 한다.

```text
AST 수집 누락 제거
  → 노드 정체성 통합
  → Edge 의미 보존
  → PredicateCandidate 추출 개선
  → GraphRule 개선
```

### 4.2 문법 지원과 의미 지원을 구분한다

AST 노드를 발견하는 것과 의미 있는 그래프를 생성하는 것은 다르다.

예를 들어 `ThrowStmt`를 찾았더라도 다음 정보가 연결되지 않으면 의미 지원이 완료된 것이 아니다.

- 예외 클래스
- 생성자 인자
- 오류 코드
- 검증 대상 변수
- 통제 조건
- 소속 실행 범위

### 4.3 전역 `findAll`보다 실행 범위를 보존한다

메서드 전체의 descendant를 일괄 수집하면 Lambda, 중첩 블록, Switch 내부의 `RETURN`과 `THROW`가 외부 메서드 결과처럼 섞일 수 있다.

최소한 다음 실행 범위는 구분되어야 한다.

- Method
- Constructor
- Lambda
- 익명 클래스 또는 로컬 클래스의 메서드
- Switch expression
- 중첩 제어문

### 4.4 지원 범위는 테스트 코드로 고정한다

“대충 Java expression을 지원한다”는 완료 기준이 될 수 없다.

지원할 검증 패턴을 샘플 코드로 정의하고, 각 패턴이 기대 그래프와 Candidate를 생성하는지 자동 검증해야 한다.

---

# 5. 개선 과제

## P0-1. RETURN 및 THROW Outcome 노드의 정체성 통합

### 상태

**확인된 구조적 문제**

### 의도

동일한 `ReturnStmt` 또는 `ThrowStmt`가 조건 연결용 노드와 메서드 결과 분석용 노드로 중복 생성되지 않도록 한다.

동일 Outcome에 다음 관계가 함께 연결되어야 한다.

- 소속 Method 또는 Lambda
- 통제하는 Condition
- THEN 또는 ELSE 결과
- 반환식 또는 예외식
- 값과 예외를 구성하는 하위 표현식

### 문제 가설

현재 조건문을 처리할 때 생성한 `RETURN` 또는 `THROW`와 메서드 전체 순회에서 생성한 `RETURN`이 서로 다른 ID로 생성될 가능성이 있다.

그 결과 다음 경로가 분리될 수 있다.

```text
CONDITION → RETURN_A
METHOD → RETURN_B → METHOD_CALL
```

GraphRule이 `CONDITION → RETURN → METHOD_CALL` 경로를 기대하면 후보가 분류되지 않는다.

### 액션 아이템

- 동일 AST Outcome을 식별하는 기준을 정의한다.
- `ReturnStmt`와 `ThrowStmt`의 노드 생성 책임을 한 곳으로 통합한다.
- Outcome 노드 조회 또는 생성 방식을 도입한다.
- 조건 처리 로직은 Outcome을 새로 생성하지 않고 기존 Outcome과 관계를 추가하도록 변경한다.
- Method, Lambda, Condition이 동일 Outcome 노드를 참조하는지 확인한다.
- 노드 ID 생성 규칙이 Edge 역할명에 따라 달라지지 않는지 확인한다.
- 동일 SourceRange에서 중복 노드가 생성되는 경우를 탐지하는 진단 로직을 검토한다.

### 달성 목표

- 하나의 `ReturnStmt`당 `FactNodeType.RETURN` 노드가 하나만 생성된다.
- 하나의 `ThrowStmt`당 `FactNodeType.THROW` 노드가 하나만 생성된다.
- 동일 Outcome에 Method 귀속 관계와 Condition 결과 관계가 함께 존재한다.
- 조건과 반환값 또는 예외값을 하나의 그래프 경로로 탐색할 수 있다.
- 기존 GraphRule이 문자열 조합 없이 구조적 경로만으로 Outcome을 판별할 수 있다.

### 최소 검증 시나리오

```java
if (value == null) {
    return ValidationError.of("value");
}
```

```java
if (value == null) {
    throw new IllegalArgumentException("value");
}
```

```java
if (invalid) {
    return error();
} else {
    return success();
}
```

---

## P0-2. THROW 문장 전체 수집 및 예외 표현식 연결

### 상태

**확인된 구조적 문제**

### 의도

`THROW`가 특정 `if` 또는 `switch`의 직접 자식일 때만 수집되는 구조를 제거하고, 지원 대상 실행 범위 내의 모든 `ThrowStmt`를 그래프에 포함한다.

또한 `throw` 뒤의 예외 표현식을 분석해 예외 타입, 인자, 오류 코드, 검증 대상 변수를 구조적으로 연결한다.

### 문제 가설

현재 `THROW` 노드는 일부 제어문 처리 경로에서만 생성되고, 메서드 전체의 `ThrowStmt`를 일관되게 등록하는 경로가 없을 수 있다.

또한 다음 표현식이 `THROW` 하위 그래프로 연결되지 않을 가능성이 있다.

```java
throw new InvalidParameterException(ErrorCode.INVALID_USER_ID, userId);
```

### 액션 아이템

- 지원 실행 범위 내 모든 `ThrowStmt` 등록 경로를 정의한다.
- `ThrowStmt.getExpression()`을 `FactExpressionVisitor`로 전달한다.
- `ObjectCreationExpr`를 통해 예외 타입과 생성자 인자를 연결한다.
- Factory 기반 예외 생성도 지원할지 범위를 결정한다.

```java
throw ExceptionFactory.invalidUser(userId);
```

- `try`, `catch`, `finally`, loop, synchronized block 등 중첩 구문 안의 throw 수집 정책을 정한다.
- Lambda 내부 throw가 외부 메서드 throw로 오염되지 않도록 실행 범위를 분리한다.
- `THROW → OBJECT_CREATION/METHOD_CALL → ARGUMENT` 경로를 검증한다.

### 달성 목표

- 지원 실행 범위에 존재하는 모든 `ThrowStmt`가 노드로 생성된다.
- `THROW` 노드에서 예외 생성식 또는 예외 반환 메서드 호출을 탐색할 수 있다.
- 예외 생성자 인자에 포함된 검증 대상 변수와 오류 코드를 탐색할 수 있다.
- 조건부 throw의 경우 Condition과 Throw가 연결된다.
- 최상위 throw와 중첩 throw의 수집 결과가 일관된다.

### 최소 검증 시나리오

```java
throw new IllegalArgumentException("invalid");
```

```java
if (userId == null) {
    throw new InvalidParameterException(ErrorCode.INVALID_USER_ID, userId);
}
```

```java
while (iterator.hasNext()) {
    if (invalid(iterator.next())) {
        throw new ValidationException();
    }
}
```

```java
Optional.ofNullable(user)
    .orElseThrow(() -> new UserNotFoundException(userId));
```

---

## P0-3. 반환 표현식 연결의 일관성 확보

### 상태

**확인된 구조적 문제**

### 의도

모든 `RETURN` 노드가 반환 표현식을 동일한 방식으로 하위 그래프에 연결하도록 한다.

단순 반환, 메서드 호출 반환, 객체 생성 반환, 조건부 반환, Switch expression 반환 사이의 처리 차이를 줄인다.

### 액션 아이템

- `ReturnStmt.getExpression()` 처리 책임을 Outcome 등록 로직에 모은다.
- 반환식이 없는 `return;`과 값 반환을 구분한다.
- 반환식의 루트 노드와 하위 표현식이 중복 생성되지 않는지 확인한다.
- 반환식이 `MethodCallExpr`, `ObjectCreationExpr`, `BinaryExpr`, `ConditionalExpr`, `SwitchExpr`일 때의 계약을 정의한다.
- 반환값의 실제 타입 해석 실패가 Candidate 누락으로 이어지지 않도록 fallback 정책을 정한다.

### 달성 목표

- 값 반환문은 항상 `RETURN → 반환식 루트` 관계를 가진다.
- 반환식 하위의 변수, 리터럴, 메서드 호출 인자를 탐색할 수 있다.
- 반환 타입 해석 실패와 AST 그래프 생성 실패가 구분된다.
- GraphRule이 `RETURN`의 source text를 재파싱하지 않아도 된다.

### 최소 검증 시나리오

```java
return false;
```

```java
return ValidationError.of("name");
```

```java
return new ValidationResult(false, "invalid");
```

```java
return invalid ? error() : success();
```

---

## P0-4. 지역 변수에 저장된 Predicate의 하위 표현식 보존

### 상태

**확인된 구조적 문제**

### 의도

검증 조건이 지역 변수에 저장된 뒤 `if`, `return`, `throw`에서 재사용되는 경우에도 원래 조건식을 복원할 수 있도록 한다.

### 문제 예시

```java
boolean invalid = value == null || value.isBlank();

if (invalid) {
    return ValidationError.of("value");
}
```

지역 변수와 조건 노드만 연결되고 `value`, `null`, `isBlank()`가 조건의 하위 operand로 연결되지 않으면 Candidate가 실제 검증 대상을 알 수 없다.

### 액션 아이템

- `visitAssigned()`가 복합 표현식의 하위 구조를 재귀 방문하도록 변경한다.
- Leaf expression과 Composite expression을 구분한다.
- 다음 초기화 표현식을 우선 지원한다.
  - `BinaryExpr`
  - `UnaryExpr`
  - `MethodCallExpr`
  - `ConditionalExpr`
  - `ObjectCreationExpr`
  - `SwitchExpr`
  - `CastExpr`
- 지역 변수 참조에서 선언 노드와 initializer predicate까지 탐색 가능한 Edge 경로를 정의한다.
- 변수 재할당이 있는 경우 최초 선언만으로 처리할지, Assignment까지 추적할지 범위를 결정한다.
- 동일 이름의 변수 shadowing을 SourceRange만으로 정확히 구분할 수 있는지 검토한다.

### 달성 목표

- Boolean 지역 변수의 초기 조건식이 operand 단위로 보존된다.
- `if (invalid)`에서 `invalid`의 선언과 원래 predicate를 역추적할 수 있다.
- 파라미터, 필드, 지역 변수의 이름 충돌이 잘못된 `READS` 관계를 만들지 않는다.
- 단순 지역 변수 추적과 재할당 추적의 지원 범위가 명시된다.

### 최소 검증 시나리오

```java
boolean invalid = value == null;
```

```java
boolean invalid = value == null || value.isBlank();
```

```java
boolean mismatched = !expected.equals(actual);
```

```java
boolean invalidStatus = status != Status.ACTIVE;
```

---

## P1-1. 실행 범위를 보존하는 Statement Traversal 도입

### 상태

**영향도 검증이 필요한 구조적 가설**

### 의도

메서드 전체에서 AST 타입별 `findAll`을 반복하는 방식 대신, 현재 실행 범위와 제어 흐름 컨텍스트를 유지하며 Statement를 재귀 방문한다.

### 문제 가설

전역 descendant 탐색은 Lambda 내부의 `RETURN`, `THROW`, `IfStmt`를 외부 메서드의 결과처럼 등록할 수 있다.

또한 중첩 조건의 부모 조건과 분기 정보를 잃을 수 있다.

### 액션 아이템

- Statement 전용 Visitor 또는 재귀 순회기를 설계한다.
- 다음 컨텍스트를 전달할 수 있는 모델을 검토한다.
  - 현재 executable node
  - 현재 controlling condition
  - branch 종류
  - Lambda 또는 Method scope
  - Switch entry
  - 예외 처리 범위
- Method와 Lambda의 Outcome을 분리한다.
- 중첩 `if`, loop, try/catch, switch block을 재귀 처리한다.
- 기존 `findAll` 기반 수집과 중복되지 않도록 책임을 정리한다.
- CFG 전체 도입 여부는 별도 판단하고, 현재 목적에 필요한 최소 제어 흐름만 지원한다.

### 달성 목표

- Lambda 내부 RETURN이 외부 Method RETURN으로 등록되지 않는다.
- 중첩 조건의 부모-자식 통제 관계를 보존할 수 있다.
- Statement 종류가 추가되어도 특정 메서드의 `findAll` 목록을 계속 늘리지 않아도 된다.
- 각 Outcome이 어느 executable scope에 속하는지 명확하다.

### 최소 검증 시나리오

```java
return users.stream()
    .filter(user -> {
        if (user.isDeleted()) {
            return false;
        }
        return true;
    })
    .toList();
```

```java
if (request != null) {
    if (request.getName() == null) {
        return error();
    }
}
```

---

## P1-2. 값 검증에 필요한 Expression 지원 범위 확대

### 상태

**확인된 문법 커버리지 부족**

### 의도

값 검증 코드에서 자주 나타나는 Expression이 그래프에서 조용히 사라지지 않도록 지원 범위를 확대한다.

### 우선 지원 대상

1. `ConditionalExpr`
2. `InstanceOfExpr`
3. `LambdaExpr`
4. `MethodReferenceExpr`
5. `SwitchExpr`
6. `CastExpr`
7. `ArrayAccessExpr`
8. `AssignExpr`

### 액션 아이템

- 각 Expression 타입이 어떤 FactNodeType과 Edge로 표현될지 결정한다.
- 별도 NodeType 추가가 필요한지 검토한다.
- 기존 `CONDITION`, `METHOD_CALL`, `FIELD_ACCESS`로 충분한 경우 불필요한 타입 증가는 피한다.
- 미지원 Expression을 기록하는 진단 기능을 추가한다.
- 단순 source text 보존과 의미적 operand 분해의 지원 수준을 구분한다.
- 지원 우선순위를 실제 미분류 샘플 빈도로 조정한다.

### 달성 목표

- 값 검증 샘플에서 사용되는 주요 Expression이 null 처리로 사라지지 않는다.
- 미지원 Expression 발생 건수와 타입을 확인할 수 있다.
- 지원 범위가 테스트 케이스 목록으로 명시된다.
- Expression 지원 추가가 기존 그래프 중복을 만들지 않는다.

### 최소 검증 시나리오

```java
return invalid ? error() : success();
```

```java
if (!(value instanceof String text)) {
    throw new ValidationException();
}
```

```java
return Optional.ofNullable(value)
    .map(String::trim)
    .orElseThrow(() -> new ValidationException());
```

---

## P1-3. Lambda와 Method Reference의 그래프 연결 완성

### 상태

**확인된 연결 커버리지 부족**

### 의도

Lambda 또는 Method Reference가 MethodCall의 인자로 사용될 때, 호출 인자와 실행 본문 또는 참조 대상 사이의 관계를 보존한다.

### 액션 아이템

- Lambda 노드를 호출의 `ARGUMENT`로 연결한다.
- Lambda parameter와 collection source 관계를 검증한다.
- Lambda expression body 또는 block body를 Lambda 실행 범위 아래에서 방문한다.
- Lambda 내부 `RETURN`, `THROW`, `CONDITION`을 외부 Method와 분리한다.
- Method Reference 노드를 실제 방문 경로에 연결한다.
- `orElseThrow`, `filter`, `map`, `anyMatch`, `allMatch`, `noneMatch` 등 검증 관련 API의 Lambda 의미를 어느 단계에서 해석할지 결정한다.
- Visitor는 구조를 만들고, API별 의미 해석은 GraphRule 또는 별도 semantic layer에서 담당하도록 책임을 분리한다.

### 달성 목표

- 호출에서 Lambda 또는 Method Reference 인자를 탐색할 수 있다.
- Lambda body 내부 검증 조건과 예외 생성을 탐색할 수 있다.
- 외부 메서드와 Lambda의 Outcome이 혼합되지 않는다.
- Library API 이름에 종속되지 않고 기본 구조 그래프가 생성된다.

### 최소 검증 시나리오

```java
values.stream().anyMatch(value -> value == null);
```

```java
Optional.ofNullable(user)
    .orElseThrow(() -> new UserNotFoundException(userId));
```

```java
values.stream()
    .map(String::trim)
    .filter(String::isBlank)
    .findFirst();
```

---

## P1-4. Switch Statement와 Switch Expression 처리 분리

### 상태

**확인된 의미 혼합 가능성**

### 의도

Switch statement와 Switch expression의 서로 다른 반환 의미를 구분한다.

일반 `ExpressionStmt`를 `RETURN`으로 잘못 생성하지 않고, `ReturnStmt`, `ThrowStmt`, `YieldStmt`, expression body를 올바르게 처리한다.

### 액션 아이템

- `SwitchStmt`와 `SwitchExpr` 방문 로직을 분리한다.
- `default` entry 처리 정책을 정의한다.
- Switch statement에서 다음 Statement를 구분한다.
  - `ReturnStmt`
  - `ThrowStmt`
  - `ExpressionStmt`
  - `BreakStmt`
  - 중첩 Block
- Switch expression에서 다음 결과를 지원한다.
  - expression body
  - block body
  - `YieldStmt`
  - throw expression
- selector와 case label의 비교 관계를 구조화한다.
- 여러 label을 가진 case의 조건 표현 방식을 정의한다.
- fall-through switch statement의 지원 수준을 명시한다.

### 달성 목표

- 일반 method call statement가 RETURN으로 오분류되지 않는다.
- case 내부 Return과 Throw가 해당 case condition과 연결된다.
- Switch expression의 Yield 또는 expression result가 바깥 RETURN과 연결된다.
- default branch 결과가 누락되지 않는다.

### 최소 검증 시나리오

```java
switch (status) {
    case INVALID:
        return error();
    default:
        return success();
}
```

```java
switch (status) {
    case ACTIVE:
        auditService.record(status);
        break;
}
```

```java
return switch (status) {
    case INVALID -> error();
    default -> success();
};
```

```java
return switch (status) {
    case INVALID -> {
        log.warn("invalid");
        yield error();
    }
    default -> success();
};
```

---

## P1-5. 선언 참조 및 변수 Shadowing 정확도 개선

### 상태

**영향도 검증이 필요한 가설**

### 의도

`NameExpr` 또는 `FieldAccessExpr`가 실제 파라미터, 지역 변수, 인스턴스 필드 중 어느 선언을 참조하는지 정확히 연결한다.

### 문제 가설

이름과 SourceRange 거리 기반 추정은 다음 상황에서 잘못된 선언을 선택할 수 있다.

- 중첩 블록의 동일 이름 지역 변수
- Lambda parameter와 메서드 parameter의 이름 충돌
- 지역 변수가 필드 이름을 가리는 경우
- 여러 메서드의 노드가 동일 accumulator에 존재하는 경우
- 생성자 파라미터와 Lombok 필드 연결

### 액션 아이템

- JavaParser Symbol Solver를 변수 선언 참조에도 사용할 수 있는지 검토한다.
- 현재 accumulator 탐색 범위가 method-local인지 확인한다.
- 선언 노드에 executable scope ID 또는 lexical scope 정보를 추가할지 검토한다.
- 해석 성공 시 Symbol Solver 결과를 우선하고, 실패 시 현재 heuristic을 fallback으로 사용한다.
- heuristic fallback 사용 여부를 TypeResolution 또는 진단 정보에 남긴다.

### 달성 목표

- 동일 이름의 선언이 여러 개 있어도 올바른 선언과 연결된다.
- Symbol resolution 실패와 heuristic 연결이 구분된다.
- 잘못된 `READS` Edge가 PredicateCandidate의 대상 필드를 오염시키지 않는다.

---

## P2-1. PredicateCandidate Seed 범위 재검토

### 상태

**Candidate 생성 코드 확인 필요**

### 의도

값 검증 후보의 시작점을 `RETURN`과 `THROW`로만 제한했을 때 발생하는 구조적 누락을 확인하고, 필요한 경우 Candidate 종류를 확장한다.

### 누락 가능 패턴

```java
Objects.requireNonNull(userId);
```

```java
Assert.hasText(name, "name is required");
```

```java
Preconditions.checkArgument(age >= 0);
```

```java
errors.rejectValue("name", "required");
```

```java
rejectIfEmptyOrWhitespace(errors, "name", "required");
```

```java
validator.validate(request);
```

이러한 코드는 현재 메서드 AST 안에 직접적인 `RETURN` 또는 `THROW`가 없을 수 있다.

### 액션 아이템

- 현재 `PredicateCandidate` seed 조건을 확인한다.
- 실제 미분류 샘플 중 Outcome이 없는 validation sink 비율을 측정한다.
- 후보 종류를 다음처럼 분리할지 검토한다.
  - Outcome Candidate
  - Validation Sink Candidate
  - Validation Condition Candidate
- 모든 MethodCall을 Candidate로 올리지 않도록 제한 기준을 정의한다.
- 제한 기준 후보:
  - resolved owner type
  - qualified method signature
  - configured validation API catalog
  - condition expression argument 존재 여부
  - 오류 누적 객체 사용 여부
  - void-returning guard method
- Custom validator method의 interprocedural 추적 범위를 별도 Unit of Work로 분리한다.

### 달성 목표

- 직접 throw/return이 없는 대표 검증 API가 Candidate 단계에서 누락되지 않는다.
- MethodCall 후보 폭증으로 분석 성능과 정밀도가 무너지지 않는다.
- Candidate 유형별로 적용할 GraphRule 범위를 구분할 수 있다.
- Candidate 생성률, 분류 성공률, false positive를 각각 측정할 수 있다.

---

## P2-2. 미지원 AST와 그래프 단절 관측성 추가

### 상태

**개선 권장**

### 의도

Visitor가 처리하지 못한 AST가 조용히 사라져 원인을 추적하기 어려운 문제를 줄인다.

### 액션 아이템

- 미지원 Expression 타입별 카운터를 추가한다.
- 미지원 Statement 타입별 카운터를 추가한다.
- Outcome은 생성됐지만 value child가 없는 경우를 집계한다.
- Condition은 생성됐지만 operand가 없는 경우를 집계한다.
- 동일 SourceRange와 NodeType의 중복 노드 발생을 탐지한다.
- Candidate 생성 실패 사유를 상태값 또는 진단 이벤트로 남긴다.
- 운영 출력과 테스트 진단 출력의 상세 수준을 분리한다.

### 달성 목표

다음 지표를 확인할 수 있어야 한다.

- AST 타입별 방문 수
- AST 타입별 지원 수와 미지원 수
- RETURN 수와 반환식 연결 성공 수
- THROW 수와 예외식 연결 성공 수
- CONDITION 수와 operand 연결 성공 수
- 중복 Outcome 노드 수
- PredicateCandidate 생성 수
- Candidate 미생성 사유
- GraphRule별 평가 대상 수와 성공 수

---

# 6. 테스트 전략

## 6.1 Golden Graph Test

샘플 Java 코드를 입력하고 생성된 노드와 Edge를 고정된 결과와 비교한다.

검증 대상:

- FactNodeType
- SourceRange
- payload
- type resolution 상태
- Edge type
- Edge direction
- ordinal
- role
- 노드 중복 여부

전체 노드 ID 문자열에 과도하게 의존하기보다 의미적 그래프 구조를 검증하는 방식을 우선한다.

## 6.2 Candidate Extraction Test

Visitor가 생성한 그래프에서 기대하는 `PredicateCandidate`가 생성되는지 검증한다.

예시 검증 항목:

- Candidate 종류
- Candidate 시작 노드
- 관련 Condition
- 검증 대상 변수
- 비교 연산자
- 기준 값
- Outcome 종류
- 오류 코드 또는 예외 타입

## 6.3 GraphRule Contract Test

각 GraphRule이 요구하는 최소 그래프 형태를 테스트 코드로 명시한다.

Rule 실패가 다음 중 어디에서 발생했는지 구분해야 한다.

- Visitor가 노드를 만들지 못함
- Visitor가 Edge를 만들지 못함
- Candidate가 생성되지 않음
- Candidate는 생성됐으나 Rule이 탐색하지 못함
- Rule은 일치했으나 최종 분류가 누락됨

## 6.4 Regression Corpus

실제 프로젝트에서 미분류된 값 검증 코드를 작은 corpus로 축적한다.

각 사례는 다음 정보를 가진다.

```text
case id
source code
expected candidate
expected graph evidence
expected GraphRule
expected category
current failure stage
```

---

# 7. 완료 판단 기준

Visitor 개선 작업은 단순히 코드가 컴파일되거나 노드 수가 증가했다고 완료로 판단하지 않는다.

다음 조건을 모두 만족해야 한다.

## 구조 정확성

- 동일 AST Outcome의 중복 노드가 없다.
- Outcome과 Condition, Method, value expression 관계가 연결된다.
- Lambda와 Method 실행 범위가 섞이지 않는다.
- Switch statement와 expression의 의미가 구분된다.

## 커버리지

- 정의된 필수 검증 패턴 corpus가 모두 그래프에 표현된다.
- 미지원 AST가 측정 가능하다.
- RETURN과 THROW 내부 표현식 연결률을 측정할 수 있다.

## Candidate 개선

- 기존 미분류 사례 중 Visitor 또는 Candidate 단계 누락 건이 감소한다.
- GraphRule 평가 대상까지 도달하는 Candidate 수가 증가한다.
- Candidate 증가가 무분별한 false positive 증가로 이어지지 않는다.

## 회귀 안정성

- 기존 정상 분류 사례가 유지된다.
- 노드 중복과 Edge 중복이 증가하지 않는다.
- 동일 입력에 대해 그래프가 결정적으로 생성된다.
- 성능 저하가 허용 범위 안에 있다.

---

# 8. 세분화된 Unit of Work 분할 기준

실제 Unit of Work는 코드 확인 후 조정하되, 한 작업에서 너무 많은 문법과 의미를 함께 고치지 않습니다. 각 Unit of Work는 커밋 단위로 실행할 수 있도록 세세하게 분리되어 개별 문서로 관리됩니다.

전체 UOW 진행 현황 및 인덱스는 [UOW Index](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/index.md)를 참고하십시오. 모든 진행 상황은 [construction.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/construction.md)를 통해 추적됩니다.

## UOW 파일 목록

* **UOW-1-1**: [RETURN 노드 정체성 통합](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-1-1-return-identity.md) - 중복 RETURN 노드 생성 방지 및 식별 규칙 단일화
* **UOW-1-2**: [THROW 노드 정체성 통합](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-1-2-throw-identity.md) - 중복 THROW 노드 생성 방지 및 식별 규칙 단일화
* **UOW-2-1**: [THROW 전체 수집 도입](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-2-1-throw-traversal.md) - 특정 제어문 외의 전체 THROW 문 수집 구조 도입
* **UOW-2-2**: [THROW 예외 표현식 연결](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-2-2-throw-expression.md) - throw 예외 객체의 생성 인자 및 타입 연결
* **UOW-3-1**: [RETURN 표현식 연결 일관화](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-3-1-return-expression.md) - 모든 RETURN 노드와 반환 표현식 하위 그래프 연결 일관화
* **UOW-4-1**: [지역 변수 Predicate 보존](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-4-1-assigned-predicate.md) - 지역 변수에 저장된 Boolean 조건식 구조 재귀 보존
* **UOW-5-1**: [Lambda 실행 범위 분리](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-5-1-lambda-traversal.md) - Lambda 내부 Outcome 수집 오염 방지 및 스코프 분리
* **UOW-6-1**: [필수 Expression 지원 확대](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-6-1-expression-coverage.md) - `ConditionalExpr`, `InstanceOfExpr` 등 검증 표현식 지원
* **UOW-7-1**: [Switch 처리 분리](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-7-1-switch-traversal.md) - Switch Statement와 Expression 분리 처리 및 YieldStmt 지원
* **UOW-8-1**: [선언 참조 및 Shadowing 개선](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-8-1-shadowing-resolution.md) - 변수 Shadowing 방지 및 lexical scope 기반 바인딩 정밀화
* **UOW-9-1**: [PredicateCandidate Seed 확장](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-9-1-candidate-seed.md) - Objects.requireNonNull 등 validation sink 기반 시드 수집
* **UOW-10-1**: [관측성 및 진단 도구 추가](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-10-1-visitor-diagnostics.md) - 미지원 AST 계측 및 중복 노드 진단 로그 추가
* **UOW-11-1**: [테스트 하네스 구축](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-11-1-test-harness.md) - Golden Graph Test 및 Candidate Extraction Test 구조 완성

각 Unit of Work의 완성 조건 및 구현 의도는 각 문서에 기술되어 있습니다.


---

# 9. 우선순위 요약

## 즉시 확인 및 개선

1. 동일 `RETURN`과 `THROW` 노드의 중복 생성 여부
2. `THROW` 내부 예외 표현식 누락
3. 조건 Outcome과 실제 반환값 그래프의 단절
4. 지역 변수 predicate initializer의 하위 표현식 누락

## 다음 단계

5. Lambda 실행 범위 오염 여부
6. `ConditionalExpr`, `InstanceOfExpr`, `SwitchExpr` 지원
7. Switch statement의 `ExpressionStmt` 오분류 여부
8. 변수 선언 참조 정확도

## Candidate와 Rule 확인 후 결정

9. `RETURN`과 `THROW` 외 validation sink 후보 확장
10. 11개 GraphRule의 최소 그래프 계약 재정의

---

# 10. 핵심 판단

현재 값 검증 분류 실패를 GraphRule 부족으로만 판단하면 안 된다.

먼저 다음 질문에 답해야 한다.

```text
검증 코드가 AST에서 발견됐는가?
→ 필요한 FactNode가 생성됐는가?
→ 필요한 FactEdge가 연결됐는가?
→ 하나의 의미 단위가 여러 노드로 분리되지 않았는가?
→ PredicateCandidate가 생성됐는가?
→ 그 다음에 GraphRule이 평가됐는가?
```

이 순서를 기준으로 실패 지점을 분리해야 한다.

Visitor 단계에서 의미 정보가 누락되거나 그래프가 단절된 상태라면 GraphRule 추가는 근본 해결이 아니다. 우선 그래프가 값 검증의 증거를 안정적으로 표현하도록 만든 뒤 Candidate와 Rule을 개선해야 한다.
