# UOW-04 극성과 오탐 방지

## 목적

boolean helper의 이름이나 반환 타입만 보고 정상 조건을 실패로 오인하지 않는다.

## 필수 시나리오

```java
if (isNotValid(value)) throw failure;   // true가 실패
if (!isValid(value)) throw failure;     // false가 실패
if (isValid(value)) return success;     // 실패 근거 아님
```

## 작업

- unary `!`, `== true`, `== false`, `!= true`, `!= false` 극성을 구조적으로 판정한다.
- 직접 실패 outcome이 없는 boolean 호출은 승격하지 않는다.
- ambiguous expression은 추측하지 않고 PARTIAL 또는 diagnostic으로 처리한다.

## 완료 조건

- 양·음 극성 테스트가 모두 통과한다.
- 일반 boolean 조회 메서드는 후보가 되지 않는다.

