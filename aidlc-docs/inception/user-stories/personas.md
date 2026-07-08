# Personas

## Persona P-01: PoC Developer

### Summary
GitHub Repository URL 기반 Spring API Contract 추출 PoC를 직접 설계하고 구현하는 개발자다.

### Goals
- GitHub 저장소를 입력으로 받아 정적 분석 파이프라인이 끝까지 동작하는지 확인하고 싶다.
- endpoint, DTO, validator, service hint를 하나의 분석 흐름으로 연결하고 싶다.
- LLM 정규화 결과를 코드 기반 evidence와 함께 추적 가능한 형태로 저장하고 싶다.
- OpenAPI와 `ApiCondition` JSON을 동시에 생성하는 PoC를 빠르게 검증하고 싶다.

### Frustrations
- 분석 대상 저장소를 가져오는 단계에서 clone 실패나 구조 차이로 파이프라인이 쉽게 깨질 수 있다.
- validator bean, custom constraint, service/domain hint처럼 분산된 validation 근거를 한 번에 묶기 어렵다.
- LLM 응답이 schema를 벗어나거나 candidate trace를 잃으면 결과 신뢰도가 급격히 떨어진다.

### Needs
- 구현 가능한 feature 단위 story
- 실패 시나리오까지 포함한 acceptance criteria
- 테스트 evidence와 데모 확인 포인트가 포함된 verification expectations

### Relevant Stories
- S-01
- S-02
- S-03
- S-04
- S-05
- S-06
- S-07
- S-08

## Persona P-02: Technical Lead / Architect

### Summary
PoC의 구조적 타당성과 확장 가능성을 검토하는 기술 리드 또는 아키텍트다.

### Goals
- 이 PoC가 단순 데모가 아니라 이후 제품화 가능한 구조인지 판단하고 싶다.
- 정적 분석기와 LLM의 책임 경계가 분명한지 확인하고 싶다.
- evidence, confidence, source trace가 남아 결과를 설명 가능한지 확인하고 싶다.
- Java 기반 구현이 Spring 분석 문제에 적합한지 검토하고 싶다.

### Frustrations
- 결과가 OpenAPI만 생성하고 validation semantics를 설명하지 못하면 PoC 가치가 낮아진다.
- candidate chunk와 LLM normalization 경계가 모호하면 재현성과 유지보수성이 떨어진다.
- 실패 처리, retry, cache, schema validation 계획이 없으면 운영 가능성을 판단하기 어렵다.

### Needs
- 상위 epic 수준의 사용자 가치 흐름
- story별 데모 포인트와 검증 산출물
- 구조적 리스크를 드러내는 acceptance criteria

### Relevant Stories
- S-01
- S-03
- S-04
- S-05
- S-06
- S-07
- S-08
