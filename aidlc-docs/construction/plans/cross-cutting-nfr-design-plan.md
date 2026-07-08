# Cross-Cutting NFR Design Plan

## Context
- Basis document:
  - `aidlc-docs/construction/cross-cutting/nfr-requirements/cross-cutting-nfr-requirements.md`
- Goal:
  - derive pipeline-wide non-functional design patterns and logical components before continuing more detailed NFR design work

## Execution Checklist
- [ ] cross-cutting NFR themes를 설계 패턴으로 변환한다.
- [ ] resilience, determinism, reproducibility, data minimization, failure isolation 관점의 logical component를 식별한다.
- [ ] `nfr-design-patterns.md`를 생성한다.
- [ ] `logical-components.md`를 생성한다.

## Planning Questions

## Question 1
cross-cutting NFR Design에서 가장 우선할 resilience pattern은 무엇입니까?

A) partial failure containment

B) retry + validation gating

C) A와 B를 모두 최우선으로 두고 stage별 적용 위치를 명시

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 2
reproducibility 설계의 핵심 단위는 무엇으로 둘까요?

A) run-level ID 중심

B) run-level + chunk-level identity 중심

C) B안 + artifact-level provenance까지 포함

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 3
cost/caching design은 어디까지 설계 범위에 포함할까요?

A) cache extension point만 문서화

B) inputHash/promptVersion 기반 cache key 설계 포함

C) B안 + cache hit/miss observability와 invalidation 원칙까지 포함

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Question 4
data minimization pattern은 어느 수준까지 강제할까요?

A) 일반 원칙 수준만 명시

B) artifact 종류별 저장 허용 범위를 정의

C) B안 + 금지되는 artifact 형태와 snippet retention 원칙까지 명시

X) Other (please describe after [Answer]: tag below)

[Answer]:

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 cross-cutting NFR Design 산출물 생성을 진행한다.
- 모호한 답변이 있으면 follow-up question을 같은 문서에 추가한다.
