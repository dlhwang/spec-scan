# Unit of Work Index

이 문서는 `Fact Visitor 기반 PredicateCandidate 추출 커버리지 개선 계획`을 수행하기 위해 세분화된 Unit of Work(UOW)들을 관리하는 총괄 인덱스입니다.

모든 작업은 커밋 단위로 실행 가능하도록 설계되었으며, 상세한 구현 가이드는 각 UOW 문서에서 확인할 수 있습니다. 전체 진행 상황은 프로젝트 루트의 [construction.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/construction.md)를 통해 추적됩니다.

## UOW 로드맵 및 목표 (Goal)

| UOW ID | 세부 작업명 | 목표 (Goal) | 상태 | 상세 문서 |
| :--- | :--- | :--- | :--- | :--- |
| **UOW-1-1** | RETURN 노드 정체성 통합 | 동일 `ReturnStmt`가 조건/메서드 수준에서 중복 생성되는 문제를 방지하고 단일 노드로 일관화 | TODO | [UOW-1-1-return-identity.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-1-1-return-identity.md) |
| **UOW-1-2** | THROW 노드 정체성 통합 | 동일 `ThrowStmt`가 조건/메서드 수준에서 중복 생성되는 문제를 방지하고 단일 노드로 일관화 | TODO | [UOW-1-2-throw-identity.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-1-2-throw-identity.md) |
| **UOW-2-1** | THROW 전체 수집 도입 | 특정 제어문(if/switch 등) 아래에 직접 존재하는 throw뿐만 아니라 범위 내의 모든 `ThrowStmt`를 수집 | TODO | [UOW-2-1-throw-traversal.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-2-1-throw-traversal.md) |
| **UOW-2-2** | THROW 예외 표현식 연결 | 수집된 THROW 노드의 예외 생성자 인자, 타입 정보를 하위 그래프로 재귀 연결 | TODO | [UOW-2-2-throw-expression.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-2-2-throw-expression.md) |
| **UOW-3-1** | RETURN 표현식 연결 일관화 | 조건 분기 등의 Outcome으로 수집된 RETURN 노드에 대해서도 반환 표현식 하위 그래프가 누락 없이 균일하게 연결되도록 수정 | TODO | [UOW-3-1-return-expression.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-3-1-return-expression.md) |
| **UOW-4-1** | 지역 변수 Predicate 보존 | 조건 판단식이 지역 변수에 임시 할당되었다가 활용될 때, 해당 변수 선언 시점의 조건식 operand(&&, \|\| 등)를 재귀적으로 추적하여 보존 | TODO | [UOW-4-1-assigned-predicate.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-4-1-assigned-predicate.md) |
| **UOW-5-1** | Lambda 실행 범위 분리 | Lambda 내부의 RETURN/THROW Outcome이 외부 Method scope의 Outcome과 혼합되지 않도록 바운더리 구분 및 파라미터 연결 | TODO | [UOW-5-1-lambda-traversal.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-5-1-lambda-traversal.md) |
| **UOW-6-1** | 필수 Expression 지원 확대 | `ConditionalExpr`(? :), `InstanceOfExpr`, `CastExpr` 등 값 검증 코드에서 다수 쓰이는 표현식 방문 및 노드 매핑 지원 | TODO | [UOW-6-1-expression-coverage.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-6-1-expression-coverage.md) |
| **UOW-7-1** | Switch 처리 분리 | Switch statement와 Switch expression의 처리 방식을 완전히 분리하고, YieldStmt 및 case 조건 연동 완성 | TODO | [UOW-7-1-switch-traversal.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-7-1-switch-traversal.md) |
| **UOW-8-1** | 선언 참조 및 Shadowing 개선 | 이름 매칭 중심의 선언부 바인딩에서 Lexical Scope 우선 검토와 JavaParser Symbol Solver 연동 fall-back을 통한 READS 관계 정밀화 | TODO | [UOW-8-1-shadowing-resolution.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-8-1-shadowing-resolution.md) |
| **UOW-9-1** | PredicateCandidate Seed 확장 | explicit throw/return이 없는 `Objects.requireNonNull`, `Preconditions.checkArgument` 등 validation sink성 호출도 후보 시드로 자동 추출 | TODO | [UOW-9-1-candidate-seed.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-9-1-candidate-seed.md) |
| **UOW-10-1** | 관측성 및 진단 도구 추가 | 그래프 빌드 과정 중의 미지원 AST 타입 통계 및 중복 노드 검출 진단 로직 추가 | TODO | [UOW-10-1-visitor-diagnostics.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-10-1-visitor-diagnostics.md) |
| **UOW-11-1** | 테스트 하네스 구축 | 코드 변경이 기존 그래프 형상에 미치는 사이드 이펙트를 통제하기 위한 Golden Graph 및 Candidate Extraction 테스트 자동화 체계 완비 | TODO | [UOW-11-1-test-harness.md](file:///C:/Users/Administrator/.gemini/antigravity/worktrees/auto-oas/codex-rulelayer-dev/docs/uow/UOW-11-1-test-harness.md) |
