# Rule-Based 정적분석기 지원 범위

이 문서는 `rule-based-static-analysis` 개선이 완료됐을 때 초기 PoC에서 무엇을 분석하려는지 예시 중심으로 설명한다.

## 지원하려는 범위

### 1. 명시적인 조건과 실패 흐름

조건문이 `throw` 또는 `return`으로 직접 이어지는 코드를 검증 후보로 찾는다.

```java
if (request.name() == null) {
    throw new IllegalArgumentException();
}
```

```java
if (!isAllowed(request)) {
    return false;
}
```

지원 대상:

- `if (...) throw ...`
- `if (...) return ...`
- `else`에서 발생하는 `throw` 또는 `return`
- 한 메서드 안에 존재하는 복수의 검증 조건

### 2. 비교 및 논리 조건식

실패 조건을 구성하는 비교와 논리 연산을 분석한다.

```java
if (age < 18 || age > 100) {
    throw new InvalidAgeException();
}
```

지원 대상:

- `==`, `!=`
- `<`, `<=`, `>`, `>=`
- `&&`, `||`, `!`
- 괄호가 포함된 복합 조건
- then/else 및 논리 부정을 고려한 실패 방향

### 3. 요청값과 도메인값의 출처 추적

조건식에 사용된 값이 API 요청에서 왔는지, 조회된 도메인 객체에서 왔는지 연결한다.

```java
if (!request.version().equals(entity.version())) {
    throw new VersionMismatchException();
}
```

지원 대상:

- DTO 또는 API parameter의 필드 접근
- 도메인 객체의 필드 또는 getter 접근
- 요청값과 도메인값의 비교
- 단순 지역 변수 별칭

```java
String requestedName = request.name();

if (requestedName == null) {
    throw new IllegalArgumentException();
}
```

### 4. 메서드 호출을 사용한 boolean 검증

조건식에 사용된 메서드 호출의 receiver 타입, signature, 인자와 실패 방향을 분석한다.

```java
if (!passwordEncoder.matches(request.password(), member.password())) {
    throw new AuthenticationFailedException();
}
```

지원 대상:

- boolean 반환 메서드가 조건식에 사용되는 경우
- 메서드 호출의 receiver
- 호출 인자의 값 출처
- resolved type과 method signature
- 타입 해석 실패 시 제한된 구조 기반 fallback

메서드 이름만 같은 코드는 동일한 의미로 판단하지 않는다.

### 5. `Optional` 조회 실패 패턴

값을 조회한 뒤 `Optional.orElseThrow()`로 실패 처리하는 구조를 존재 검증 후보로 분석한다.

```java
Member member = memberRepository.findById(memberId)
    .orElseThrow(MemberNotFoundException::new);
```

지원 대상:

- 값을 반환하는 호출과 `Optional.orElseThrow()`의 연결
- JDK `Optional` 기반 실패 조회
- Spring Data receiver 타입이 확인되는 조회 패턴

`findById`라는 메서드명만으로 Repository 조회라고 판단하지 않는다.

### 6. Enum 허용 상태 검증

Enum 값과 하나 이상의 Enum 상수를 비교하는 상태 조건을 분석한다.

```java
if (order.status() != PAYMENT_WAITING
        && order.status() != PREPARING) {
    throw new InvalidOrderStateException();
}
```

지원 대상:

- Enum 값의 직접 비교
- 하나 또는 여러 개의 허용·거부 상태
- `&&`, `||`, `!`로 구성된 초기 범위의 상태 조건
- 조건의 방향을 반영한 허용값 계산

`status`, `PAYMENT_WAITING` 같은 특정 이름을 미리 알고 판단하지 않는다.

### 7. null 거부 조건

null 비교가 실패 흐름으로 연결된 코드를 검증 후보로 분석한다.

```java
if (member == null) {
    throw new MemberNotFoundException();
}
```

지원 대상:

- `value == null`
- `value != null`
- null 조건과 `throw` 또는 `return`의 연결

null 검사만으로 DB 데이터 존재 여부라고 단정하지 않는다. 근거가 부족하면 일반 불변조건 또는 미해석 후보로 남긴다.

### 8. 제한된 메서드 호출 탐색

API 메서드에서 직접 보이지 않는 검증도 설정된 깊이 안에서 호출 관계를 따라가며 탐색한다.

```java
public void create(CreateRequest request) {
    validateRequest(request);
}

private void validateRequest(CreateRequest request) {
    if (request.name() == null) {
        throw new IllegalArgumentException();
    }
}
```

지원 대상:

- 설정된 최대 깊이 안의 메서드 호출
- 호출된 메서드 내부의 명시적 조건과 실패 흐름
- Rule 실행 비용을 제한하는 탐색 budget

`validate*`, `check*`, `ensure*`, `require*`라는 이름만으로 검증 메서드라고 판단하지 않는다. 실제 내부 구조 또는 명시적으로 등록된 프로젝트 Rule이 필요하다.

### 9. 타입 해석 성공 및 실패 상태

타입을 해석할 수 있으면 fully qualified type과 resolved signature를 우선 사용한다.

타입 해석에 실패하더라도 분석 전체를 중단하지 않고 다음과 같이 처리한다.

- 얻을 수 있는 구조만 부분 추출
- 제한된 이름 휴리스틱 사용
- 결과를 `PARTIAL` 상태로 표시
- 타입 해석 실패 원인을 diagnostic으로 제공

### 10. 분석 결과의 근거와 상태 제공

분석 결과마다 무엇을 근거로 판단했는지 함께 제공한다.

지원 대상:

- 소스 파일 경로
- 시작 및 종료 line/column
- 실제 source snippet
- 조건, 호출, 입력값, 도메인값, 실패 흐름 Evidence
- 구조 추출 상태
- 비즈니스 의미 해석 상태
- API 요청 필드 연결 상태
- 미지원 또는 미해석 사유

소스에서 확인하지 못한 `targetPath`, `operator`, `expectedValues`는 임의로 생성하지 않는다.

### 11. 초기 비즈니스 Rule

초기 PoC에서는 다음 Rule을 우선 지원한다.

1. `Optional.orElseThrow()` 기반 조회 실패
2. Spring Security `PasswordEncoder.matches()` 불일치
3. Enum 허용 상태 guard
4. 요청값과 현재 도메인값 불일치
5. null 실패 guard

각 Rule은 다음 사례를 함께 검증한다.

- 정상 매칭
- 변수명과 도메인명을 변경한 동일 구조
- 메서드명만 같은 유사 코드
- 실패 흐름이 없는 코드
- then/else 반전
- 논리 부정
- 타입 해석 실패
- 한 메서드 안의 복수 후보

### 12. 기존 API 조건 출력과의 연결

새 분석 결과를 기존 `ApiCondition` 및 export 결과에 연결한다.

지원 대상:

- 입력 필드와 비교 연산자 변환
- 소스에서 확인한 expected value 전달
- runtime-dependent 조건 구분
- target을 찾지 못한 결과의 제외 또는 diagnostic 처리
- 기존 분석 결과와 신규 분석 결과의 병렬 비교

## 현재 계획에 명시적으로 포함되지 않은 범위

다음 항목은 유용한 추가 후보지만, 현재 개선 문서의 초기 Rule Pack에는 명시돼 있지 않다.

### 1. Bean Validation annotation 직접 추출

```java
@NotNull
@NotBlank
@Size
@Min
@Pattern
```

이를 지원하려면 별도의 Bean Validation Rule Pack 범위를 추가해야 한다.

### 2. Spring Validator API

```java
Errors.reject(...)
Errors.rejectValue(...)
ValidationUtils.rejectIfEmptyOrWhitespace(...)
```

이 호출은 `throw` 없이 오류를 등록하므로 별도의 실패 outcome 모델이 필요하다.

### 3. `ConstraintValidator`

```java
class CustomValidator implements ConstraintValidator<CustomConstraint, Value> {
    public boolean isValid(Value value, ConstraintValidatorContext context) {
        // ...
    }
}
```

지원하려면 `ConstraintValidator` 구현 타입, `isValid()`의 boolean 반환, violation context 사용을 별도로 분석해야 한다.

### 4. 이름만으로 알려진 Validator 메서드 판정

```text
validate*
check*
ensure*
require*
```

이름 패턴만으로 비즈니스 검증이라고 확정하는 것은 지원하지 않는다. 프로젝트별 설정이나 실제 타입·내부 실패 흐름이 확인되는 경우에만 Project Extension Rule로 지원할 수 있다.

## 초기 PoC에서 제외하는 범위

- 전체 프로그램 수준의 완전한 interprocedural data flow
- SSA 및 완전한 CFG
- reflection과 동적 디스패치의 완전 해석
- 반복문과 stream 내부의 복합 검증
- helper method를 통과하는 임의 깊이의 값 추적
- 모든 Java 프레임워크의 의미 모델
- 모든 비즈니스 로직의 자동 분류
- 자동 실행 테스트의 완전 생성

## 한 문장 요약

> 초기 PoC는 `if`, `throw`, `return`, 비교식, 메서드 호출, 값의 출처와 일부 Java·Spring 관용구를 구조적으로 분석해 근거 있는 비즈니스 Rule 후보를 만들고, 해석하지 못한 코드는 추측하지 않고 상태와 이유를 보고하는 범위다.
