# UoW 06 — Boolean condition tree

## 목적

조건을 문자열이 아니라 구조화된 boolean tree로 보존한다.

## 지원 범위

- `==`, `!=`, `>`, `>=`, `<`, `<=`
- `&&`, `||`, `!`
- null, boolean, enum, numeric, string literal
- `switch` selector와 case
- `ObjectUtils.isEmpty`, `contains`

## 예시

```java
rent <= 0 || deposit <= 0
```

```text
OR
├─ LTE(rent, 0)
└─ LTE(deposit, 0)
```

## 작업 범위

- operand 순서와 root operator 보존
- 조건과 then/else outcome 연결
- 실패 조건 반전은 고정된 Operator 반전표로만 수행
- De Morgan 변환을 정형화

## 완료 조건

- regex나 자연어 해석 없이 Operator 후보를 만들 수 있다.
- tree를 재구성했을 때 원래 조건과 의미가 같다.

## 테스트

- 중첩 AND/OR와 괄호
- unary negation
- switch expression
- De Morgan 변환

