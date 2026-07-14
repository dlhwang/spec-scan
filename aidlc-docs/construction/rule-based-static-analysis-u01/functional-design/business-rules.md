# Unit 01 Fact Code Graph 규칙

## 사실성 규칙

### FGR-01 관찰 사실만 저장

Fact Graph node와 edge에는 AST, source location, resolver 결과에서 직접 얻은 정보만 저장한다. `AUTHENTICATION`, `PERMISSION_CHECK`, `STATE_CHECK`와 같은 해석은 금지한다.

### FGR-02 resolved 정보 위조 금지

simple name이나 snippet 추측값을 resolved type 또는 signature로 저장하지 않는다. resolver 실패 시 상태를 `UNRESOLVED` 또는 `PARTIAL`로 기록한다.

### FGR-03 문자열 재파싱 금지

구조 추출 후 snippet 문자열을 다시 검색하거나 파싱해 operand, receiver, branch 의미를 복원하지 않는다.

## Identity 규칙

### FGR-04 결정적 node ID

node ID canonical input은 다음으로 구성한다.

- relative source path
- owner method identity
- AST kind
- start/end line과 column
- semantic role

읽기 가능한 node type prefix와 canonical input hash를 결합한다. 동일 입력의 반복 분석은 동일 ID를 생성해야 한다.

### FGR-05 위치 충돌 방지

같은 source range를 공유하는 wrapper/operand 노드는 semantic role로 구분한다. hash 충돌이 탐지되면 조용히 덮어쓰지 않고 build failure diagnostic을 남긴다.

## 구조 규칙

### FGR-06 operand 순서 보존

method argument와 논리·비교 operand는 index 또는 `LEFT`, `RIGHT`, `RECEIVER` role로 순서를 보존한다.

### FGR-07 branch 극성 보존

then과 else outcome을 서로 다른 edge type으로 기록한다. 논리 부정은 snippet이 아니라 `ConditionPayload` 및 operand 관계로 보존한다.

### FGR-08 외부 호출 사실 보존

외부 구현을 방문하지 않더라도 호출 노드와 확인 가능한 signature, receiver, argument edge는 기록한다.

## Traversal 규칙

### FGR-09 독립 복합 budget

신규 builder는 legacy builder budget을 공유하지 않는다. 다음 세 한도를 모두 적용한다.

- max depth 5
- API별 방문 메서드 100
- API별 edge 300

### FGR-10 내부 방문 범위

프로젝트 소스와 애플리케이션 기본 패키지 안에 있으며 API에서 도달 가능하고 조건·반환·예외 흐름과 관련된 메서드만 본문 방문 대상으로 한다.

### FGR-11 기본 제외 범위

다음 본문은 기본적으로 방문하지 않는다.

- JDK 및 외부 라이브러리
- Spring Framework 내부
- proxy 및 generated code
- Repository 구현체 내부
- 단순 getter/setter
- 명백한 value object 접근자

호출 사실 기록은 이 제외 규칙의 영향을 받지 않는다.

### FGR-12 순환 차단

현재 traversal path와 API scope visited method identity를 함께 사용한다. 재귀 호출 edge는 기록할 수 있지만 본문 재진입은 금지한다.

### FGR-13 truncation 공개

budget 중단은 조용히 누락하지 않는다. diagnostic에는 API method, reason, configured budget, observed count를 기록한다.

허용 reason은 최소 다음을 포함한다.

- `MAX_DEPTH_EXCEEDED`
- `MAX_VISITED_METHODS_EXCEEDED`
- `MAX_EDGES_EXCEEDED`

## 정합성 규칙

### FGR-14 dangling edge 금지

모든 edge endpoint는 같은 graph에 존재해야 한다.

### FGR-15 immutable 결과

완성된 graph의 node, edge, diagnostic collection은 방어적 복사 후 변경할 수 없어야 한다.

## Property-Based Testing 후보

- 동일 소스와 설정에서 node ID 집합이 동일하다.
- 모든 생성 graph에는 dangling edge가 없다.
- edge 수는 configured max를 초과하지 않는다.
- 방문 메서드 수는 configured max를 초과하지 않는다.
- 동일 input에 graph normalization을 반복해도 결과가 동일하다.

