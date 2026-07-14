# Unit 01 Fact Code Graph NFR Requirements

## 범위

Fact Code Graph domain model, 결정적 ID 생성, AST 기반 graph builder, 복합 traversal budget 및 diagnostic에 적용한다. 기존 `ValidationEvidenceGraph` 경로에는 동작 변경을 요구하지 않는다.

## 성능 및 용량

### NFR-FG-01 Bounded Traversal

API 하나의 내부 메서드 탐색은 다음 기본 한도를 동시에 적용한다.

| 항목 | 기본값 | 한도 도달 시 동작 |
|:---|---:|:---|
| maxDepth | 5 | 추가 내부 호출 방문 중단 |
| maxVisitedMethodsPerApi | 100 | 추가 메서드 본문 분석 중단 |
| maxEdgesPerApi | 300 | 추가 edge 생성 중단 |

각 값은 1 이상의 설정값으로 교체할 수 있어야 한다.

### NFR-FG-02 예측 가능한 중단

- 한도를 초과해도 이미 생성한 node와 edge를 보존한다.
- API 하나의 중단이 다른 API graph 생성을 중단시키지 않는다.
- observed count는 configured limit을 초과하지 않아야 한다.
- wall-clock SLA는 초기 PoC에서 강제하지 않고 fixture별 build duration을 Evidence로 기록한다.

### NFR-FG-03 외부 본문 제외

JDK, Spring, 외부 library, generated code, repository 구현 본문은 기본 방문에서 제외해 graph 폭증을 방지한다. 호출 node와 확인 가능한 signature는 유지한다.

## 신뢰성 및 부분 성공

### NFR-FG-04 API 단위 실패 격리

- parse 또는 resolution 실패는 관련 API의 diagnostic으로 제한한다.
- root API를 생성할 수 없는 경우 해당 API만 실패 처리한다.
- 다른 API의 완전 결과를 손상시키지 않는다.

### NFR-FG-05 Truncation 투명성

budget 중단 결과에는 다음 정보가 필수다.

- API method identity
- `truncated=true`
- 중단 reason
- configured budget
- observed traversal stats
- 가능한 source range

### NFR-FG-06 순환 안전성

직접 재귀와 간접 순환 모두 traversal path와 visited method identity로 차단한다. 호출 사실은 기록할 수 있으나 본문을 재방문하지 않는다.

## 결정성 및 재현성

### NFR-FG-07 결정적 Identity

동일 source, 설정, source root에서 반복 실행한 node ID와 edge ID 집합은 동일해야 한다.

ID는 다음 canonical input을 사용한다.

- relative path
- owner method identity
- AST kind
- source range
- semantic role

### NFR-FG-08 Immutable Result

완성된 graph와 diagnostic collection은 외부 변경으로 변하지 않아야 한다. 생성자가 방어적 복사를 수행한다.

### NFR-FG-09 Graph Integrity

- duplicate node ID 0건
- dangling edge 0건
- invalid node type과 payload 조합 0건
- `RESOLVED` 상태의 근거 없는 resolved field 0건

## 관찰 가능성

### NFR-FG-10 Diagnostic Code 안정성

최소 다음 code를 안정된 계약으로 제공한다.

- `MAX_DEPTH_EXCEEDED`
- `MAX_VISITED_METHODS_EXCEEDED`
- `MAX_EDGES_EXCEEDED`
- `TYPE_RESOLUTION_FAILED`
- `SOURCE_PARSE_FAILED`
- `NODE_ID_COLLISION`
- `API_ROOT_UNRESOLVED`

### NFR-FG-11 최소 Evidence

diagnostic은 전체 source dump를 저장하지 않는다. relative path, source range, 제한된 snippet 또는 details만 보존한다.

## 보안 및 안전

### NFR-FG-12 Static Analysis Only

대상 repository의 build, test, run, annotation processor 또는 임의 script를 실행하지 않는다.

### NFR-FG-13 경로 최소화

결과에는 분석 workspace 절대 경로 대신 repository 기준 상대 경로를 사용한다.

## 테스트 품질

### NFR-FG-14 Example-Based Tests

다음 구체 사례를 JUnit assertion으로 고정한다.

- boolean method guard
- 이름을 바꾼 동형 guard
- then/else branch polarity
- local alias
- type resolution failure
- 세 budget 한도
- 직접 및 간접 순환

### NFR-FG-15 Property-Based Tests

jqwik를 사용해 최소 다음 property를 검증한다.

- 동일 canonical input은 동일 ID를 생성한다.
- 서로 다른 semantic role 또는 source position은 다른 ID를 생성한다.
- 유효 graph에는 dangling edge가 없다.
- graph collection은 생성 후 외부 collection 변경의 영향을 받지 않는다.
- traversal stats는 configured budget을 넘지 않는다.

### NFR-FG-16 재현 가능한 PBT

- jqwik shrinking을 비활성화하지 않는다.
- 실패 시 seed와 최소 failing input을 test output에 남긴다.
- PBT는 일반 `gradlew test`에 포함한다.
- PBT에서 발견된 결함은 축소된 입력을 example-based regression으로 추가한다.

## PBT Compliance

| Rule | 상태 | 적용 내용 |
|:---|:---|:---|
| PBT-01 | Compliant | ID 결정성, graph integrity, budget invariant 식별 |
| PBT-02 | Planned | 의미 있는 source range와 role generator 사용 |
| PBT-03 | Planned | identity와 graph invariant 검증 |
| PBT-04 | Planned | 동일 입력 ID 생성의 idempotency 검증 |
| PBT-05 | N/A | 별도 reference algorithm이 없음 |
| PBT-06 | N/A | mutable state machine이 아닌 immutable graph 생성 |
| PBT-07 | Planned | domain-specific SourceRange와 payload generator 제공 |
| PBT-08 | Planned | shrinking 및 seed 재현 유지 |
| PBT-09 | Compliant | jqwik 1.7.4 선택 |
| PBT-10 | Compliant | example-based test와 PBT 병행 |

## 초기 수용 기준

- 기본 budget과 사용자 지정 budget 테스트 통과
- PBT property 전체 통과
- 동일 fixture 반복 분석 결과 ID 집합 일치
- dangling edge 및 duplicate node ID 0건
- budget 중단 fixture에서 diagnostic 100% 제공
- 기존 전체 테스트 regression 0건

