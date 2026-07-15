# UoW 14 — PasswordEncoder 제어 흐름

## 목적

성공 분기 이후 후속 실패가 있는 `PasswordEncoder.matches` 패턴을 검출한다.

## 지원 패턴

```java
if (passwordEncoder.matches(raw, encoded)) {
    return success;
}
return failure;
```

기존 지원 패턴인 `!matches`, boolean literal 비교, 명시적 else도 유지한다.

## 작업 범위

- 제한된 trailing statement 또는 post-dominator 분석
- true/false outcome 연결
- password request origin 연결
- Spring `PasswordEncoder` qualified signature 검증

## 출력 정책

- 현재 output 모델로 정확히 표현 가능한 경우만 executable condition으로 내보낸다.
- 성공과 실패가 모두 HTTP 200이면 공통 `result NOT_EMPTY`를 만들지 않는다.

## 완료 조건

- RealEstate login 패턴이 Fact-backed candidate로 검출된다.
- 이름만 `matches`인 일반 메서드는 검출하지 않는다.

## 테스트

- trailing failure
- explicit else
- negated matches
- unrelated matches
- unresolved receiver

