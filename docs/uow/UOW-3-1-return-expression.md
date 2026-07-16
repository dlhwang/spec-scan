# UOW-3-1: RETURN 표현식 연결 일관화

## Goal (목표)
모든 `RETURN` 노드가 일괄적으로 반환 표현식을 하위 그래프에 연결하도록 처리 방식을 일관화하여, 조건부 분기 등에서 누락되는 표현식 경로를 완비한다.

## 1. 문제 증거
현재 `FactMethodVisitor.java`의 `ReturnStmt` 순회부에서는 `expressions.visit()`을 통해 표현식을 정상 방문하지만, `directOutcomes` -> `outcome`을 거쳐 생성된 `RETURN` 노드들에 대해서는 하위 표현식을 방문하는 경로가 누락되어 있습니다.
이로 인해 분기문 내부에서 직접 반환하는 값(예: `return false;`, `return ValidationError.of(...)`)의 세부 내용이 조건 결과와 이어지지 않는 문제가 발생합니다.

## 2. 지원 대상 코드 패턴
```java
if (invalid) {
    return ValidationError.of("name");
}
```

## 3. 현재 그래프
* 조건부 Outcome으로 생성된 `RETURN` 노드는 자식 표현식 노드(`ValidationError.of`)를 가지지 못하고 단절됨.

## 4. 기대 그래프
```text
CONDITION(invalid)
  └─ THEN_OUTCOME → RETURN
                      └─ OPERAND_OF → METHOD_CALL(ValidationError.of)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)

## 6. 비대상 범위
* 반환값이 없는 단순 `return;` 문은 하위 표현식이 연결되지 않는 것이 정상이므로 예외로 처리합니다.

## 7. 테스트 케이스
* `if (cond) { return methodCall(); }` 형태의 소스 파싱 후, `CONDITION → RETURN → METHOD_CALL` 경로가 중복 없이 연결되는지 확인.

## 8. 완료 기준
* 모든 값 반환문은 `RETURN → 반환식 루트` 관계를 가집니다.
* 중복 Outcome 해제 작업(`UOW-1-1`)과 유기적으로 작용하여, 단일화된 `RETURN` 노드에 조건 연결과 표현식 연결이 동시에 보존됩니다.

## 9. 성능 및 호환성 영향
* 표현식 노드의 생성 정합성이 통일되므로 Candidate 추출 및 GraphRule 평가 결과의 일관성이 향상됩니다.
