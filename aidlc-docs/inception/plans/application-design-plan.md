# Application Design Plan

## Goal
GitHub Repository URL 기반 Spring API Contract 추출 PoC의 상위 컴포넌트, 서비스 계층, 컴포넌트 간 의존 관계, 그리고 LLM 정규화 연계 인터페이스를 명확히 정의한다.

## Execution Checklist
- [ ] 요구사항과 사용자 스토리에서 설계 범위를 확인한다.
- [ ] source ingestion, static scan, validation extraction, chunk generation, normalization, assembly를 기준으로 상위 컴포넌트 경계를 정의한다.
- [ ] `components.md`를 생성한다.
- [ ] `component-methods.md`를 생성한다.
- [ ] `services.md`를 생성한다.
- [ ] `component-dependency.md`를 생성한다.
- [ ] `application-design.md`를 생성한다.
- [ ] 설계 산출물 간 용어, 책임, 데이터 흐름 일관성을 검증한다.

## Design Scope Summary
- 입력은 GitHub Repository URL이다.
- 분석 대상은 Spring 기반 Java repository이며, 런타임 실행 없이 정적 분석만 수행한다.
- 핵심 파이프라인은 Source Ingestion -> Static Scan -> Candidate Extraction -> Candidate Chunk Generation -> LLM Normalization -> ApiCondition/OpenAPI Assembly 이다.
- validation은 annotation 기반, validator 계층 기반, service/domain hint 기반의 3계층으로 나눈다.
- 표준 Bean Validation은 `ApiCondition`으로 바로 매핑하고, unsupported/custom annotation 및 추론이 필요한 로직은 `ValidationCandidate`를 거쳐 LLM 정규화로 보낸다.
- candidate chunk generation은 독립 책임으로 설계한다.

## Mandatory Artifacts
- [ ] Generate `aidlc-docs/inception/application-design/components.md` with component definitions and responsibilities
- [ ] Generate `aidlc-docs/inception/application-design/component-methods.md` with method signatures, inputs, outputs, and high-level purpose
- [ ] Generate `aidlc-docs/inception/application-design/services.md` with service orchestration design
- [ ] Generate `aidlc-docs/inception/application-design/component-dependency.md` with dependency relationships and communication patterns
- [ ] Generate `aidlc-docs/inception/application-design/application-design.md` as the consolidated design document

## Planning Questions

## Question 1
컴포넌트 구조의 기본 스타일을 무엇으로 둘까요?

A) Pipeline-first

- ingestion -> scan -> extract -> chunk -> normalize -> assemble 단계 컴포넌트를 우선 고정한다.
- 장점: 이번 PoC의 end-to-end 흐름과 가장 직접적으로 맞다.

B) Domain-first

- repository, endpoint, candidate, condition, contract 같은 도메인 모델 중심으로 컴포넌트를 나눈다.
- 장점: 장기 확장에는 유리하지만 현재 PoC에서는 흐름 책임이 흐려질 수 있다.

C) Hybrid

- 상위는 pipeline 기준, 내부는 domain model과 port/interface 기준으로 설계한다.
- 장점: PoC 흐름과 확장성을 모두 잡기 쉽다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) Hybrid

상위 구조는 pipeline-first로 둔다. 이번 PoC의 핵심 흐름이 GitHub Repository URL 입력 -> Source Ingestion -> Static Scan -> Candidate Extraction -> Candidate Chunk Generation -> LLM Normalization -> ApiCondition/OpenAPI Assembly 이므로, end-to-end 처리 흐름이 먼저 보여야 한다.

다만 내부 설계는 domain model과 port/interface 기준으로 나눈다. `ApiEndpoint`, `ValidationCandidate`, `CandidateChunk`, `LlmNormalizationResult`, `ApiCondition` 같은 핵심 도메인 모델이 이후 구현과 검증의 중심이 되기 때문이다.

즉, 상위는 pipeline 기준으로 이해하기 쉽게 만들고, 내부는 도메인 모델과 인터페이스 기준으로 확장 가능하게 설계한다.

## Question 2
LLM 연계 컴포넌트 경계를 어디까지 분리할까요?

A) Normalization Adapter만 분리

- candidate chunk 입력과 normalized result 출력만 담당한다.
- 프롬프트 구성, schema validation, retry도 adapter 내부에 둔다.

B) Adapter + Response Validator 분리

- 호출 책임과 응답 검증 책임을 분리한다.
- 실패 처리와 schema 검증을 독립 테스트하기 쉽다.

C) Adapter + Prompt Builder + Response Validator 분리

- LLM 호출 단위를 가장 명확히 나눈다.
- PoC치고는 다소 세분화될 수 있지만 책임 경계는 가장 선명하다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) Adapter + Prompt Builder + Response Validator 분리

LLM 호출은 이번 PoC의 핵심 차별점이므로 책임 경계를 명확히 나눈다.

`PromptBuilder`는 candidate chunk를 LLM 입력 schema에 맞게 구성한다.
`LlmNormalizationAdapter`는 실제 LLM 호출과 raw response 수신만 담당한다.
`LlmResponseValidator`는 JSON schema validation, enum validation, candidateId validation, evidence 없는 rule 생성 차단을 담당한다.

PoC치고는 다소 세분화되어 보일 수 있지만, LLM 응답은 비결정성이 있고 실패 가능성이 높기 때문에 호출, prompt 구성, 응답 검증을 분리하는 편이 테스트와 디버깅에 유리하다.

## Question 3
Spring 정적 분석 엔진의 내부 구성은 어느 수준으로 나눌까요?

A) 단일 `SpringAnalysisService`

- endpoint, DTO, validator, service hint 추출을 하나의 분석 서비스가 수행한다.
- 초기 구현은 빠르지만 story별 검증 경계가 약해진다.

B) `EndpointExtractor`, `AnnotationConditionExtractor`, `ValidatorCandidateExtractor`, `ServiceHintExtractor`로 분리

- 현재 story 경계와 가장 잘 맞는다.
- chunk generation 이전 책임 경계가 명확해진다.

C) B안 + `TypeResolver`와 `SourceTraceResolver`를 공통 지원 컴포넌트로 추가 분리

- 재사용성과 테스트성은 좋아진다.
- 초기 설계 문서가 조금 더 커진다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) B안 + `TypeResolver`와 `SourceTraceResolver`를 공통 지원 컴포넌트로 추가 분리

정적 분석은 story와 요구사항 경계에 맞춰 `EndpointExtractor`, `AnnotationConditionExtractor`, `ValidatorCandidateExtractor`, `ServiceHintExtractor`로 나눈다.

추가로 `TypeResolver`와 `SourceTraceResolver`를 공통 컴포넌트로 분리한다. endpoint, DTO, validator, service hint 분석 모두 타입 해석과 source trace가 필요하기 때문이다.

이번 PoC는 단순 endpoint 추출이 아니라 evidence, confidence, source trace를 유지해야 하므로, source trace를 각 extractor 내부에 흩뿌리면 설계가 빠르게 지저분해진다. 공통 resolver로 분리하는 것이 맞다.

## Question 4
결과 저장 인터페이스를 PoC에서 어느 수준으로 추상화할까요?

A) 파일 출력만 우선 지원

- OpenAPI JSON/YAML과 ApiCondition JSON 파일 생성에 집중한다.
- DB 저장은 이후 확장 포인트만 남긴다.

B) 파일 출력 + 저장 포트 정의

- 현재 구현은 파일 기반이지만 `ResultStorePort` 같은 인터페이스를 둬서 DB 확장을 대비한다.
- PoC 범위와 확장성의 균형이 좋다.

C) 파일 출력 + DB 저장 둘 다 설계에 포함

- 장기 목표와는 맞지만 이번 PoC 구현 범위는 커진다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: B) 파일 출력 + 저장 포트 정의

PoC 구현은 파일 출력에 집중한다. 결과물은 OpenAPI JSON/YAML과 ApiCondition JSON 파일로 확인한다.

다만 설계에는 `ResultStorePort`를 둔다. 현재 adapter는 file-based implementation으로 두고, 이후 DB 저장이나 API 서버 연동이 필요해지면 port 구현체만 추가할 수 있게 한다.

즉, 지금은 파일로 빠르게 증명하되, 저장 방식이 application service에 박히지 않도록 한다.

## Question 5
패키지 구조를 어떤 기준으로 고정할까요?

A) layer 기준

- `ingestion`, `analysis`, `normalization`, `output`, `common`

B) feature 기준

- `repository`, `endpoint`, `validation`, `chunk`, `llm`, `contract`

C) hybrid

- 상위는 feature 기준, 내부 구현은 layer/role 기준으로 세분화한다.

X) Other (please describe after [Answer]: tag below)

[Answer]:
[Answer]: C) hybrid

상위 패키지는 feature/pipeline 기준으로 나눈다.

예시:

* `ingestion`
* `analysis`
* `candidate`
* `normalization`
* `contract`
* `output`
* `common`

각 feature 내부는 role/layer 기준으로 세분화한다.

예시:

* `application`
* `domain`
* `port`
* `adapter`
* `support`

이렇게 하면 PoC 흐름을 패키지에서 바로 읽을 수 있고, 동시에 JavaParser, LLM client, file output 같은 외부 기술 의존성은 adapter로 밀어낼 수 있다.

## Approval Notes
- 위 `[Answer]:` 항목이 모두 채워진 뒤에만 application design 산출물 생성으로 진행한다.
- 답변에 모호한 표현이 있으면 follow-up question을 같은 문서에 추가한다.
- 답변이 완료되면 이를 기준으로 `components.md`, `component-methods.md`, `services.md`, `component-dependency.md`, `application-design.md`를 생성한다.

## Answer Analysis
- 각 질문은 명확한 단일 선택 또는 구체적 하이브리드 기준으로 답변되었다.
- pipeline-first 상위 구조와 domain/port 중심 내부 구조의 경계가 명시되었다.
- LLM 컴포넌트는 `PromptBuilder`, `LlmNormalizationAdapter`, `LlmResponseValidator`로 분해하는 방향이 확정되었다.
- 정적 분석기는 extractor 계층과 `TypeResolver`, `SourceTraceResolver` 공통 지원 컴포넌트로 분리하는 것이 확정되었다.
- 저장 방식은 파일 기반 구현 + `ResultStorePort` 추상화로 정리되었다.
- follow-up question은 필요하지 않다.
