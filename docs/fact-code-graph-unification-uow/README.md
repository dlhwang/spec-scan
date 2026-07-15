# FactCodeGraph 통합 Unit of Work

## 목표

`FactCodeGraph`를 실행 흐름 기반 규칙의 단일 내부 그래프로 사용하고 기존 정규화 파이프라인을 제거함.

최종 흐름은 아래와 같음.

```text
RepositorySource
├─ DefaultFactCodeGraphBuilder
│  └─ FactGraphBuildResult
│     └─ GraphRuleEngine
│        └─ BusinessRuleCandidate + RuleEffect
│           ├─ REQUEST_REQUIREMENT
│           ├─ RESPONSE_GUARANTEE
│           └─ BUSINESS_RESTRICTION
├─ ApiEndpoint.requestBindings
│  └─ RequestBindingConditionAdapter
└─ RuleOutputService
   └─ EndpointRuleOutput
```

## 제거 목표

- `ValidationEvidenceGraph` 및 builder
- `NormalizationService`
- `RuleBasedConditionNormalizer`
- `CandidateChunk` 및 generator/validator
- 서비스 의미를 source snippet 문자열로 재해석하는 로직

## UoW 실행 순서

| 순서 | UoW | 핵심 결과 |
|---|---|---|
| 1 | 책임 및 기준선 고정 | 기존 정규화 책임과 출력 소비처를 테스트로 고정 |
| 2 | GraphRule 이전 | SERVICE_HINT와 실행 흐름 validator를 개별 규칙으로 이전 |
| 3 | 요청 제약 이전 | DTO·애너테이션 제약을 request binding 경로로 이전 |
| 4 | 단일 그래프 연결 | FactGraph를 한 번 생성하여 출력 경로에서 재사용 |
| 5 | 출력·산출물 이전 | structured JSON과 evidence artifact를 신규 모델에 연결 |
| 6 | 레거시 제거 | 정규화, chunk, 기존 graph 타입 완전 삭제 |

## 의존성

```text
UoW 01
 ├─→ UoW 02 ─┐
 └─→ UoW 03 ─┼─→ UoW 04 → UoW 05 → UoW 06
              ┘
```

UoW 02와 UoW 03은 UoW 01 완료 후 병렬 수행 가능함.

## 공통 원칙

- 실행 흐름 기반 규칙은 `GraphRule`에서 처리함.
- 모든 `GraphRule`은 `BusinessRuleCandidate`의 `RuleEffect`를 명시함.
- `BusinessRuleCategory`는 의미 분류, `RuleEffect`는 출력 방향으로 사용함.
- `RuleEffect`에 따라 request, response, business restriction을 상호 배타적으로 분기함.
- DTO 및 애너테이션 기반 제약은 request binding adapter에서 처리함.
- snippet 문자열 포함 여부만으로 비즈니스 의미를 판정하지 않음.
- endpoint에서 evidence fact까지 도달 가능한 결과만 출력함.
- 표현할 수 없는 조건을 유사 operator로 약화하지 않음.
- 프로젝트 전용 규칙은 `PROJECT_EXTENSION` 계층으로 분리함.
- 신규 범용 그래프나 신규 normalizer 계층을 만들지 않음.
- 동일 입력에서 node ID와 출력 순서가 결정적이어야 함.

## 공통 병합 게이트

- 해당 UoW의 단위 및 통합 테스트가 통과함.
- 기존 golden 결과의 의도하지 않은 변화가 없음.
- 신규 출력은 fact evidence 또는 request binding evidence를 가짐.
- request effect가 response 또는 excluded 출력에 섞이지 않음.
- response effect가 request 출력에 섞이지 않음.
- business restriction은 allowlist, template 및 evidence gate를 통과해야 함.
- 지원하지 않는 조건은 diagnostic으로 남기고 추정 출력하지 않음.
- 다음 UoW가 레거시와 신규 경로를 동시에 장기간 유지하도록 만들지 않음.

## 원본 설계 문서

- [FactCodeGraph 통합 구현 계획](../fact-code-graph-unification-plan.md)
