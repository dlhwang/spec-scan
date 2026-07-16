# UOW-2-1: THROW 전체 수집 도입

## Goal (목표)
특정 `if` 혹은 `switch` 제어문 하위에 직접 자식으로 있는 `ThrowStmt`만 수집되는 구조를 개선하여, 메서드 실행 범위 내의 모든 `ThrowStmt`를 누락 없이 일관적으로 수집해 그래프에 등록한다.

## 1. 문제 증거
현재 `FactMethodVisitor.java`는 `IfStmt`와 `SwitchStmt`를 만났을 때만 하위 자식을 확인하여 `ThrowStmt`를 제한적으로 수집합니다.
따라서 다음과 같이 중첩 구문이나 loop, try-catch 내에 존재하는 `ThrowStmt`는 수집 대상에서 누락됩니다.
```java
while (iterator.hasNext()) {
    if (invalid(iterator.next())) {
        throw new ValidationException(); // 수집되지 않음
    }
}
```

## 2. 지원 대상 코드 패턴
```java
while (condition) {
    if (check()) {
        throw new IllegalArgumentException();
    }
}
```

## 3. 현재 그래프
* `THROW` 노드가 아예 생성되지 않아 그래프상에서 예외를 던지는 흐름을 인지할 수 없음.

## 4. 기대 그래프
```text
METHOD
  └─ ORIGINATES_FROM (혹은 EXECUTED_OUTCOME) → THROW (ID: throw-id)
```

## 5. 수정 책임 클래스
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)

## 6. 비대상 범위
* Lambda 표현식 내부에 존재하는 `ThrowStmt`는 외부 메서드의 스코프와 분리해야 하므로 `UOW-5-1`에서 수집 책임을 조율합니다.
* 수집된 `THROW` 노드와 예외 객체 표현식 간의 상세 연결은 `UOW-2-2`에서 수행합니다.

## 7. 테스트 케이스
* loop(while/for) 또는 중첩 블록 내에 `ThrowStmt`가 단독으로 기재된 소스 코드를 파싱하여 `THROW` 노드가 정상 수집되는지 검증.
* try-catch 블록 내의 throw문이 수집되는지 검증.

## 8. 완료 기준
* 지원 대상 실행 범위(Executable Scope) 내의 모든 `ThrowStmt`가 예외 없이 그래프에 노드로 등록되어야 합니다.

## 9. 성능 및 호환성 영향
* 이전 버전에서 수집되지 않던 throw문이 추가되므로 그래프의 노드 개수가 소폭 증가합니다.
