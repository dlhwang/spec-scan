# UOW-1-2: THROW 노드 정체성 통합

## Goal (목표)
동일한 `ThrowStmt`가 조건 제어 흐름에 따라 중복 생성되는 문제를 방지하고, 단 하나의 일관된 `FactNodeType.THROW` 노드만을 생성하여 관계를 연결한다.

## 1. 문제 증거
`FactMethodVisitor.java` 내의 `directOutcomes` -> `outcome` 메서드는 분기 제어문(`IfStmt`, `SwitchStmt` 등)의 outcome으로 `ThrowStmt`를 수집할 때 매번 새 노드를 생성합니다. 또한 향후 `UOW-2-1`에서 메서드 전역에서 `ThrowStmt`를 일괄 수집하는 로직이 추가되면 RETURN 노드와 마찬가지로 중복 생성이 발생하게 됩니다.
따라서 AST Node의 Identity에 매핑되는 단일 `THROW` 노드를 생성하고 조회하여 사용하는 로직이 필요합니다.

## 2. 지원 대상 코드 패턴
```java
if (value == null) {
    throw new IllegalArgumentException("value");
}
```

## 3. 현재 그래프
```text
CONDITION(value == null)
  └─ THEN_OUTCOME → THROW (ID: outcome-then-outcome)
```

## 4. 기대 그래프
```text
METHOD
  └─ ORIGINATES_FROM (혹은 THROW 연동 엣지) → THROW (ID: unified-throw-id)

CONDITION(value == null)
  └─ THEN_OUTCOME → THROW (ID: unified-throw-id)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)

## 6. 비대상 범위
* `ThrowStmt` 뒤의 예외 표현식(Argument, Type 등)의 그래프 연결은 `UOW-2-2`에서 처리합니다.

## 7. 테스트 케이스
* `IfStmt` 내에 `ThrowStmt`가 포함된 샘플 코드에서 생성된 `FactNodeType.THROW` 타입 노드의 중복 여부를 감지.
* `THROW` 노드가 단일 인스턴스로 생성되어 조건 엣지와 메서드 엣지 모두에 정상 연결되었는지 검증.

## 8. 완료 기준
* 하나의 `ThrowStmt`에 대해 단 하나의 `FactNodeType.THROW` 노드만 생성 및 등록됩니다.
* 노드의 ID 생성 정책이 Edge 역할명이나 순회 컨텍스트에 따라 달라지지 않고 AST 소스 범위를 기반으로 일관되게 고정됩니다.

## 9. 성능 및 호환성 영향
* 불필요하게 늘어나는 중복 노드를 방지하여 그래프 구조의 간결함을 유지합니다.
