# Code Graph 기반 API 테스트 조건 추출 — 10분 발표 대본 (Why 중심)

> 이 문서는 HTML 슬라이드로 만들기 전의 **발표 대본**이다.
> 원칙: 기능 나열(What)이 아니라, 왜 만들었고 왜 이 구조가 되었는지(Why)를 하나의 이야기로 잇는다.
> 목표 시간: 약 10분. 아래 각 슬라이드에 **말할 시간**과 **말 대본(narration)**을 붙였다.

---

## 이 발표를 관통하는 단 하나의 문장

> **어려운 문제는 "조건을 어떤 포맷으로 적느냐"가 아니라, "코드 속 조건이 어떤 API 입력에서 왔고 무엇을 의미하는지 증명하는 것"이었다.**

이 문장 하나가 이 발표의 척추다. 모든 구조 결정 — Fact Code Graph 통합, 의미 계층 분리, 증명 수준별 출력 — 은 전부 이 한 문장에서 파생된다. 발표 내내 청중이 이 문장을 붙잡게 만든다.

발표 구조도 이 통찰을 중심으로 뒤집는다.

- **일반적인 발표**: 무엇을 만들었나 → 어떻게 동작하나 → 앞으로
- **이 발표(Why 중심)**: 무엇이 진짜 어려웠나(Why) → 그래서 이 구조를 택했다(What) → 그래서 이 방향으로 간다(Where)

---

## 시간 배분 (총 10분)

| 구간 | 슬라이드 | 시간 | 역할 |
|---|---|---|---|
| 여는 이야기 | 1 | 1:00 | 문제를 피부로 느끼게 |
| Why ①: 왜 이 도구가 필요했나 | 2 | 1:30 | 어노테이션의 한계 |
| Why ②: 왜 YAML만으로 안 됐나 | 3 | 1:30 | PoC가 드러낸 진짜 문제 |
| 전환점 | 4 | 1:00 | "포맷이 아니라 의미" 선언 |
| What ①: 그래서 Fact Graph로 통합 | 5 | 1:30 | 왜 하나의 그래프인가 |
| What ②: 그래서 증명 수준으로 출력 | 6 | 1:30 | 왜 함부로 안 내보내나 |
| Where: 그래서 이렇게 나눈다 | 7 | 1:30 | Java / YAML / LLM 역할 |
| 닫는 이야기 + 결정 요청 | 8 | 0:30 | 회의에서 정할 것 |

> 슬라이드 8장. 슬라이드당 평균 1분 15초. 커밋 해시·긴 표는 슬라이드가 아니라 **부록(Appendix)**으로 뺀다. 질문 나오면 그때 연다.

---

## 슬라이드 1 — 문제를 피부로 (1:00)

**제목:** `if (...) throw ...` — 이 한 줄을 어떻게 테스트로 만들 것인가

**화면에 띄울 것:** 코드 세 조각만. 설명 텍스트 없음.

```java
if (!passwordEncoder.matches(request.password(), member.password()))
    throw new InvalidPasswordException();
```
```java
Order order = orderRepository.findById(orderNo)
    .orElseThrow(OrderNotFoundException::new);
```
```java
if (!order.isShippingChangeable())
    throw new IllegalStateException();
```

**말 대본:**
> "API 테스트를 자동으로 만들려고 합니다. 그런데 정작 테스트에 필요한 조건은 여기 이 세 줄에 숨어 있습니다. 비밀번호가 틀리면 실패, 주문이 없으면 실패, 배송 상태가 아니면 실패. 문제는 — 이 조건들이 Controller의 어노테이션에는 전혀 안 나온다는 겁니다. Service와 Domain 깊숙이 들어가야 나옵니다. 오늘 이야기는 '이 한 줄들을 어떻게 실행 가능한 테스트 조건으로 바꿨는가', 그리고 그 과정에서 '진짜 어려운 문제가 무엇이었는가'입니다."

---

## 슬라이드 2 — Why ①: 어노테이션은 절반만 알려준다 (1:30)

**제목:** 어노테이션으로 얻는 것과, 못 얻는 것

**화면에 띄울 것:** 좌우 대비.

| 어노테이션으로 얻는다 (쉬움) | 실행 경로에만 있다 (어려움) |
|---|---|
| HTTP method / path | 비밀번호 일치 여부 |
| PATH/QUERY/HEADER/BODY 바인딩 | 리소스 존재 여부 (Optional lookup) |
| request/response DTO 스키마 | 권한·상태 전이 조건 |
| Bean Validation (`@NotNull` 등) | 낙관적 락, 도메인 불변식 |

**말 대본:**
> "왼쪽은 어노테이션만 봐도 안정적으로 뽑힙니다. 이건 이미 잘 됩니다. 문제는 오른쪽입니다. 오른쪽 조건들이야말로 negative test, 시나리오 테스트를 만들 때 진짜 필요한 것들인데, 코드 실행 경로를 따라가야만 보입니다. 그래서 저희 목표는 API 목록을 만드는 게 아니라, 실행 경로를 따라가서 **네 가지로 정규화하는 것**입니다."

**보조 (한 줄씩):**
- `requestPreconditions` — 요청 값이 충족해야 할 조건 → negative test
- `responseAssertions` — 정상 응답이 보장하는 것 → status/body 검증
- `excludedBusinessRules` — 요청 값만으론 못 만드는 외부 상태 조건 → 사전 데이터 설계
- `diagnostics` — 끝까지 증명 못 한 후보 → 품질 개선·사람 검토

> **이 슬라이드의 Why:** "왜 굳이 실행 경로까지 파고드나? 어노테이션 밖에 진짜 조건이 있으니까."

---

## 슬라이드 3 — Why ②: YAML을 먼저 해봤다, 그런데 (1:30)

**제목:** YAML Rule은 PoC까지 됐다 — 문제는 포맷이 아니었다

**화면에 띄울 것:** 초기 YAML 한 개 + 그것이 드러낸 한계 세 줄.

```yaml
- id: DOCUMENT_EDIT_PERMISSION
  match:
    predicateType: BOOLEAN_CALL
    methodName: canEdit
    failureOutcome: THEN
  output:
    category: AUTHORIZATION
    constraint: { kind: CONTROL_FLOW_ONLY }
```

**말 대본:**
> "처음엔 '규칙을 YAML로 외부화하면 되겠다' 생각했고, 실제로 로딩·검증·실행까지 PoC를 만들었습니다. 여기까지는 됩니다. 그런데 실제로 돌려보니 벽에 부딪혔습니다.
> `canEdit`라는 메서드 이름은 매칭했는데 — **그래서 이 조건이 어떤 API 입력에서 온 거지?** YAML은 답을 못 합니다.
> `CONTROL_FLOW_ONLY`로 찾긴 했는데 — 이게 요청 값 제약인지, 외부 권한 상태인지 구분이 안 됩니다.
> 그리고 만약 이 판단까지 전부 YAML에 넣으면? YAML이 또 하나의 프로그래밍 언어가 됩니다.
> **여기서 깨달았습니다. 어려운 건 포맷이 아니었다.**"

**강조 박스:**
> ⚠️ 발표에서 절대 이렇게 말하지 않는다: "YAML 룰 엔진은 아직 구현 안 됐다."
> 정확한 표현: "YAML 실행 가능성은 PoC로 검증했고, 그 덕분에 **무엇을 외부화하면 안 되는지**를 배웠다."

> **이 슬라이드의 Why:** "왜 YAML로 다 안 하나? 해봤더니 포맷은 쉬운 부분이었고, 의미 해석이 진짜 문제였으니까."

---

## 슬라이드 4 — 전환점 (1:00)

**제목:** 진짜 질문은 여섯 개였다

**화면에 띄울 것:** 코드 한 줄 위에 질문 여섯 개.

```java
if (!passwordEncoder.matches(request.password(), member.password())) throw ...
```

1. 실패 분기는 `then`인가 `else`인가? (polarity)
2. `!`와 위임 메서드를 거치며 polarity가 어떻게 뒤집히나?
3. 이 값은 Controller의 **어느 인자**에서 왔나? (origin)
4. Java 인자명이 아니라 실제 **HTTP wire name**은? (`password`? `pwd`?)
5. BODY라면 정확한 **JSONPath**는? (`$.password`)
6. 이건 **요청 값 제약**인가, **외부 선행조건**인가?

**말 대본:**
> "`matches`라는 호출 하나를 진짜 테스트 조건으로 바꾸려면, 최소한 이 여섯 개 질문에 답해야 합니다. 이름 매칭은 첫 번째 질문에도 못 답합니다. 이 여섯 개는 전부 **데이터 흐름과 그래프 탐색** 문제입니다. 그래서 저희는 방향을 바꿨습니다. YAML 문법을 넓히는 대신, 이 여섯 질문에 답할 수 있는 **공통 분석 기반**을 먼저 만들기로."

> **이 슬라이드가 발표의 무게중심.** 여기서 청중이 "아, 그래서 구조를 그렇게 만들었구나"를 받아들이게 만든다.

---

## 슬라이드 5 — What ①: 그래서 Fact Code Graph 하나로 (1:30)

**제목:** 왜 하나의 그래프인가 — 공정한 비교를 위해

**화면에 띄울 것:** 파이프라인 다이어그램.

```text
Spring Source
    ↓
Canonical Fact Code Graph   ← 모든 분석이 소비하는 단 하나의 사실
    ↓
Predicate Candidate + Evidence
    ├─ Deterministic Rule & Semantic Classifier
    └─ LLM Semantic Interpretation
```

**말 대본:**
> "예전엔 endpoint 분석, validation 분석, output 분석이 **서로 다른 중간 모델**을 봤습니다. 같은 사실이 중복되고, 변환하다 손실됐습니다. 그래서 하나의 canonical Fact Code Graph로 통합했습니다 — 호출 관계, 조건과 throw/return, 데이터 흐름, DTO 스키마를 전부 **하나의 사실**로 기록합니다.
> 이게 단순 리팩터링이 아닌 이유: Rule과 LLM이 **같은 그래프, 같은 evidence**를 입력으로 받아야, 정확도·재현성·비용을 공정하게 비교할 수 있습니다. 서로 다른 걸 보면서 '누가 더 낫다'고 말할 수 없으니까요."

**보조 — 왜 코드를 무제한으로 넣지 않나:**
> "전부 그래프에 넣으면 느리고 오탐이 늘어납니다. 그래서 내부 비즈니스 메서드는 본문까지 방문하고, JDK·Spring·라이브러리는 호출 사실만 기록합니다. 깊이·메서드 수·엣지 수에 budget을 둡니다. **못 푸는 건 조용히 버리지 않고 diagnostic으로 남깁니다.**"

> **이 슬라이드의 Why:** "왜 그래프를 통합했나? Rule과 LLM을 같은 잣대로 비교하려면 같은 사실을 봐야 하니까."

---

## 슬라이드 6 — What ②: 그래서 "증명 수준"으로 출력 (1:30)

**제목:** 왜 함부로 내보내지 않는가 — 추측을 테스트 조건으로 만들지 않기 위해

**화면에 띄울 것:** 출력 결정 트리.

```text
Candidate
  ├─ 구조 미지원      → diagnostics
  ├─ 의미 미해결      → diagnostics
  └─ 의미 해결
       ├─ REQUEST_REQUIREMENT   → requestPreconditions
       ├─ RESPONSE_GUARANTEE    → responseAssertions
       └─ BUSINESS_RESTRICTION  → excludedBusinessRules
```

**말 대본:**
> "저희 출력은 '찾았다/못 찾았다'가 아니라 '어디까지 증명했나'로 갈립니다. 구조를 못 봤으면 diagnostic, 의미를 못 풀었어도 diagnostic. **의미까지 증명된 것만** 세 종류의 실행 가능한 계약으로 나갑니다.
> 왜 이렇게까지? coverage 숫자를 올리려고 추측한 값을 실행 가능한 테스트 조건이라고 내보내는 순간, 이 도구는 신뢰를 잃습니다. 못 푼 건 못 풀었다고 정직하게 남기는 게 더 낫습니다."

**보조 — 의미 계층을 나눈 이유(간단히):**
> "그리고 이걸 가능하게 하려고 책임을 나눴습니다. 사실 기록(Graph Builder), 후보 수집(Detector), 의미 분류(Classifier), API target 연결(Resolver), 출력 귀속(Adapter). 예전엔 이게 다 섞여 있어서 규칙 하나가 그래프를 매번 다시 순회하고, 증명 안 된 게 실행 가능한 조건처럼 보였습니다."

> **이 슬라이드의 Why:** "왜 증명 수준으로 나누나? 도구의 신뢰는 '정직하게 모른다고 말하는 것'에서 나오니까."

---

## 슬라이드 7 — Where: 그래서 이렇게 나눈다 (1:30)

**제목:** YAML은 그래프 탐색 언어가 아니라 "프로젝트 의미 사전"

**화면에 띄울 것:** 경계 표 + 이상적 YAML 예시.

| 엔진(코드)에 남긴다 | YAML로 외부화한다 |
|---|---|
| AST · Fact Graph 생성 | method / signature alias |
| data-flow · origin 역추적 | argument의 semantic role |
| polarity · outcome 분석 | category / effect / operator |
| wire name · JSONPath 계산 | 프로젝트별 vocabulary |
| dedup · diagnostic lifecycle | exception → 도메인 의미 매핑 |

```yaml
- id: PASSWORD_MUST_MATCH
  match:
    ownerType: org.springframework.security.crypto.password.PasswordEncoder
    method: matches
    arguments: [ { role: rawValue }, { role: encodedValue } ]
    failure: { negated: true, throws: "*InvalidPasswordException" }
  output:
    category: AUTHENTICATION
    effect: REQUEST_REQUIREMENT
    targetFromArgumentRole: rawValue   # 엔진이 origin을 역추적해 $.password를 계산
```

**말 대본:**
> "그래서 앞으로의 역할 분담은 이렇습니다. YAML은 '이 메서드가 이 프로젝트에서 무슨 의미다'만 선언합니다. `rawValue`가 어느 API 입력인지는 YAML이 직접 찾지 않습니다 — 엔진이 origin을 역추적해서 `$.password`를 계산합니다.
> 한 문장으로: **Java 엔진은 '무슨 일이 일어났는가'를 구조적으로 증명하고, YAML은 '이 구조가 이 프로젝트에서 무슨 의미인가'를 선언합니다.**
> Rule·YAML·LLM은 경쟁이 아닙니다. 공통·증명 가능한 패턴은 Java, 프로젝트별 용어는 YAML, 미등록 long-tail은 LLM fallback. 그리고 LLM이 반복해서 맞히고 사람이 검증한 패턴은 YAML이나 Java로 승격합니다. 셋 다 같은 evidence를 남기고 같은 corpus로 평가합니다."

> **이 슬라이드의 Why:** "왜 YAML을 좁게 제한하나? 넓히면 디버깅 불가능한 그래프 쿼리 언어가 되니까. 검증된 것만 점진적으로 외부화한다(Progressive Externalization)."

---

## 슬라이드 8 — 닫는 이야기 + 결정 요청 (0:30)

**제목:** 핵심 산출물은 Rule 목록이 아니라 "사실 → 계약" 파이프라인

**말 대본:**
> "정리하면, 저희 핵심 산출물은 거대한 규칙 목록이나 YAML 문법이 아닙니다. 소스코드를 **공통 사실(Fact Code Graph)**로 바꾸고, 그 사실을 **테스트 계약**으로 번역하는 의미 파이프라인입니다. YAML을 먼저 해본 덕분에, 무엇을 외부화하면 안 되는지까지 배웠습니다."

**회의에서 정할 것 (화면에 3개만):**
1. YAML을 **프로젝트별 의미 매핑**으로 제한할 것인가?
2. **요청 값 조건 vs 외부 상태 조건**을 출력 계약에서 분리할 것인가?
3. **precision과 recall** 중 무엇을 우선할 것인가?

> (LLM fallback 승인 기준, 사용자 Rule 작성·검토 주체는 시간 남으면 / 부록에서.)

---

## 부록 (슬라이드로 만들되, 질문 나올 때만 연다)

### A. 실제 corpus에서 확인한 것 (질문 대비)
- endpoint·binding 탐지는 안정적. DTO 구조·response status·evidence 품질이 강점.
- 병목은 endpoint 탐지가 아니라: wire-name identity, repository Optional 의미, delegated authentication, response wrapper/factory 해석.
- **주의:** endpoint 수는 corpus마다 다르다. `ddd-start2` PoC = 12개 API, cross-analyzer 개선 문서 = HyunSolution_BE 17개 endpoint. **섞지 말 것.**

### B. 실제로 해결한 의미 패턴 (슬라이드 5~6 보강 요청 시)
direct binary constraint · null/empty guard · `Optional.orElseThrow` repository lookup · `PasswordEncoder.matches` · delegated composite validation · optimistic lock · response wrapper / factory constant / `@JsonIgnore`

### C. 개발 방식 (프로세스 질문 시)
```text
실제 실패 수집 → 작은 fixture로 재현 → shadow classifier 구현
→ semantic output 동등성 검증 → 기존 Rule 억제/제거 → 전체 corpus 회귀
```

### D. Rule / YAML / LLM 비교표 (트레이드오프 질문 시)
| 항목 | Java Classifier | YAML Mapping | LLM |
|---|---|---|---|
| 정확도 | 알려진 구조에서 높음 | 등록된 의미에서 높음 | 문맥 따라 변동 |
| 재현성 | 높음 | 높음 | 모델·프롬프트 영향 |
| 변경 비용 | 코드·배포 | 설정·검증 | 프롬프트·검증셋 |
| 적합 역할 | 공통 구조 | 프로젝트 vocabulary | long-tail fallback |

---

## 발표 사실 확인 기준 (HTML 제작 시에도 유지)

- `45 → 64 → 72` 같은 점수는 현재 저장소 산식으로 재검증 전까지 **쓰지 않는다.**
- endpoint 수 `12개`(ddd-start2)와 `17개`(HyunSolution_BE)를 **섞지 않는다.**
- YAML을 **"미구현"이라 말하지 않는다.** 로더·실행 PoC는 구현되어 있다.
- YAML이 현재 **production 기본 경로**에 연결됐다고 과장하지 않는다. 현재 `RuleOutputService`는 built-in `InitialRulePacks.all()`을 쓴다.
- 작업 트리의 미커밋 변경을 완료 기능으로 발표하지 않는다.
- 검증 안 된 수치 대신 **구조 변화와 corpus 사례** 중심으로 말한다.

## 근거 자료
- `README.md`, `construction.md`, `poc_report.md`
- `docs/fact-code-graph-to-rule-output-pipeline.md`
- `docs/semantic-rule-engine-uow/README.md`, `docs/cross-analyzer-improvement-uow/README.md`
- `src/main/java/io/atworks/specscan/analysis/application/RuleOutputService.java`
- `src/main/java/io/atworks/specscan/analysis/support/rule/yaml/`, `.../support/semantic/`
