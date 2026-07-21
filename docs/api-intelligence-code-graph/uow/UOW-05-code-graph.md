# UOW-05 API별 Shallow Code Graph

## Goal

발견된 API 하나를 root로 Controller, 직접 호출 Service, DTO, Validation, 조건, 예외 및 반환 관계의 독립 그래프를 생성한다.

## 선행 조건

UOW-01, UOW-02, UOW-04 완료.

## 허용 파일

- `src/main/java/io/atworks/apiintelligence/port/out/CodeGraphPort.java`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/**Graph*`
- `src/main/java/io/atworks/apiintelligence/adapter/javaparser/SymbolResolutionFallback.java`
- graph fixture와 해당 테스트

## 필수 계약

- node: API, METHOD, TYPE, FIELD, VALIDATION, CONDITION, EXCEPTION
- edge: DECLARES, CALLS, USES_TYPE, BINDS_REQUEST, VALIDATES, CHECKS, RETURNS, THROWS
- Controller에서 직접 호출하는 Service까지만 보장한다.
- DTO field와 Bean Validation, `if`/`else if`/`switch`, 직접 `throw`/`return`을 수집한다.
- graph는 반드시 단일 apiId에 귀속된다.
- depth 8, methods 100, edges 2000 기본 한도를 설정에서 받는다.
- 초과 시 `GRAPH_TRUNCATED`; 조용한 생략 금지
- symbol 해석이 유일하지 않으면 edge를 만들지 않고 `SYMBOL_RESOLUTION_AMBIGUOUS`
- node/edge/attributes는 stable order이며 source-derived node는 가능한 source location을 가진다.

## 구현 절차

1. API root와 handler method node를 만든다.
2. request/response type, fields, validation을 연결한다.
3. direct call receiver를 symbol solver로 해석하고 제한된 syntax fallback을 적용한다.
4. method body 조건, 예외, return을 방문한다.
5. 한도와 cycle을 검사하고 graph integrity를 검증한다.

## 테스트와 완료 조건

- direct service call, DTO/validation, if/switch/throw/return
- ambiguous receiver에는 fabricated edge가 없음
- cycle과 모든 한도, dangling edge 거부
- fixture API별 독립 graph와 stable ordering
- DB 의미, reflection, 깊은 전역 call graph를 추론하지 않음

