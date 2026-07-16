# UOW-8-1: 선언 참조 및 Shadowing 개선

## Goal (목표)
변수나 필드를 읽는 `READS` 관계 연결 시 단순 이름 매칭과 줄 번호 비교를 넘어, Lexical Scope 구조(블록 스코프, 람다 파라미터 등)를 파악하고 필요한 경우 JavaParser Symbol Solver fall-back을 활용해 정확히 매칭한다.

## 1. 문제 증거
현재 `FactExpressionVisitor.java`의 `relateDeclaredOrigin`은 파일 전체의 노드를 돌면서 이름이 같고 줄 번호가 자신보다 앞에 있으면 선언 노드로 인식하는 Heuristic 방식을 사용합니다:
```java
for (FactNode declaration : acc.nodes()) {
    ...
    if (!rootName.equals(declaredName) || ...) continue;
    int distance = reference.sourceRange().startLine() - declaration.sourceRange().startLine();
    ...
}
```
이 방식은 다음과 같은 상황에서 오매칭을 일으킵니다:
* 중첩 블록 내 동명의 다른 지역 변수.
* Lambda parameter 이름과 메서드 parameter의 이름 충돌.
* 파라미터나 지역 변수가 상위 클래스 필드명을 Shadowing(가림)하는 경우.

이로 인해 잘못된 `READS` 엣지가 생성되어, 검증 대상 변수 분석에 오류가 생깁니다.

## 2. 지원 대상 코드 패턴
```java
public void update(String name) {
    if (name == null) { ... } // name 파라미터 참조
    users.forEach(name -> { 
        if (name == null) { ... } // 람다 파라미터 name 참조 (Shadowing)
    });
}
```

## 3. 현재 그래프
* 람다 내부의 `name` 참조가 바깥쪽 메서드의 `name` 파라미터 선언 노드와 잘못 연결될 수 있음.

## 4. 기대 그래프
* 람다 내부의 `name` 참조는 람다 파라미터 `name` 선언과 연결되고, 메서드 수준의 `name` 참조는 메서드 파라미터 `name` 선언과 올바르게 격리되어 연결됨.

## 5. 수정 책임 클래스
* [FactExpressionVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java)

## 6. 비대상 범위
* 전체 프로젝트 차원의 클래스 의존성 추적은 제외하며, 단일 Java 파일(Lexical Scope) 내의 변수/필드 분석 정밀도 개선에만 한정합니다.

## 7. 테스트 케이스
* 변수 Shadowing이 발생하는 샘플 코드를 입력하여, 참조 노드가 상위 선언이 아닌 가장 인접하고 유효한 Lexical Scope 내의 선언 노드와 연결되는지 검증.

## 8. 완료 기준
* 동일 이름의 변수 선언이 여러 개 존재해도 올바른 Scope 내의 선언 노드로 `READS` 관계가 맺어집니다.
* 잘못 매칭된 `READS` 엣지로 인해 PredicateCandidate 분석 대상 필드가 오염되지 않습니다.

## 9. 성능 및 호환성 영향
* 파서 내에서 Scope 탐색 비용이 추가되나, 정밀한 분석을 위한 필수 개선 항목입니다.
