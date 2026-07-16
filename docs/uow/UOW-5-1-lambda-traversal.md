# UOW-5-1: Lambda 실행 범위 분리

## Goal (목표)
Lambda 표현식 내부에 존재하는 `ReturnStmt` 및 `ThrowStmt` Outcome들이 외부 Method scope의 Outcome과 섞여 잘못 연결되는 문제를 해결하고, Lambda 노드 하위에 전용 스코프를 유지하며 수집한다.

## 1. 문제 증거
현재 `FactMethodVisitor.java`의 `ReturnStmt` 전체 순회(findAll)와 `ThrowStmt` 수집은 메서드 전체에서 하위 Lambda 구분 없이 작동합니다.
그 결과 Lambda 내부에서 반환하는 결과나 예외가 바깥쪽 메서드의 반환 결과로 오분류되어 그래프가 뒤섞입니다:
```java
return users.stream()
    .filter(user -> {
        if (user.isDeleted()) {
            return false; // Lambda의 RETURN
        }
        return true;     // Lambda의 RETURN
    })
    .toList(); // 바깥 메서드의 RETURN
```
이 코드에서 Lambda 내부의 `return false`가 메서드 전체 결과인 것처럼 `METHOD → RETURNS → RETURN`으로 연결되는 문제가 발생합니다.

## 2. 지원 대상 코드 패턴
```java
list.forEach(x -> {
    if (x == null) throw new IllegalArgumentException();
});
```

## 3. 현재 그래프
```text
METHOD
  ├─ RETURNS → RETURN (list.stream...)
  └─ RETURNS → RETURN (false) -- (Lambda 내부 return이 외부로 노출)
```

## 4. 기대 그래프
```text
METHOD
  └─ RETURNS → RETURN (list.stream...)

LAMBDA (user -> { ... })
  ├─ PARAMETER (user)
  ├─ RETURNS → RETURN (false)
  └─ RETURNS → RETURN (true)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)

## 6. 비대상 범위
* 익명 클래스(Anonymous Class) 내부 메서드는 본 UOW 범위에서 제외하며, 순수 `LambdaExpr`만 격리 스코프로 취급합니다.

## 7. 테스트 케이스
* Lambda 표현식과 중첩 If가 혼재된 코드를 파싱하여, Lambda 내부의 `RETURN` 노드가 외부 `METHOD` 노드와 직접적인 `RETURNS` 엣지로 연결되지 않음을 검증.
* Lambda 파라미터가 Collection Source와 올바르게 `ORIGINATES_FROM`으로 엣지 연동되는지 검증.

## 8. 완료 기준
* Lambda 실행 범위 내에서 생성된 모든 Outcome 노드는 오직 `LAMBDA` 노드에만 귀속 관계를 가집니다.
* 외부 메서드의 `RETURNS` 엣지는 Lambda 내부의 `ReturnStmt`를 참조하지 않습니다.

## 9. 성능 및 호환성 영향
* 불필요하게 꼬여 있던 엣지가 제거되어, GraphRule이 Candidate 평가 시 잘못된 Outcome을 감지하여 발생시키는 오탐(False Positive)을 차단합니다.
