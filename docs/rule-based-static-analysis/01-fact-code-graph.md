# Work Unit 01: Fact Code Graph 구축

## 목적

소스에서 관찰한 사실과 비즈니스 의미를 분리한다. 기존 `BUSINESS_RULE` 중심 그래프를 즉시 삭제하지 않고, 신규 Fact Code Graph를 병렬로 구축한다.

## 설계 결정

Fact Code Graph에는 `AUTHENTICATION`, `PERMISSION_CHECK`, `STATE_CHECK`와 같은 의미를 저장하지 않는다. 이런 의미는 Unit 03 이후 Graph Rule이 생성한다.

## 최소 노드 모델

```java
enum FactNodeType {
    API_METHOD,
    METHOD,
    PARAMETER,
    LOCAL_VARIABLE,
    FIELD_ACCESS,
    ENUM_CONSTANT,
    METHOD_CALL,
    CONDITION,
    THROW,
    RETURN
}
```

필요하면 request field와 domain field는 별도 노드 타입 대신 `FIELD_ACCESS`의 선언 타입과 root origin으로 구분한다. 동일 사실을 중복 노드로 만들지 않는다.

## 최소 엣지 모델

```java
enum FactEdgeType {
    CALLS,
    HAS_ARGUMENT,
    RECEIVER_OF,
    READS,
    ASSIGNED_FROM,
    COMPARES_WITH,
    OPERAND_OF,
    CONTROLS,
    THEN_OUTCOME,
    ELSE_OUTCOME,
    ORIGINATES_FROM
}
```

분기 극성은 edge evidence 문자열이 아니라 타입이 있는 구조로 표현한다.

## 노드 공통 속성

- 안정적인 node ID
- source file relative path
- 시작 및 종료 line/column
- 원본 snippet
- resolved type 또는 resolved signature
- type resolution 상태
- AST kind

노드마다 필요 속성이 다르면 하나의 거대한 nullable record 대신 타입별 payload 또는 sealed hierarchy를 고려한다.

## 추출 순서

1. API 및 도달 가능한 메서드 식별
2. 조건식과 분기 outcome 추출
3. 조건식 operand tree 보존
4. method call의 receiver, signature, arguments 연결
5. field access의 root와 선언 타입 연결
6. 단순 지역 변수 별칭의 origin 연결
7. source location 저장

## 분기 모델 예시

```java
if (!passwordEncoder.matches(request.password(), member.password())) {
    throw new AuthenticationFailedException();
}
```

그래프에는 다음 사실만 있어야 한다.

- 조건은 `!` 연산자다.
- 피연산자는 `matches` 호출이다.
- 호출 receiver의 resolved type 정보가 있다.
- 두 인자의 origin이 각각 요청 필드와 도메인 필드다.
- 조건의 true branch가 예외 발생을 제어한다.

그래프 생성 단계에서는 이를 인증 실패라고 부르지 않는다.

## 변경 예상 파일

- 신규 graph domain model 파일
- `ValidationEvidenceGraphBuilder` 또는 별도 `FactCodeGraphBuilder`
- 타입 해석 지원 코드
- graph builder 단위 테스트

기존 그래프가 다른 출력에 사용된다면 신규 builder를 분리하고 adapter를 둔다.

## 완료 기준

- 조건식, 호출, 인자, 필드, Enum, throw/return을 구조적으로 질의할 수 있다.
- then/else outcome과 논리 부정을 잃지 않는다.
- 동일한 소스 입력에서 node ID가 안정적이다.
- 그래프 자체에 비즈니스 의미 분류가 없다.
- 비밀번호 예제와 이름을 바꾼 boolean method 예제의 사실 구조가 동일한 방식으로 표현된다.
- 타입 해석 실패가 예외로 숨겨지지 않고 상태로 남는다.

## 비목표

- 비즈니스 룰 판정
- 완전한 CFG 및 SSA
- 임의 깊이의 interprocedural value flow
- 기존 출력 제거

## 위험과 대응

- AST 문자열을 node label에 저장하고 다시 파싱하는 우회 구현을 금지한다.
- edge 수 증가로 탐색 비용이 커질 수 있으므로 메서드 및 호출 깊이 budget을 유지한다.
- JavaParser resolution 실패 시 simple name을 resolved name처럼 저장하지 않는다.

