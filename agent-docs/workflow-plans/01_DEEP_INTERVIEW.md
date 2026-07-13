# AI-DLC Workflow Plan 01: Deep Interview Gating

이 지침서는 AI-DLC 방법론의 첫 번째 단계인 **Deep Interview Gating(심층 인터뷰 연동)** 단계를 완수하기 위한 에이전트 실행 지침 및 명세서입니다.

---

## 🎯 1. 실행 목표 (Goal)
에이전트가 독단적으로 코드를 구현하기 전에 비즈니스 요구사항과 소스 코드 간의 정책적 불일치, 설계 모호성, 엣지 케이스를 발견하고, 개발자와의 문답을 통해 정밀한 명세(Specs)를 자산화하여 모호성을 최소화하는 것입니다.

---

## 🛠️ 2. 상세 실행 지침 (Specific Instructions)

### ① 모호성 탐지 및 질문 추출
* 에이전트는 작업 대상 코드베이스를 분석하여 다음과 같은 정합성 충돌을 발견할 경우 즉시 작업을 중단하고 질문을 준비해야 합니다:
  - 구현하려는 새로운 규칙이 기존 설계 규칙(예: 검증 로직 등)과 충돌하는 경우.
  - 요구사항이 추상적이어서 구체적인 어설션(Assertion) 검증 케이스를 정할 수 없는 경우.
* 질문은 기계적인 단답식이 아닌, 다음과 같이 **다중 선택식 대안(Options)** 구조로 작성하십시오.

### ② 인터뷰 작성 포맷 예시
```text
[질문] @ModelAttribute OrderRequest 복구 시, 기존 validationConditions 데이터 포맷과의 호환성을 어떻게 유지해야 합니까?
- Option A: 기존 validationConditions의 JSON Path와 구조를 완전히 보존하고 신규 필드만 추가합니다. (권장 - 하위 호환 보장)
- Option B: OrderRequest 사양에 맞춰 validationConditions의 스키마를 전면 리팩토링합니다. (호환성 깨짐 발생 가능)
[의견 선택]: Option A를 선택해주십시오.
```

---

## 📄 3. 산출물 명세 (Specs 디렉토리 파일 생성)

인터뷰 결과 합의된 결정 사항은 에이전트의 향후 컨텍스트 오염을 막기 위해 아래 파일로 고정 영속화해야 합니다.

### 파일명: `specs/deep-interview-[slug].md`
* **포함될 내용**:
  - `Slug`: 해당 명세 영역 식별자
  - `Decisions`: 인터뷰 결과 확정된 의사결정 목록
  - `Constraints`: 개발 진행 시 반드시 우회하지 않고 준수해야 할 하드 코딩 제약 조건 목록
* **작성 템플릿**:
```markdown
# Deep Interview Specification: [Feature Name]

## 의사결정 사항 (Decisions)
- [Decision 1] 기존 validationConditions 스키마의 하위 호환성을 유지한다.
- [Decision 2] 검증 로직은 endpoint-scoped graph-backed 데이터에 의해서만 결정된다.

## 하드 제약 조건 (Constraints)
- [Constraint 1] ValidationExtractionService는 Spring MVC의 기본 파라미터를 필터링해야 한다.
```

---

## 📋 4. 에이전트 적용용 룰 (Copy & Paste Rule)
> ```text
> # RULE: AI-DLC DEEP INTERVIEW GATING
> - Check if any ambiguity exists between requirements and code.
> - If ambiguity is found, halt coding immediately.
> - Formulate choices (Option A, B, C) with trade-offs and ask the developer.
> - Save the approved answers to specs/deep-interview-[slug].md.
> - Strictly follow this spec file in subsequent coding.
> ```
