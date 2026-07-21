# UOW-02 SemanticContext 도입

## 목표

모든 classifier가 공유하는 graph index와 정규화된 predicate 모델을 도입한다.

## 모델

```text
SemanticPredicate
  expressionTree
  failurePolarity
  inputOrigin
  methodIdentity
  evidence
  resolutionQuality
```

## 작업

- node 및 outgoing/incoming edge index를 graph당 한 번 만든다.
- operand, call target, controlled outcome, parameter flow, input path 조회 API를 제공한다.
- 기존 `StructuralRuleSupport`와 결과 동등성 테스트를 만든다.
- AND/OR와 switch boolean path를 expression tree로 보존한다.

## ExpressionTree MVP 범위

지원 expression은 다음으로 제한한다.

- `BinaryExpr`: `&&`, `||`, `==`, `!=`, `<`, `<=`, `>`, `>=`
- `UnaryExpr`: 논리 부정 `!`
- `MethodCallExpr`
- leaf: parameter, field, local reference, null/boolean/number/string literal, enum constant
- `EnclosedExpr`: 별도 노드를 만들지 않고 내부 expression으로 정규화
- boolean switch path: case 선택조건과 boolean 결과식의 합성 경로

1차 범위에서 제외한다.

- 삼항연산자 `ConditionalExpr`
- pattern matching을 포함한 `InstanceOfExpr`
- lambda 자체를 독립 predicate로 해석하는 기능
- assignment와 side effect expression
- bitwise boolean operator

제외 expression은 삭제하지 않는다. root fact와 evidence를 유지하고 `ResolutionQuality.PARTIAL`, `UNSUPPORTED_SEMANTIC_EXPRESSION` diagnostic으로 표시한다.

## 완료 조건

- classifier가 전체 node/edge 컬렉션을 직접 순회할 필요가 없다.
- 기존 Rule을 아직 삭제하지 않고 shadow context를 생성한다.
- 동일 graph에서 index가 한 번만 생성됨을 검증한다.
- 지원하지 않는 expression이 완전 분석된 것으로 표시되지 않는다.
