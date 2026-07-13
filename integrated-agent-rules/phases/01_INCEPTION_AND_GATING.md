# AI-DLC Phase 01: 인셉션 & 게이팅 (Inception & Gating)

이 지침서는 AI-DLC의 첫 번째 단계인 **인셉션(요구사항 분석 및 역공학)** 단계와 **게이팅(심층 인터뷰 연동)** 단계의 통합 실행 매뉴얼입니다.

---

## 🔄 1. 프로세스 맵 및 연동 방식

```text
[1. 거시적 인셉션 단계]
  - Workspace Detection: 브라운필드/그린필드 진단
  - Reverse Engineering: 코드베이스 아키텍처 및 비즈니스 트랜잭션 도식화
         ▼
[2. 요구사항 모호성 탐지] ➔ 에이전트의 임의 해석 위험 감지
         ▼ (트리거)
[3. 미시적 게이팅 단계 (Deep Interview Gating)]
  - 개발자와 다중 옵션식 인터뷰 수행
  - specs/deep-interview-[slug].md 자산화
```

---

## 🛠️ 2. 단계별 실행 지침 (Activities)

### ① 거시적 코드 분석 및 요구사항 대입
1. **코드 탐색**: 변경 요청이 발생하면 관련 패키지 구조와 비즈니스 서비스 간의 트랜잭션 흐름을 확인하십시오.
2. **사전 영향도 평가**: 요구사항이 시스템의 어느 영역(예: DTO, 비즈니스 검증기, 데이터베이스 어댑터 등)에 미칠지 분석하여 충돌 가능성을 예측합니다.

### ② 심층 인터뷰 실행 및 질문 설계 (Gating)
에이전트는 단순히 구현을 바로 들어가지 않고, 분석 중 확인된 모호성이나 잠재적 결함을 해소하기 위해 반드시 문답을 수행해야 합니다.

* **인터뷰 수행 기준**:
  - 기존 핵심 비즈니스 룰과 요구사항의 룰이 모호하게 얽힐 때.
  - 대표 회귀 시나리오의 통과 기준을 정밀하게 잡을 수 없을 때.
* **질문 설계 룰**:
  - 질문은 아래 예시와 같이 명확한 **대안(Options)**을 제공하고, 각 대안의 트레이드오프(기술적 강점/리스크)와 **에이전트 권장 의견**을 함께 제시해야 합니다.

* **질문 템플릿 예시**:
```text
[질문] SERVICE_HINT 복구 작업 중, 도메인 검증이 실패했을 때 에러 핸들링은 기존 Exception 객체를 재사용해야 합니까, 아니면 새로운 커스텀 Exception을 생성해야 합니까?
- Option A: 기존 DomainValidationException을 재사용합니다. (권장 - 기존 어셈블리 테스트 호환 유지)
- Option B: ServiceHintNormalizationException을 신규 생성합니다. (구체적 분류 가능하나 관련 핸들러 전면 수정 필요)
[에이전트 권장]: Option A (기존 예외 구조 유지 및 충돌 최소화)
```

---

## 📄 3. specs/ 내 자산화 명세 및 규격

개발자 답변을 통해 확정된 의사결정은 에이전트가 향후 기획 및 실행 중 위배하지 못하도록 아래 형식에 따라 명세 파일로 영속화해야 합니다.

* **저장 경로**: `specs/deep-interview-[slug].md`
* **작성 템플릿**:
```markdown
# Deep Interview Specification: [Feature Name]

## 1. 개요 및 맥락 (Context)
- [기존 코드의 문제점 및 해결 영역 기술]

## 2. 합의된 의사결정 사항 (Decisions)
- **결정 1**: [Option A/B 중 최종 선택된 내용 명시]
- **결정 2**: [추가 합의 사항 기술]

## 3. 구현 단계별 하드 제약 조건 (Constraints)
- **제약 1**: [에이전트가 코딩 및 테스트 시 반드시 만족해야 하는 불변의 규칙]
```

---

## 📋 4. 에이전트 적용용 룰 (Copy & Paste Rule)
> ```text
> # RULE: INCEPTION & GATING
> - Perform workspace and dependency detection.
> - Analyze target code for edge cases and identify rule conflicts.
> - If ambiguity is found, present structured Options (A, B, C) with trade-offs.
> - Save the finalized decision spec in specs/deep-interview-[slug].md.
> - Reject any code implementation that violates this specification.
> ```
