# Code Graph 기반 API 테스트 조건 추출: 발표 내용 초안

> 이 문서는 발표용 HTML을 만들기 전, `auto-oas`의 코드와 커밋을 근거로 발표 내용을 정리한 초안이다.
> 화면 구성이나 디자인보다 먼저, 무엇을 사실로 말할지와 어떤 흐름으로 설명할지를 확정하는 데 목적이 있다.

## 0. 발표의 중심 주장

이 프로젝트를 가장 정확하게 설명하는 문장은 다음과 같다.

> `auto-oas`는 Spring 소스코드에서 API 호출 스펙뿐 아니라 실행 경로의 검증 조건까지 추출하기 위해, 하나의 Fact Code Graph 위에 규칙 기반 분석과 LLM 분석이 함께 동작하는 구조를 만들고 있다. YAML Rule PoC도 구현했지만, 설정 포맷을 확장하기 전에 공통 의미 모델과 검출 정확도를 먼저 안정화하는 방향으로 발전해 왔다.

이 방향은 **Progressive Externalization**으로 설명할 수 있다. 다만 이 프로젝트에서는 “나중에 처음으로 YAML을 만든다”는 뜻이 아니다.

- 2026-07-14에 사용자 YAML Rule의 로딩·검증·실행 PoC를 이미 구현했다.
- 초기 YAML Rule은 `BOOLEAN_CALL`, 메서드 이름 또는 signature, 실패 분기, category를 선언할 수 있다.
- 그러나 출력 제약은 `CONTROL_FLOW_ONLY` 중심이며, 실제 API 입력 경로와 실행 가능한 조건으로 연결하는 의미 해석은 제한적이다.
- 이후 개발의 중심은 YAML 문법 확대가 아니라 Fact Code Graph 통합, 후보 추출, 의미 분류, 출력 정규화로 이동했다.
- 현재 기본 실행 경로는 `InitialRulePacks.all()`의 Java built-in Rule Pack을 사용하며, YAML composer는 독립 PoC 및 테스트 수준으로 남아 있다.

따라서 발표에서 피해야 할 표현은 다음과 같다.

> 현재 YAML 룰 엔진은 아직 구현되지 않았다.

대신 다음처럼 설명하는 것이 정확하다.

> YAML Rule의 최소 실행 가능성은 검증했다. 그 과정에서 진짜 어려운 문제는 포맷이 아니라, 코드의 조건을 API 입력·외부 상태·응답 보장으로 정확히 해석하는 공통 의미 모델이라는 것을 확인했다. 현재는 이 기반을 안정화하고, 검증된 의미 매핑만 점진적으로 외부화할 수 있도록 경계를 재정리하고 있다.

---

## 1. 왜 이 프로젝트를 시작했는가

Spring Controller와 DTO의 어노테이션만 분석해도 다음 정보는 비교적 안정적으로 얻을 수 있다.

- HTTP method와 path
- PATH, QUERY, HEADER, BODY binding
- request/response DTO schema
- Bean Validation 조건
- 기본 response status와 type

하지만 API 테스트에 필요한 조건은 어노테이션에만 있지 않다. 실제 조건은 Controller 이후의 Service와 Domain 실행 경로에 숨어 있다.

```java
if (!passwordEncoder.matches(request.password(), member.password())) {
    throw new InvalidPasswordException();
}
```

```java
Order order = orderRepository.findById(orderNo)
    .orElseThrow(OrderNotFoundException::new);
```

```java
if (!order.isShippingChangeable()) {
    throw new IllegalStateException();
}
```

이 프로젝트의 목표는 이 코드를 단순히 문자열로 찾는 것이 아니라, API별로 다음 네 종류의 결과로 정규화하는 것이다.

| 결과 | 의미 | 활용 |
|---|---|---|
| `requestPreconditions` | 요청 값이 충족해야 할 실행 가능 조건 | negative/validation test 생성 |
| `responseAssertions` | 정상 응답이 보장해야 할 조건 | status, header, body 검증 |
| `excludedBusinessRules` | 외부 상태·권한·도메인 상태처럼 요청 값만으로 실행하기 어려운 조건 | 사전 데이터와 시나리오 설계 |
| `diagnostics` | 구조 또는 의미를 끝까지 증명하지 못한 후보 | 분석 품질 개선과 사용자 검토 |

핵심 메시지:

> 목표는 API 목록을 만드는 것이 아니라, 실제 실행 경로에서 테스트 가능한 계약과 테스트 준비에 필요한 비즈니스 조건을 추출하는 것이다.

---

## 2. 프로젝트가 실제로 거쳐 온 변화

발표에서는 기능 목록보다, 왜 구조가 지금의 형태가 되었는지를 시간순으로 보여주는 편이 설득력이 높다.

### 2.1 YAML Rule Pack PoC: 외부화 가능성 검증

관련 커밋:

- `4f55ece` — `feat: load and validate user YAML rules`
- `7e6c6c0` — `feat: execute user YAML rules with built-in packs`
- `5c8df4b` — `docs: define user YAML rule pack PoC`
- `d8f901b` — `docs: add user YAML rule examples`

이 단계에서는 다음을 검증했다.

- YAML 파일과 문자열에서 사용자 Rule 로딩
- 필수 필드, enum, unknown field, 중복 ID 검증
- built-in pack과 user pack 합성
- `BOOLEAN_CALL` 후보에 method name/signature와 실패 분기를 매칭
- 사용자 Rule의 실행 통계와 진단 제공

초기 YAML의 대표 형태는 다음과 같다.

```yaml
rules:
  - id: DOCUMENT_EDIT_PERMISSION
    match:
      predicateType: BOOLEAN_CALL
      methodName: canEdit
      failureOutcome: THEN
    output:
      category: AUTHORIZATION
      constraint:
        kind: CONTROL_FLOW_ONLY
```

이 PoC가 증명한 것은 “YAML을 읽을 수 있다”는 사실이다. 동시에 다음 한계도 드러냈다.

- 메서드 이름을 매칭해도 대상이 어떤 API 입력에서 왔는지 자동으로 알 수 없다.
- `CONTROL_FLOW_ONLY` 결과는 실행 가능한 request condition보다 `excludedBusinessRules`로 귀속되는 경우가 많다.
- graph traversal, argument origin, polarity, targetPath 계산까지 YAML에 넣으면 YAML이 또 하나의 프로그래밍 언어가 된다.

### 2.2 Fact Code Graph 통합: 분석의 기준점을 하나로 만들기

관련 커밋:

- `30bec40` — Fact Code Graph UoW 구현
- `f1aac83` — 코드 그래프 이중화 제거 및 Graph Rule Aggregate
- `74b2253` — legacy validation evidence graph 제거
- `1b97c62` — UoW 통합 및 회귀 테스트 정리

초기 구조에서는 endpoint/validation/output 경로가 서로 다른 그래프나 중간 모델을 소비하면서 같은 사실이 중복되거나 변환 중 손실될 위험이 있었다. 이후 하나의 canonical `FactCodeGraph`를 중심으로 다음을 통합했다.

- Controller → Service → Domain 메서드 호출 관계
- 조건식과 `THROW`/`RETURN`의 제어 관계
- parameter, local variable, field access, literal의 데이터 관계
- DTO schema와 JSON field mapping
- 후보의 evidence와 endpoint 귀속

이 전환의 의미는 단순한 리팩터링이 아니다.

> Rule과 LLM이 서로 다른 소스 표현을 분석하는 것이 아니라, 동일한 사실 그래프를 소비해야 결과를 공정하게 비교하고 함께 개선할 수 있다.

### 2.3 Rule과 LLM의 입력 통합

관련 커밋:

- `12db074` — Rule 또는 LLM scan mode와 구조화 UI 도입
- `01df9a5` — Rule과 LLM의 Fact Graph pipeline 통합
- `827da4f`, `9a79477` — OpenAI 분석 경로의 오류 관측성과 파싱 안정성 개선

현재의 비교 단위는 “YAML 대 AI”가 아니라 다음 구조에 가깝다.

```text
Spring Source
    ↓
Canonical Fact Code Graph
    ↓
Predicate Candidate / Evidence
    ├─ Deterministic Rule & Semantic Classifier
    └─ LLM Semantic Interpretation
```

두 방식이 같은 그래프와 evidence를 입력으로 받아야 정확도, 재현성, 비용을 의미 있게 비교할 수 있다.

### 2.4 의미 계층 분리: Rule 개수보다 책임과 탐색 비용 개선

관련 커밋:

- `e74c0e9` — shared semantic graph context
- `5831ab6` — validation seed contributor 분리
- `df994ad` — Optional lookup classifier 통합
- `d0f36f8` — heuristic authorization rule 격리
- `962cb26` — 전문 semantic classifier 추가
- `49933a6` — delegated composite validation 분류
- `e86d63d` — 검증된 semantic dispatch로 cutover
- `c01d84c`, `26801ef` — 직접 request constraint와 optimistic lock 분류

이 단계의 핵심 문제는 Java Rule 클래스가 많다는 사실 자체가 아니었다.

- 각 Rule이 동일 그래프를 반복 순회했다.
- Detector와 Rule이 같은 관용구의 의미를 중복 판단했다.
- 구조 탐색, 의미 분류, API target 결정, output 변환이 섞였다.
- 증명하지 못한 결과가 실행 가능한 조건처럼 보일 위험이 있었다.

이를 해결하기 위해 현재 구조는 책임을 다음처럼 분리한다.

| 계층 | 책임 |
|---|---|
| Fact Graph Builder | AST, symbol, call, control/data relation을 사실로 기록 |
| Candidate Detector / Seed Contributor | 검증 가능성이 있는 구조를 후보로 수집 |
| `SemanticContext` / `FactGraphIndex` | 공통 인덱스와 탐색 문맥 제공 |
| `SemanticRuleDispatcher`와 classifier | Optional, PasswordEncoder, binary constraint 등 의미 분류 |
| Target Resolver | Java 값의 기원을 API wire name과 JSONPath로 연결 |
| Output Adapter | effect와 resolution 상태에 따라 4대 결과로 귀속·중복 제거 |

이 구조가 YAML 외부화의 전제다. YAML은 위 계층 전체를 대체하지 않고, 안정화된 semantic classifier가 소비할 프로젝트별 의미 사전을 선언해야 한다.

### 2.5 실제 저장소 품질 개선

관련 커밋:

- `fafc9f9` — 실제 저장소의 Rule output coverage 개선
- `f3fbb1a` — canonical graph 기반 Rule output 품질 개선
- `c2da925` — 현재 구조에 맞춘 reverse-engineering 문서 갱신

실제 17개 endpoint corpus 비교에서 확인된 내용은 다음과 같다.

- endpoint와 binding 탐지는 안정적이었다.
- `auto-oas`는 DTO 구조, response status, evidence 품질에서 강점이 있었다.
- 비교 대상 분석기는 repository lookup과 외부 상태 조건 recall에서 강점이 있었다.
- 병목은 endpoint 탐지가 아니라 wire-name identity, repository Optional 의미, delegated authentication, response wrapper/factory 해석이었다.

이후 개선은 단순 Rule 추가보다 다음 품질 축에 집중했다.

- Java 인자명과 실제 PATH/QUERY/HEADER wire name 분리
- Optional lookup을 request 값 제약이 아닌 외부 리소스 존재 조건으로 구분
- delegated boolean validation의 내부 조건 승격
- PasswordEncoder 실패 조건의 입력 origin 연결
- generic response wrapper와 `@JsonIgnore` 반영
- response factory의 상수값을 body assertion으로 추출
- 해결된 결과와 diagnostic의 정합성 및 중복 제거

---

## 3. 현재 구현 구조

현재 파이프라인은 다음 순서로 동작한다.

```text
Git URL 또는 Local Repository
    ↓
Spring Endpoint / DTO Static Scan
    ↓
Fact Code Graph Build
    - 제한된 interprocedural traversal
    - AST node/edge
    - DTO schema
    ↓
Annotation Validation Extraction
    ↓
Method Scope + Predicate Candidate Detection
    ↓
Semantic Dispatch
    - Binary / Standard Guard
    - Composite Validation
    - Optional Lookup
    - PasswordEncoder
    - Optimistic Lock
    - Authorization 등
    ↓
Candidate Output Adaptation
    ├─ requestPreconditions
    ├─ responseAssertions
    ├─ excludedBusinessRules
    └─ diagnostics
    ↓
JSON / OpenAPI YAML Export
```

### 모든 코드를 무제한으로 그래프에 넣지 않는다

그래프 빌더는 성능과 오탐을 제어하기 위해 traversal policy와 budget을 사용한다.

- 애플리케이션 내부 소스가 있는 비즈니스 메서드는 body까지 방문한다.
- JDK, Spring, 외부 라이브러리, Repository 등은 주로 호출 사실만 기록한다.
- 최대 depth, API별 방문 메서드 수, edge 수를 제한한다.
- 분석할 수 없는 구조는 조용히 버리지 않고 diagnostic으로 남기는 방향을 사용한다.

### 출력은 “찾았다”가 아니라 “증명 수준”에 따라 결정된다

```text
Candidate
  ├─ 구조 미지원 → diagnostics
  ├─ 의미 미해결 → diagnostics
  └─ 의미 해결
       ├─ REQUEST_REQUIREMENT → requestPreconditions
       ├─ RESPONSE_GUARANTEE → responseAssertions
       └─ BUSINESS_RESTRICTION → excludedBusinessRules
```

이 구분은 coverage를 높이기 위해 추측한 값을 실행 가능한 테스트 조건으로 내보내는 일을 막는다.

---

## 4. 무엇이 어려웠고, 무엇을 배웠는가

### 4.1 조건을 발견하는 것과 테스트 조건으로 만드는 것은 다르다

`if (...) throw ...`를 찾는 것만으로는 충분하지 않다. 최소한 다음 질문에 답해야 한다.

1. 실패 분기는 `then`인가 `else`인가?
2. 부정 연산과 delegated boolean method를 거치며 polarity가 어떻게 변하는가?
3. 조건의 값은 Controller의 어떤 인자에서 왔는가?
4. Java 인자명이 아니라 실제 HTTP wire name은 무엇인가?
5. BODY라면 정확한 JSONPath는 무엇인가?
6. 이 조건은 요청 값 제약인가, DB/권한/도메인 상태 같은 외부 선행조건인가?

그래서 현재 병목은 AST node 수가 아니라 **의미와 origin의 연결**이다.

### 4.2 프로젝트별 메서드 이름을 코드에 계속 추가할 수 없다

실제 프로젝트는 같은 의미를 서로 다른 이름으로 표현한다.

- `findById`, `findOne`, `load`, `require`
- `validate`, `check`, `ensure`, `canEdit`
- `matches`, `isMatchPassword`, custom encoder wrapper

이를 모두 Java의 `if (methodName.equals(...))`로 처리하면 분석기는 특정 프로젝트의 메서드 사전이 된다. 반대로 이름 패턴을 느슨하게 잡으면 false positive가 증가한다.

이 문제는 YAML이 해결할 수 있는 대표 영역이지만, YAML에는 “이 이름/signature가 어떤 의미다”만 넣고 data-flow와 graph traversal은 엔진에 남겨야 한다.

### 4.3 YAML의 범위를 넓히면 자체 프로그래밍 언어가 된다

다음을 모두 YAML로 표현하려 하면 유지보수 가능한 설정 포맷이 아니라 디버깅하기 어려운 graph query language가 된다.

- graph traversal 순서와 깊이
- argument binding과 origin 역추적
- condition expression과 polarity
- exception/outcome 연결
- targetPath 계산
- output transformation
- deduplication과 confidence 정책

따라서 YAML은 분석 절차가 아니라 **구조화된 의미 매핑**으로 제한하는 것이 적절하다.

### 4.4 Rule과 LLM은 경쟁 구현이 아니라 역할이 다르다

- 구조가 명확하고 반복되는 패턴은 deterministic classifier가 적합하다.
- 프로젝트 고유 이름과 문맥 해석은 YAML alias 또는 LLM이 유리할 수 있다.
- LLM이 반복해서 같은 의미로 분류하고 사람이 검증한 패턴은 YAML Rule로 승격할 수 있다.
- 어떤 경로든 evidence와 resolution status를 남기고 동일 golden corpus로 평가해야 한다.

---

## 5. 앞으로의 외부화 방향

### 코드에 남길 책임

- Java parsing과 symbol resolution
- Fact Code Graph 생성
- method traversal과 budget
- control/data-flow 추적
- argument origin과 wire-name/JSONPath 계산
- condition polarity와 outcome 분석
- endpoint projection
- output 귀속, 중복 제거, diagnostic lifecycle

### YAML로 외부화할 후보

- qualified owner type 또는 signature pattern
- method alias
- receiver와 argument의 semantic role
- 성공/실패 polarity
- exception 의미
- rule category와 effect
- normalized operator 또는 domain meaning
- 프로젝트별 vocabulary

향후 YAML은 다음과 같은 수준이 적절하다.

```yaml
rules:
  - id: PASSWORD_MUST_MATCH
    match:
      ownerType: org.springframework.security.crypto.password.PasswordEncoder
      method: matches
      arguments:
        - role: rawValue
        - role: encodedValue
      failure:
        negated: true
        throws: "*InvalidPasswordException"
    output:
      category: AUTHENTICATION
      effect: REQUEST_REQUIREMENT
      targetFromArgumentRole: rawValue
      operator: MATCHES_DOMAIN_VALUE
```

여기서 YAML은 `rawValue`가 어느 API 입력인지 직접 탐색하지 않는다. 엔진이 argument origin을 역추적해 BODY의 `$.password` 같은 target을 계산한다.

핵심 원칙:

> Java 엔진은 “무슨 일이 일어났는가”를 구조적으로 증명하고, YAML은 “이 구조가 이 프로젝트에서 무슨 의미인가”를 선언한다.

---

## 6. Rule, YAML, LLM 비교 계획

세 방식을 동일한 candidate/evidence와 동일한 평가 corpus에서 비교한다.

| 항목 | Java Classifier | YAML Mapping | LLM Interpretation |
|---|---|---|---|
| 정확도 | 알려진 구조에서 높음 | 등록된 의미에서 높음 | 문맥에 따라 변동 |
| 커버리지 | 구현된 classifier 범위 | 등록된 프로젝트 vocabulary 범위 | 미등록 패턴 해석 가능 |
| 재현성 | 높음 | 높음 | 모델·프롬프트 영향 |
| 변경 비용 | 코드·테스트·배포 필요 | 설정 변경과 검증 필요 | 프롬프트·모델·검증셋 관리 |
| 설명 가능성 | classifier와 evidence 명확 | Rule ID와 match 근거 명확 | evidence 인용을 강제해야 함 |
| 적합한 역할 | 공통 구조와 검증된 관용구 | 프로젝트별 의미 사전 | long-tail semantic fallback |

측정 항목:

- endpoint/binding/schema recall
- semantic resolved ratio
- precision과 recall
- false target 및 false positive
- response assertion coverage
- diagnostic 잔존/중복 비율
- 신규 프로젝트 적용 시간
- Rule 작성·검토 시간
- LLM latency와 token cost
- 사람의 최종 검토 시간

현실적인 목표 구조는 다음과 같다.

```text
1. 공통이며 구조적으로 증명 가능한 패턴
   → Java semantic classifier

2. 프로젝트별 용어와 alias
   → YAML semantic mapping

3. 미등록 long-tail 후보
   → LLM fallback

4. 반복되고 검증된 LLM 해석
   → YAML 또는 Java classifier로 승격
```

---

## 7. 발표 구성안

### 슬라이드 1. 문제 정의

제목:

> API 목록이 아니라, 테스트 가능한 실행 조건을 추출한다

전달 내용:

- 어노테이션만으로는 서비스·도메인 내부의 검증 조건을 알 수 없다.
- 목표는 코드 실행 경로를 API precondition, assertion, business rule로 변환하는 것이다.

### 슬라이드 2. 출발점과 초기 PoC

제목:

> YAML Rule은 이미 PoC했지만, 포맷이 핵심 문제는 아니었다

전달 내용:

- YAML 로딩, 검증, built-in pack 합성, 실행까지 구현했다.
- 초기 PoC는 method call과 control flow를 외부화할 수 있음을 증명했다.
- 그러나 targetPath와 외부 상태 의미를 해결하려면 공통 분석 모델이 필요했다.

### 슬라이드 3. 구조 전환

제목:

> 하나의 Fact Code Graph를 분석의 기준으로 통합

전달 내용:

- 이중 그래프와 legacy evidence 경로 제거
- Rule과 LLM이 같은 graph/evidence 소비
- endpoint별 제한된 projection과 traversal budget 사용

### 슬라이드 4. 현재 파이프라인

제목:

> Source → Fact → Candidate → Semantics → Test Contract

전달 내용:

```text
Spring Source
  → Fact Code Graph
  → Predicate Candidate
  → Semantic Classifier
  → Target Resolution
  → Preconditions / Assertions / Business Rules / Diagnostics
```

### 슬라이드 5. 실제로 해결한 의미 패턴

제목:

> 메서드 이름 매칭에서 구조와 의미 분류로

예시:

- direct binary constraint
- 표준 null/empty guard
- `Optional.orElseThrow`와 repository lookup
- `PasswordEncoder.matches`
- delegated composite validation
- optimistic lock
- response wrapper, factory constant, `@JsonIgnore`

### 슬라이드 6. 가장 큰 기술적 병목

제목:

> 조건 발견보다 API 입력과 외부 상태로의 연결이 어렵다

전달 내용:

- delegated call에서 argument origin 보존
- Java 이름과 wire name 분리
- request constraint와 external-state prerequisite 구분
- 해결된 결과와 diagnostic lifecycle 정합성

### 슬라이드 7. 개발 방식

제목:

> 실제 corpus와 golden regression으로 안전하게 cutover

전달 내용:

```text
실제 실패 수집
  → 작은 fixture로 재현
  → shadow classifier 구현
  → semantic output 동등성 검증
  → 기존 Rule 억제 또는 제거
  → 전체 corpus 회귀
```

### 슬라이드 8. YAML의 역할 재정의

제목:

> YAML은 그래프 탐색 언어가 아니라 프로젝트 의미 사전

| 엔진에 유지 | YAML로 외부화 |
|---|---|
| AST와 Fact Graph | method/signature alias |
| data-flow와 origin | argument semantic role |
| polarity와 outcome | category/effect/operator |
| wire name과 JSONPath | 프로젝트별 vocabulary |
| deduplication/diagnostic | exception/domain mapping |

### 슬라이드 9. Rule과 AI의 역할

제목:

> 공통 사실 위에서 deterministic core와 semantic fallback을 결합

전달 내용:

- Java: 공통이며 구조적으로 증명 가능한 패턴
- YAML: 프로젝트별로 바뀌는 의미 매핑
- LLM: 미등록 long-tail 후보
- 반복 검증된 AI 결과는 결정적 Rule로 승격

### 슬라이드 10. 다음 단계와 결정 요청

기술적 다음 단계:

1. endpoint graph를 안정적인 canonical project graph/projection 구조로 정리
2. repository Optional과 external-state prerequisite의 출력 의미 분리
3. delegated password/authentication의 origin·polarity 회귀 강화
4. response generic, Jackson visibility, factory constant coverage 강화
5. 구조화된 semantic field만 사용하는 YAML 2차 PoC
6. 동일 corpus에서 Java/YAML/LLM의 품질과 비용 비교

회의에서 정할 내용:

- YAML을 프로젝트별 의미 매핑으로 제한할 것인가?
- request 값 조건과 테스트 데이터/외부 상태 조건을 출력 계약에서 분리할 것인가?
- precision과 recall 중 어느 지표를 우선할 것인가?
- 사용자 Rule의 작성·검토·배포 주체는 누구인가?
- LLM을 fallback으로 허용할 경우 evidence와 승인 기준을 어떻게 둘 것인가?

---

## 8. 발표 도입 멘트 초안

> 이 프로젝트는 Spring 코드에서 API 목록만 만드는 도구가 아닙니다. Controller 이후 Service와 Domain 실행 경로를 따라가며, 어떤 요청 조건이 필요하고 어떤 응답을 보장하며 어떤 외부 상태가 준비되어야 하는지를 테스트 가능한 형태로 추출하는 프로젝트입니다.
>
> 사용자 YAML Rule의 로딩과 실행은 이미 최소 PoC로 검증했습니다. 하지만 실제로 적용해 보니 어려운 문제는 YAML 문법이 아니었습니다. `canEdit`, `orElseThrow`, `matches` 같은 호출을 찾은 뒤, 그 조건이 어떤 API 입력에서 왔고 요청 값 제약인지 외부 상태 조건인지 증명하는 공통 의미 모델이 핵심이었습니다.
>
> 그래서 이후에는 하나의 Fact Code Graph로 분석 기준을 통합하고, 후보 탐지·의미 분류·target 해석·출력 정규화를 분리했습니다. Rule과 LLM도 이제 서로 다른 입력을 보는 것이 아니라 같은 graph와 evidence를 소비하는 방향으로 정리했습니다.
>
> 앞으로 YAML은 분석 엔진 전체를 옮기는 포맷이 아니라, 프로젝트마다 달라지는 메서드와 도메인 용어의 의미를 외부화하는 사전으로 제한하려고 합니다. 공통 구조는 코드로 증명하고, 프로젝트별 의미는 YAML로, 미등록 패턴은 LLM fallback으로 처리하는 구조를 동일 corpus에서 비교 검증하는 것이 다음 단계입니다.

---

## 9. 마무리 멘트 초안

> 지금까지의 핵심 산출물은 거대한 Rule 목록이나 YAML 문법이 아니라, 소스코드를 공통 사실로 바꾸는 Fact Code Graph와 그 사실을 테스트 계약으로 변환하는 의미 파이프라인입니다.
>
> YAML PoC를 먼저 해 본 덕분에 무엇을 외부화하면 안 되는지도 확인했습니다. graph traversal, data-flow, targetPath 계산까지 설정으로 옮기지 않고, 검증된 구조 위에서 프로젝트별 의미만 점진적으로 외부화하겠습니다.
>
> 최종적으로는 Java Rule, YAML mapping, LLM 해석을 경쟁 관계로 두지 않습니다. 같은 evidence를 기반으로 각각 공통 패턴, 프로젝트별 vocabulary, long-tail 문맥 해석을 담당하게 하고, 실제 corpus의 precision·recall·운영 비용으로 경계를 결정하겠습니다.

---

## 10. 발표 자료 작성 시 사실 확인 기준

HTML 제작 단계에서 다음 원칙을 유지한다.

- 첨부 방향 문서의 `45 → 64 → 72` 점수는 현재 저장소의 평가 산식과 결과 파일로 재검증되기 전까지 사용하지 않는다.
- endpoint 수는 corpus마다 다르므로 `12개`와 `17개`를 섞지 않는다.
  - `ddd-start2` PoC 보고서는 12개 API 사례다.
  - cross-analyzer 개선 문서는 HyunSolution_BE 17개 endpoint 평가다.
- YAML을 “미구현”이라고 표현하지 않는다. 로더와 실행 PoC는 구현되어 있다.
- YAML이 현재 기본 production path에 연결되어 있다고 과장하지 않는다. 현재 `RuleOutputService`는 built-in `InitialRulePacks.all()`을 사용한다.
- 작업 트리의 미커밋 변경은 완료 기능으로 발표하지 않는다.
- 코드나 commit으로 확인되지 않은 수치 대신, 검증 가능한 구조 변화와 corpus 사례를 중심으로 설명한다.

## 11. 근거 자료

- `README.md`
- `docs/fact-code-graph-to-rule-output-pipeline.md`
- `docs/semantic-rule-engine-uow/README.md`
- `docs/cross-analyzer-improvement-uow/README.md`
- `construction.md`
- `poc_report.md`
- `src/main/java/io/atworks/specscan/analysis/application/RuleOutputService.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/yaml/`
- `src/main/java/io/atworks/specscan/analysis/support/semantic/`
- 위 본문에 표시한 Git commits
