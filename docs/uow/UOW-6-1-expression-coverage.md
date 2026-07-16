# UOW-6-1: 필수 Expression 지원 확대

## Goal (목표)
값 검증 코드에서 자주 나타나지만 현재 그래프 파싱 단계에서 지원되지 않아 조용히 누락되는 핵심 표현식(`ConditionalExpr`, `InstanceOfExpr`, `CastExpr` 등)의 방문 로직을 구현하여 그래프의 문법 커버리지를 보장한다.

## 1. 문제 증거
현재 `FactExpressionVisitor.java`는 `MethodCallExpr`, `BinaryExpr`, `UnaryExpr`, `ObjectCreationExpr` 등 일부 표현식만 명시적으로 지원하며, 그 외의 많은 표현식들은 `expressionNode()` 내부에서 걸러지거나 `null`을 반환하여 그래프에서 완전히 배제됩니다.
예를 들어 삼항 연산자(`invalid ? error() : success()`)나 `instanceof` 패턴 매칭(`value instanceof String`), 형변환(`(String) value`) 등은 값 검증 영역에서 매우 빈번하게 사용되는데도 그래프에 표현되지 않아 Candidate 추출 누락의 원인이 됩니다.

## 2. 지원 대상 코드 패턴
```java
return invalid ? error() : success();
if (value instanceof String text) { ... }
```

## 3. 현재 그래프
* 삼항 연산자나 instanceof가 통째로 무시되어, 이에 관련된 조건 및 Outcome이 그래프에 매핑되지 않음.

## 4. 기대 그래프
```text
RETURN
  └─ OPERAND_OF → CONDITIONAL(invalid ? error() : success())
                     ├─ CONDITION → FIELD_ACCESS(invalid)
                     ├─ THEN_OUTCOME → METHOD_CALL(error)
                     └─ ELSE_OUTCOME → METHOD_CALL(success)
```

## 5. 수정 책임 클래스
* [FactExpressionVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java)

## 6. 비대상 범위
* `AnnotationExpr`이나 `ClassExpr` 등 값 검증 제어 흐름과 연관성이 떨어지는 비필수 표현식은 지원 대상에서 제외합니다.

## 7. 테스트 케이스
* `instanceof`, 삼항 연산자(`? :`), `CastExpr`이 포함된 코드를 파싱한 뒤 관련 노드(`FactNodeType.CONDITION` 등) 및 엣지가 누락 없이 생성되는지 검증.

## 8. 완료 기준
* `ConditionalExpr` 처리 시 조건, 참 결과, 거짓 결과가 엣지로 모두 연결됩니다.
* `InstanceOfExpr`이 조건 노드로 정상 수집되어 피연산자들이 보존됩니다.
* 미지원 문법 카운터(`UOW-10-1` 연계)에서 해당 표현식들의 미지원 집계수가 0으로 기록됩니다.

## 9. 성능 및 호환성 영향
* 파싱 가능한 표현식 커버리지가 확대되어 GraphRule 평가의 신뢰도가 크게 상승합니다.
