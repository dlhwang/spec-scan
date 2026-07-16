# UOW-10-1: 관측성 및 진단 도구 추가

## Goal (목표)
그래프 생성 과정 중 발생하는 미지원 AST(Expression/Statement) 타입 및 중복 노드 충돌 이벤트를 조용히 넘기지 않고, 통계 및 진단 로그로 기록하여 개선 대상의 계측 및 관측 가능성을 추가한다.

## 1. 문제 증거
현재 `FactExpressionVisitor.java` 및 `FactMethodVisitor.java`는 지원하지 않는 문법이나 예외 상황을 만났을 때 단순히 `return null;`이나 `catch (RuntimeException ignored)`로 조용히 넘어가 버립니다.
이로 인해 커버리지 분석 실패 시 실패 원인이 AST 파싱 단계의 유실 때문인지, 엣지 누락 때문인지, 아니면 Candidate Rule 매칭 실패 때문인지 역추적하는 것이 매우 곤란합니다.

## 2. 지원 대상 코드 패턴
* Visitor가 인지하지 못하는 신규 Java 릴리즈 문법 표현식이나 기타 에러 발생 코드.

## 3. 현재 그래프
* 조용히 노드가 유실되고 원인 파악이 불가능함.

## 4. 기대 그래프
```text
(내부 진단 메트릭 축적)
- Unhandled AST Node Counts:
  - PatternExpr: 5
  - YieldStmt: 2
- Duplicate Node Alert:
  - SourceRange: User.java:L15-C5 has duplicate Return nodes.
```

## 5. 수정 책임 클래스
* [FactExpressionVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactExpressionVisitor.java)
* [FactMethodVisitor.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactMethodVisitor.java)
* [FactGraphAccumulator.java](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/src/main/java/io/atworks/specscan/analysis/support/fact/FactGraphAccumulator.java)

## 6. 비대상 범위
* 프로덕션 환경의 실제 동작 성능을 크게 저하시킬 수 있는 과도한 전역 객체 추적은 지양하며, 로깅 및 단순 카운터 지표 형태로 제공합니다.

## 7. 테스트 케이스
* 의도적으로 지원하지 않는 표현식(`PatternExpr` 등)을 흘려보낸 후, 진단 결과 데이터 구조체에 미지원 카운트가 정상 증가했는지 확인.

## 8. 완료 기준
* 미지원 AST 타입의 이름과 발생 빈도를 수집할 수 있는 진단 API가 추가됩니다.
* 동일 SourceRange에서 중복 ID 노드가 등록되려고 할 때 경고 로깅이나 예외 검출이 작동합니다.

## 9. 성능 및 호환성 영향
* 개발 및 테스트 단계에서는 상세 진단 로그를 활성화하고, 프로덕션 실행 시에는 오버헤드가 없도록 분기 구성이 필요합니다.
