# Unit of Work Plan

## Goal
GitHub Repository URL 기반 Spring API Contract 추출 PoC를 구현 가능한 단위로 분해하고, 각 단위의 책임, 의존 관계, 스토리 매핑을 확정한다.

## Execution Checklist
- [x] requirements, stories, application design를 기준으로 unit 분해 범위를 확인한다.
- [x] unit 경계 기준을 확정한다.
- [x] `unit-of-work.md`를 생성한다.
- [x] `unit-of-work-dependency.md`를 생성한다.
- [x] `unit-of-work-story-map.md`를 생성한다.
- [x] 모든 story가 최소 1개 unit에 할당되었는지 검증한다.
- [x] unit 간 의존 관계와 순서를 검증한다.

## Decomposition Context
- 이번 PoC는 독립 배포 서비스 분리가 아니라, 하나의 Java CLI/애플리케이션 내부를 구현 단위로 쪼개는 monolith형 unit 분해에 가깝다.
- 상위 파이프라인은 `ingestion -> analysis -> candidate -> normalization -> contract -> output` 이다.
- 핵심 경계는 `CandidateChunk`를 기준으로 정적 분석/후처리와 LLM 연계 이후를 나누는 것이다.
- 표준 Bean Validation direct mapping과 LLM normalization candidate 흐름은 분리되어야 한다.

## Mandatory Artifacts
- [x] Generate `aidlc-docs/inception/application-design/unit-of-work.md` with unit definitions and responsibilities
- [x] Generate `aidlc-docs/inception/application-design/unit-of-work-dependency.md` with dependency matrix
- [x] Generate `aidlc-docs/inception/application-design/unit-of-work-story-map.md` mapping stories to units
- [x] Validate unit boundaries and dependencies
- [x] Ensure all stories are assigned to units

## Planning Questions

## Question 1
story를 어떤 기준으로 unit에 묶을까요?

A) pipeline 단계 기준

- ingestion, analysis, extraction, normalization, output 순서대로 unit을 자른다.

B) story 난이도/크기 기준

- 비슷한 구현 난이도와 작업량을 기준으로 unit을 자른다.

C) hybrid

- 기본은 pipeline/책임 경계로 자르되, 너무 큰 영역은 story 기준으로 추가 분해한다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) hybrid

## Question 2
의존성이 강한 정적 분석 계층을 어디까지 하나의 unit으로 묶을까요?

A) `S-02`부터 `S-05`까지 하나의 분석 unit으로 묶는다

- endpoint extraction, annotation extraction, validator extraction, service hint extraction을 한 번에 구현한다.

B) endpoint/type extraction과 validation extraction 계층을 분리한다

- `S-02`는 별도 unit
- `S-03`~`S-05`는 validation 계층 unit들로 분리한다

C) 더 세분화한다

- `S-02`, `S-03`, `S-04`, `S-05`를 각각 독립 unit 또는 거의 독립 unit으로 다룬다

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) endpoint/type extraction과 validation extraction 계층 분리

## Question 3
team/ownership 관점에서 이번 PoC의 unit은 어느 수준으로 잡을까요?

A) 한 사람이 순차 구현하는 단위면 충분하다

- ownership보다 구현 순서와 검증 편의성을 우선한다.

B) 이후 병렬 작업이 가능하도록 ownership 경계를 일부 고려한다

- 예를 들어 analysis, normalization, output을 나눠 협업 가능하게 한다.

C) 처음부터 병렬 팀 작업 기준으로 강하게 나눈다

- 이번 PoC 규모에서는 다소 과할 수 있다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) 이후 병렬 작업 가능하도록 ownership 경계 일부 고려

## Question 4
business/domain 경계 측면에서 후보/조건 모델을 다루는 unit을 어떻게 볼까요?

A) `ValidationCandidate`, `CandidateChunk`, `ApiCondition`을 하나의 계약 unit으로 묶는다

- candidate lifecycle 전체를 하나의 핵심 도메인으로 본다.

B) candidate 생성 계층과 final contract 조립 계층을 분리한다

- candidate 쪽과 contract/output 쪽의 책임을 다르게 본다.

C) normalization을 중심으로 candidate 전후를 나눈다

- pre-LLM unit과 post-LLM unit 경계를 가장 중요하게 본다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) candidate 생성 계층과 final contract 조립 계층 분리

## Question 5
기술적 관점에서 LLM 연계와 출력 계층을 하나의 unit으로 둘까요, 분리할까요?

A) 하나로 묶는다

- normalization과 output을 한 번에 처리한다.

B) 분리한다

- LLM normalization unit과 contract/output unit을 나눈다.

C) normalization은 별도 unit, output은 contract assembly와 함께 묶는다

- LLM 실패/재시도 책임과 최종 산출물 책임을 분리한다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) normalization은 별도 unit, output은 contract assembly와 함께 묶음

## Question 6
이번 PoC에서 최종 unit 개수는 어느 정도가 적절할까요?

A) 3개 이하의 큰 unit

B) 4~6개의 중간 unit

C) 7개 이상의 작은 unit

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) 4~6개의 중간 unit

## Approval Notes
- 모든 `[Answer]:` 항목이 채워진 뒤에만 unit 산출물 생성을 진행한다.
- 답변이 모호하면 follow-up question을 이 문서에 추가한다.
- 답변이 완료되면 이를 기준으로 `unit-of-work.md`, `unit-of-work-dependency.md`, `unit-of-work-story-map.md`를 생성한다.

## Answer Analysis
- story grouping은 pipeline/책임 경계를 기본으로 하되, 큰 영역은 story 기준으로 추가 분해하는 hybrid 방식으로 확정되었다.
- `S-02` endpoint/type extraction과 `S-03`~`S-05` validation extraction 계층은 분리한다.
- ownership은 단일 구현자 기준에만 묶지 않고, 이후 병렬 작업이 가능하도록 analysis, normalization, contract/output 경계를 일부 고려한다.
- candidate 생성 계층과 final contract assembly 계층은 별도 unit으로 분리한다.
- normalization은 독립 unit으로 유지하고, output은 contract assembly와 함께 묶는다.
- 전체 unit 개수는 4~6개의 중간 크기 unit이 적절하다는 방향이 확정되었다.
- 답변 간 충돌이나 모호성은 없어 follow-up question은 필요하지 않다.
