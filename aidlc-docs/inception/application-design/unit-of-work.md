# Unit of Work

## Decomposition Summary
This PoC is decomposed into 5 medium-sized units of work inside a single Java application. The units follow the top-level pipeline, but split large analysis responsibilities and preserve the pre-LLM and post-LLM boundaries.

## Unit UOW-01: Repository Ingestion
- Scope:
  - GitHub repository URL intake
  - temporary workspace preparation
  - source root and build layout detection
  - static-analysis-only safety guardrails
- Primary stories:
  - `S-01`
- Responsibilities:
  - produce `RepositorySource`
  - isolate clone/fetch and workspace preparation concerns
  - expose analysis-ready source metadata
- Main components:
  - `RepositoryIngestionService`
  - `RepositoryFetcherPort`
  - `WorkspacePreparerPort`

## Unit UOW-02: Endpoint and Type Analysis
- Scope:
  - Spring endpoint extraction
  - request binding extraction for `HEADER`, `PATH`, `QUERY`, `BODY`
  - response binding extraction for `RESPONSE`
  - shared type and source trace resolution
- Primary stories:
  - `S-02`
- Responsibilities:
  - produce `ApiEndpoint` inventory and endpoint context
  - resolve request/response types for downstream validation extraction
  - establish reusable `TypeResolver` and `SourceTraceResolver`
- Main components:
  - `SpringStaticScanService`
  - `EndpointExtractor`
  - `TypeResolver`
  - `SourceTraceResolver`

## Unit UOW-03: Validation Candidate Extraction
- Scope:
  - direct annotation-based condition mapping
  - unsupported/custom annotation fallback to `ValidationCandidate`
  - validator-layer candidate extraction
  - service/domain hint extraction
- Primary stories:
  - `S-03`
  - `S-04`
  - `S-05`
- Responsibilities:
  - produce direct `ApiConditionDraft` results for standard Bean Validation
  - produce `ValidationCandidate` inventory for custom/derived validation logic
  - preserve evidence and source trace for every extracted rule candidate
- Main components:
  - `ValidationExtractionService`
  - `AnnotationConditionExtractor`
  - `ValidatorCandidateExtractor`
  - `ServiceHintExtractor`
  - shared `TypeResolver`
  - shared `SourceTraceResolver`

## Unit UOW-04: Candidate Chunk and Normalization
- Scope:
  - candidate chunk generation
  - invalid candidate filtering
  - prompt building
  - LLM invocation
  - response schema/semantic validation
- Primary stories:
  - `S-06`
  - `S-07`
- Responsibilities:
  - convert raw extracted candidates into guarded LLM input units
  - enforce `candidateId`, evidence, and source trace validity before LLM
  - normalize accepted LLM outputs into validated structured results
- Main components:
  - `CandidateChunkGenerationService`
  - `CandidateChunkGenerator`
  - `CandidateChunkValidator`
  - `CandidateIdGenerator`
  - `NormalizationService`
  - `PromptBuilder`
  - `LlmNormalizationAdapter`
  - `LlmResponseValidator`

## Unit UOW-05: Contract Assembly and Output
- Scope:
  - final `ApiCondition` assembly
  - OpenAPI assembly
  - output persistence
  - demo/review artifact preparation
- Primary stories:
  - `S-08`
- Responsibilities:
  - merge direct conditions and validated normalized results
  - emit OpenAPI + ApiCondition JSON outputs
  - isolate persistence strategy behind `ResultStorePort`
- Main components:
  - `ContractAssemblyService`
  - `ApiConditionAssembler`
  - `OpenApiAssembler`
  - `OutputService`
  - `ResultStorePort`
  - `FileResultStoreAdapter`

## Boundary Decisions
- `S-02` is kept separate from validation extraction to keep endpoint/type context stable before rule extraction begins.
- `S-03` to `S-05` are grouped into one unit because they all build pre-LLM validation evidence and share type/trace infrastructure.
- `S-06` and `S-07` are grouped because candidate chunk generation is the hard boundary immediately before normalization, and both areas are tightly coupled around LLM safety and validation.
- `S-08` is isolated because final contract materialization and persistence should not depend on raw normalization internals.

## Ownership Guidance
- UOW-02 and UOW-03 can be worked on in close sequence, with some later parallelism after shared support contracts stabilize.
- UOW-04 is a natural ownership boundary for LLM-facing logic.
- UOW-05 is a natural ownership boundary for output and review-facing contract materialization.

## Greenfield Code Organization Strategy
- Single application/module implementation
- Package direction:
  - `ingestion`
  - `analysis`
  - `candidate`
  - `normalization`
  - `contract`
  - `output`
  - `common`
- Internal package roles:
  - `application`
  - `domain`
  - `port`
  - `adapter`
  - `support`
