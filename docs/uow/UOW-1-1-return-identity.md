# UOW-1-1: RETURN 노드 정체성 통합

## Goal (목표)
동일한 `ReturnStmt`가 조건부 Outcome 연결용 노드와 메서드 수준 전체 순회 노드로 중복 생성되는 문제를 방지하고, 하나의 일관된 `FactNodeType.RETURN` 노드만을 생성하여 관계를 연결한다.

## 1. 문제 증거
현재 `FactMethodVisitor.java`에는 두 가지 경로로 `ReturnStmt` 노드가 생성됩니다:
1. `method.findAll(IfStmt.class)` 및 `SwitchStmt` 처리 중 `directOutcomes` -> `outcome`을 호출하여 ID가 `edge.name()`을 접미사로 가지는 `RETURN` 노드를 생성.
2. `method.findAll(ReturnStmt.class)`를 순회하며 ID가 `"method-return"`을 접미사로 가지는 `RETURN` 노드를 생성.

이로 인해 하나의 `return` 문에 대해 서로 다른 두 개의 `FactNode` 객체가 생성되어 그래프에 등록됩니다. 결과적으로 조건과의 연결점(`CONDITION → RETURN_A`)과 반환식의 연결점(`METHOD → RETURN_B → EXPRESSION`)이 단절됩니다.

## 2. 지원 대상 코드 패턴
```java
if (value == null) {
    return ValidationError.of("value");
}
```

## 3. 현재 그래프
```text
CONDITION(value == null) 
  └─ THEN_OUTCOME → RETURN (ID: outcome-then-outcome) -- (반환식 연결 없음)

METHOD
  └─ RETURNS → RETURN (ID: method-return)
                 └─ VALUE → METHOD_CALL(ValidationError.of)
```

## 4. 기대 그래프
```text
METHOD
  └─ RETURNS → RETURN (ID: unified-return-id)
                 └─ VALUE → METHOD_CALL(ValidationError.of)

CONDITION(value == null)
  └─ THEN_OUTCOME → RETURN (ID: unified-return-id)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)

## 6. 비대상 범위
* `ThrowStmt` 노드의 중복 처리는 `UOW-1-2`에서 별도로 처리합니다.
* Return 표현식 자체의 정합성 연결은 `UOW-3-1`에서 다룹니다.

## 7. 테스트 케이스
* `IfStmt`가 포함된 샘플 코드 파싱 후, 전체 노드 리스트 내 `FactNodeType.RETURN` 타입의 노드 개수가 1개인지 검증.
* 해당 단일 `RETURN` 노드가 `CONDITION` 노드의 `THEN_OUTCOME` 엣지와 `METHOD` 노드의 `RETURNS` 엣지 모두에서 정상 탐색 가능한지 확인.

## 8. 완료 기준
* 하나의 `ReturnStmt`에 대해 오직 단 하나의 `FactNodeType.RETURN` 노드만 존재해야 합니다.
* 조건(Condition)과 반환값(Return value)이 하나의 연결된 그래프 경로로 탐색 가능합니다.

## 9. 성능 및 호환성 영향
* 불필요한 노드 및 엣지 중복 생성이 억제되어 그래프 축적 및 탐색 성능이 미세하게 향상됩니다.
