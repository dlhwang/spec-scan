# Unit 01 Fact Code Graph 비즈니스 로직 모델

## 목적

Java 소스와 타입 해석 결과에서 관찰 가능한 사실을 추출해 의미 해석이 포함되지 않은 `FactCodeGraph`를 생성한다. 기존 `ValidationEvidenceGraph`는 유지하며 신규 그래프를 병렬 생성한다.

## 입력

- 분석 대상 `RepositorySource`
- 정적 스캔에서 식별된 `ApiEndpoint`
- JavaParser AST와 타입 해석기
- `FactGraphTraversalBudget`
- 애플리케이션 기본 패키지 목록

## 출력

`FactGraphBuildResult`는 다음을 포함한다.

- API별 또는 전체 `FactCodeGraph`
- traversal 통계
- budget 및 타입 해석 diagnostic
- API별 truncation 상태

## 처리 흐름

### 1. API scope 초기화

- API method를 depth 0의 root로 등록한다.
- method의 resolved signature를 얻을 수 있으면 canonical owner identity로 사용한다.
- 타입 해석이 실패하면 소스 선언 경로와 method declaration signature로 fallback identity를 구성하되 resolved 값으로 표시하지 않는다.

### 2. 메서드 사실 추출

각 방문 메서드에서 다음 AST 사실을 추출한다.

- parameter 선언
- local variable 선언과 단순 initializer
- field access
- Enum constant access
- method call과 receiver 및 argument
- `if` condition과 operand 구조
- 직접 `throw` 및 `return` outcome

이 단계에서는 조건을 인증, 권한, 상태 또는 존재 검증으로 분류하지 않는다.

### 3. 조건식 구조 보존

- `!`, `&&`, `||`, 비교 연산자를 operand tree로 보존한다.
- `ConditionPayload`는 root operator와 AST kind를 가진다.
- 각 operand는 별도 노드이며 `OPERAND_OF` edge에 operand index 또는 role을 기록한다.
- then/else의 직접 outcome은 `THEN_OUTCOME`, `ELSE_OUTCOME`으로 구분한다.
- 중첩 branch는 상위 조건과 하위 조건의 control 관계를 유지한다.

### 4. 호출 사실과 traversal 분리

모든 확인 가능한 호출은 `METHOD_CALL` 노드로 기록한다. 내부 구현을 방문할지는 별도 정책으로 결정한다.

내부 방문 후보는 다음 조건을 모두 만족해야 한다.

- 선언 소스가 현재 분석 프로젝트에 존재함
- 선언 타입이 애플리케이션 기본 패키지에 속함
- 현재 API root에서 도달 가능함
- 조건, 반환 또는 예외 흐름과 관련된 경로임
- 명시적 제외 대상이 아님

방문하지 않는 외부 호출도 receiver type, method name, resolved signature, argument 관계는 가능한 범위에서 기록한다.

### 5. 복합 Traversal Budget 적용

기본값은 다음과 같다.

| 항목 | 기본값 | 의미 |
|:---|---:|:---|
| maxDepth | 5 | API root를 0으로 한 최대 내부 호출 깊이 |
| maxVisitedMethodsPerApi | 100 | API 하나에서 방문 가능한 선언 메서드 수 |
| maxEdgesPerApi | 300 | API 하나에서 생성 가능한 edge 수 |

어느 한도든 도달하면 추가 내부 방문 또는 edge 추가를 안전하게 중단한다. 이미 관찰한 사실은 유지하고 API scope에 truncation diagnostic을 추가한다.

### 6. 순환 차단

- 현재 traversal path의 method identity로 직접·간접 재귀를 차단한다.
- API scope 전체 visited set으로 같은 메서드의 중복 본문 분석을 방지한다.
- 동일 메서드로 향하는 각각의 호출 사실은 유지할 수 있지만 본문은 한 번만 분석한다.

### 7. 결과 정합성 검증

- 모든 edge의 source와 target node가 존재해야 한다.
- node ID는 그래프 안에서 유일해야 한다.
- `RESOLVED` 타입 정보에는 실제 resolver 결과가 있어야 한다.
- truncation이 발생하면 최소 하나의 budget diagnostic이 있어야 한다.

## 오류 및 부분 성공

- 개별 type resolution 실패는 전체 build 실패가 아니다.
- 파싱할 수 없는 소스 파일은 diagnostic으로 남기고 다른 API 분석을 계속한다.
- budget 초과는 오류가 아니라 명시적 partial result다.
- root API method 자체를 식별할 수 없으면 해당 API 결과만 실패 diagnostic으로 반환한다.

## 비목표

- 비즈니스 의미 분류
- 기존 evidence graph 교체
- 완전한 CFG와 SSA
- 외부 library 본문 분석
- 모든 helper를 통한 값의 완전한 interprocedural 추적

