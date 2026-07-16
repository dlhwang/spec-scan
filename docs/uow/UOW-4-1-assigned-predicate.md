# UOW-4-1: 지역 변수 Predicate 보존

## Goal (목표)
검증 조건식이 지역 변수에 임시 저장되었다가 `if` 문이나 `return` 문 등에서 사용될 때, 해당 지역 변수의 원래 복합 조건식 구조(AND, OR, Unary 등)를 재귀적으로 보존하여 Candidate 추출 단계에서 검증 대상 필드를 추적할 수 있게 한다.

## 1. 문제 증거
현재 `FactExpressionVisitor.java`의 `visitAssigned`는 다음과 같이 단순 할당만 처리합니다:
```java
void visitAssigned(Expression expression, FactNode local, ...) {
    FactNode node = expressionNode(expression, ...);
    if (node != null) {
        relate(local, node, FactEdgeType.ASSIGNED_FROM, -1, "INITIALIZER", acc);
        ...
    } else {
        visit(expression, local, ...);
    }
}
```
여기서 `expressionNode()`는 `BinaryExpr`를 만나면 `FactNodeType.CONDITION` 노드를 생성해 리턴하지만, 그 하위의 left/right operand에 대해 재귀적인 `visit`를 수행하지 않습니다.
그 결과 `boolean invalid = value == null || value.isBlank();` 같은 코드에서 `value`, `null`, `isBlank()` 등의 정보가 변수 그래프에 포함되지 않아, 나중에 `if (invalid)`를 만났을 때 무엇을 검증하는지 판단할 수 없게 됩니다.

## 2. 지원 대상 코드 패턴
```java
boolean invalid = value == null || value.isBlank();
if (invalid) { ... }
```

## 3. 현재 그래프
```text
LOCAL_VARIABLE(invalid)
  └─ ASSIGNED_FROM → CONDITION("||") -- (하위 value, isBlank 등 단절)
```

## 4. 기대 그래프
```text
LOCAL_VARIABLE(invalid)
  └─ ASSIGNED_FROM → CONDITION("||")
                       ├─ LEFT → CONDITION("==")
                       │           ├─ LEFT → FIELD_ACCESS(value)
                       │           └─ RIGHT → NULL_LITERAL(null)
                       └─ RIGHT → METHOD_CALL(isBlank)
```

## 5. 수정 책임 클래스
* [FactExpressionVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java)

## 6. 비대상 범위
* 지역 변수 선언 시점의 초기화식(`initializer`)만 추적 대상으로 삼으며, 변수가 여러 번 재할당되는 데이터 플로우 추적(Data Flow Analysis)은 본 UOW 범위를 벗어납니다.

## 7. 테스트 케이스
* 복합 Boolean 조건식이 할당된 지역 변수가 포함된 소스를 입력하여, 생성된 `LOCAL_VARIABLE` 노드 하위의 모든 피연산자 노드들이 유실되지 않고 그래프에 등록되는지 검증.

## 8. 완료 기준
* Boolean 변수에 할당된 조건식 내부의 리터럴, 변수, 메서드 호출 노드가 누락 없이 수집됩니다.
* `READS` 엣지를 타고 변수 노드에서 선언 시 할당된 원래 조건 그래프를 역추적할 수 있습니다.

## 9. 성능 및 호환성 영향
* 복합 조건식의 노드가 세분화되어 생성되므로 그래프 내 노드와 엣지 수가 증가하지만, 데이터 보존을 위해 필수적입니다.
