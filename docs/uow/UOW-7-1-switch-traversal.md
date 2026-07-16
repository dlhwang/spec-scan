# UOW-7-1: Switch 처리 분리

## Goal (목표)
`SwitchStmt`(일반 switch 문)와 `SwitchExpr`(값으로 평가되는 switch 식)의 서로 다른 흐름 및 반환 의미를 구분하여 처리하고, Switch Expression의 Yield 및 case별 Outcome을 그래프 상에 정확히 구조화한다.

## 1. 문제 증거
현재 `FactMethodVisitor.java`의 `switchEntries`는 `SwitchStmt`와 `SwitchExpr`을 동일하게 다루며, 각 case entry 하위의 `Statement`를 돌며 `ThrowStmt` 또는 `ExpressionStmt`를 `RETURN` 노드로 임의 생성해 버립니다.
이로 인해 다음과 같이 값을 반환하지 않는 일반 메서드 호출문이 switch 블록 내에 있다는 이유만으로 `RETURN` 노드로 오분류되는 문제가 발생합니다:
```java
switch (status) {
    case ACTIVE:
        auditService.record(status); // 일반 메서드 호출이나 RETURN 노드로 생성됨
        break;
}
```
또한, 최신 Java 규격의 `SwitchExpr`에서 쓰이는 `YieldStmt` 및 화살표 표현식(`->`)에 대한 결과 맵핑 구조가 불안정합니다.

## 2. 지원 대상 코드 패턴
```java
return switch (status) {
    case INVALID -> error();
    default -> {
        log.info("default case");
        yield success();
    }
};
```

## 3. 현재 그래프
* Switch Statement 내부의 임의의 `ExpressionStmt`가 `FactNodeType.RETURN`으로 잘못 취급되어 그래프 오염.
* YieldStmt 구조 파싱 누락.

## 4. 기대 그래프
```text
METHOD
  └─ RETURNS → RETURN
                 └─ OPERAND_OF → SWITCH_EXPR
                                    ├─ SELECTOR → status
                                    ├─ CASE(INVALID) → THEN_OUTCOME → error()
                                    └─ CASE(DEFAULT) → THEN_OUTCOME → success() (YieldStmt 파싱 결과)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)
* [FactExpressionVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java)

## 6. 비대상 범위
* `fall-through`가 복잡하게 얽힌 switch statement의 경우 복잡한 제어 흐름 추적 대신, 단순 조건 매핑으로 제한합니다.

## 7. 테스트 케이스
* `SwitchStmt` 내부의 단순 메서드 호출이 `RETURN` 노드로 오분류되지 않는지 검증.
* `SwitchExpr` 내부의 `YieldStmt` 및 expression body가 외부 `RETURN` 노드와 연결되는지 검증.

## 8. 완료 기준
* Switch statement 내의 일반 문장이 `RETURN`으로 오분류되지 않습니다.
* `SwitchExpr`에 대한 case 결과물들이 상위 식(Expression)으로 정확히 취합 연결됩니다.

## 9. 성능 및 호환성 영향
* 복잡한 Switch 처리가 깔끔해져 불필요한 가짜 `RETURN` 노드가 대폭 제거됩니다.
