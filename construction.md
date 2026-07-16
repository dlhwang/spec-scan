# Construction Progress Tracker

이 문서는 `Fact Visitor 기반 PredicateCandidate 추출 커버리지 개선 계획`에 따라 세분화된 Unit of Work(UOW)들을 실제로 구현하고 테스트 및 검증하는 과정을 실시간으로 체크하는 진행률 관리 문서입니다.

모든 작업은 본 문서의 체크리스트를 업데이트하며 단계적으로 진행됩니다.

## 1. 전체 작업 진행률

- [x] UOW-1-1: RETURN 노드 정체성 통합
- [x] UOW-1-2: THROW 노드 정체성 통합
- [x] UOW-2-1: THROW 전체 수집 도입
- [x] UOW-2-2: THROW 예외 표현식 연결
- [x] UOW-3-1: RETURN 표현식 연결 일관화
- [x] UOW-4-1: 지역 변수 Predicate 보존
- [x] UOW-5-1: Lambda 실행 범위 분리
- [x] UOW-6-1: 필수 Expression 지원 확대
- [x] UOW-7-1: Switch 처리 분리
- [x] UOW-8-1: 선언 참조 및 Shadowing 개선
- [x] UOW-9-1: PredicateCandidate Seed 확장
- [x] UOW-10-1: 관측성 및 진단 도구 추가
- [x] UOW-11-1: 테스트 하네스 구축

---

## 2. 세부 진행 단계 및 체크리스트

### [x] UOW-1-1: RETURN 노드 정체성 통합
- **목표(Goal)**: 중복 RETURN 노드 생성 방지 및 식별 규칙 단일화
- [x] `FactMethodVisitor` 내 `ReturnStmt` 탐색 및 생성 책임을 단일 메서드로 통합
- [x] `IfStmt` / `SwitchStmt` 의 분기 처리에서 기존 Outcome 노드를 조회하여 엣지만 추가하는 방식으로 변경
- [x] 단위 테스트 작성 및 기존 테스트 통과 여부 검증

### [x] UOW-1-2: THROW 노드 정체성 통합
- **목표(Goal)**: 중복 THROW 노드 생성 방지 및 식별 규칙 단일화
- [x] `FactMethodVisitor` 내 `ThrowStmt` 탐색 및 생성 책임을 단일 메서드로 통합
- [x] Outcome 중복 등록 여부를 판단하는 ID Generator 정밀화
- [x] 단위 테스트 작성 및 기존 테스트 통과 여부 검증

### [x] UOW-2-1: THROW 전체 수집 도입
- **목표(Goal)**: 특정 제어문 외의 전체 THROW 문 수집 구조 도입
- [x] `FactMethodVisitor` 내에서 `method.findAll(ThrowStmt.class)`를 사용한 일괄 수집/등록 경로 추가
- [x] nested switch, try-catch, loop 안의 throw가 누락 없이 수집되는지 검증
- [x] 단위 테스트 작성

### [x] UOW-2-2: THROW 예외 표현식 연결
- **목표(Goal)**: throw 예외 객체의 생성 인자 및 타입 연결
- [x] `ThrowStmt.getExpression()`을 `FactExpressionVisitor`로 전달하는 로직 구현
- [x] `ObjectCreationExpr` 파싱을 통한 예외 타입과 생성자 인자 간 `OPERAND_OF` 엣지 연결
- [x] 단위 테스트 작성

### [x] UOW-3-1: RETURN 표현식 연결 일관화
- **목표(Goal)**: 모든 RETURN 노드와 반환 표현식 하위 그래프 연결 일관화
- [x] `ReturnStmt`가 Outcome으로 등록될 때 `ReturnStmt.getExpression()`을 일괄 처리하여 하위 표현식과 항상 연동되도록 수정
- [x] 반환값의 엣지(`OPERAND_OF` 등) 누락 케이스 제거 및 단위 테스트 검증

### [x] UOW-4-1: 지역 변수 Predicate 보존
- **목표(Goal)**: 지역 변수에 저장된 Boolean 조건식 구조 재귀 보존
- [x] `visitAssigned` 메서드가 복합 표현식의 하위 구조를 재귀적으로 방문해 operand 노드들을 보존하도록 수정
- [x] `READS` 엣지를 통한 변수 선언 시점의 조건식 역추적 검증
- [x] 단위 테스트 작성

### [x] UOW-5-1: Lambda 실행 범위 분리
- **목표(Goal)**: Lambda 내부 Outcome 수집 오염 방지 및 스코프 분리
- [x] Lambda 내부의 `RETURN` / `THROW`가 외부 Method scope에 병합되지 않고 Lambda node 하위 scope에 분리 수집되도록 수정
- [x] Lambda parameter와 collection source 간 `ORIGINATES_FROM` 연결 보장
- [x] 단위 테스트 작성

### [x] UOW-6-1: 필수 Expression 지원 확대
- **목표(Goal)**: `ConditionalExpr`, `InstanceOfExpr` 등 검증 표현식 지원
- [x] `ConditionalExpr`, `InstanceOfExpr`, `CastExpr`, `ArrayAccessExpr` 등의 파싱 로직 추가
- [x] 각 표현식이 적절한 NodeType과 Edge를 가지도록 `FactExpressionVisitor` 리팩토링
- [x] 단위 테스트 작성

### [x] UOW-7-1: Switch 처리 분리
- **목표(Goal)**: Switch Statement와 Expression 분리 처리 및 YieldStmt 지원
- [x] `SwitchStmt`와 `SwitchExpr`의 방문 로직 분리
- [x] Switch Expression의 YieldStmt 또는 expression result가 올바른 RETURN 노드와 연동되도록 구현
- [x] 단위 테스트 작성

### [x] UOW-8-1: 선언 참조 및 Shadowing 개선
- **목표(Goal)**: 변수 Shadowing 방지 및 lexical scope 기반 바인딩 정밀화
- [x] `relateDeclaredOrigin`에서 lexical scope를 먼저 확인하고 충돌을 처리하는 Heuristic 보강
- [x] 필요한 경우 JavaParser Symbol Solver fall-back을 활용하여 정확한 READS 연동 구현
- [x] 단위 테스트 작성

### [x] UOW-9-1: PredicateCandidate Seed 확장
- **목표(Goal)**: Objects.requireNonNull 등 validation sink 기반 시드 수집
- [x] 직접적인 throw/return이 없는 검증용 guard API(ex: Objects.requireNonNull 등)를 감지하여 PredicateCandidate seed로 등록
- [x] 무분별한 candidate 확장을 막는 qualified method signature 필터링 추가

### [x] UOW-10-1: 관측성 및 진단 도구 추가
- **목표(Goal)**: 미지원 AST 계측 및 중복 노드 진단 로그 추가
- [x] `FactGraphAccumulator` 또는 빌더 단에서 미지원 AST(Expression/Statement) 타입을 로깅/카운팅하는 진단 기능 개발
- [x] 중복 ID를 가진 노드 생성 시 경고 로깅 및 진단 인터페이스 구축

### [x] UOW-11-1: 테스트 하네스 구축
- **목표(Goal)**: Golden Graph Test 및 Candidate Extraction Test 구조 완성
- [x] 고정된 Java 소스를 기반으로 생성되는 Fact Graph의 JSON 결과 및 기대 구조를 검증하는 테스트 하네스 구현
- [x] 회귀 방지를 위한 Regression Corpus 검증 테스트 작성

---

## 3. 검증 환경 및 빌드 커맨드

* **프로젝트 빌드 및 테스트 실행**:
  ```powershell
  .\gradlew.bat clean test
  ```
