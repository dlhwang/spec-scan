# Application Design

## Overview
This PoC uses a hybrid architecture. The top-level structure follows the end-to-end pipeline so the extraction flow is immediately visible. Inside each stage, responsibilities are divided around domain models and port/adapter boundaries so the design can expand without collapsing into one large analysis service.

The pipeline is:

1. Source Ingestion
2. Static Scan
3. Validation Extraction
4. Candidate Chunk Generation
5. LLM Normalization
6. Contract Assembly
7. Output Persistence

## Architectural Decisions
- Use Java as the primary implementation language.
- Keep analysis static-only; no target repository code execution.
- Separate standard Bean Validation mapping from candidate-based LLM normalization.
- Keep candidate chunk generation as its own bounded stage before LLM invocation.
- Split LLM integration into `PromptBuilder`, `LlmNormalizationAdapter`, and `LlmResponseValidator`.
- Introduce `ResultStorePort` even though the first implementation is file-based.
- Use shared `TypeResolver` and `SourceTraceResolver` to avoid duplicated trace logic across extractors.

## Component Summary

### Ingestion
- Accepts GitHub repository URL.
- Produces `RepositorySource`.
- Hides clone/workspace mechanics behind ports.

### Analysis
- Extracts Spring endpoints and request/response bindings.
- Resolves request locations as `HEADER`, `PATH`, `QUERY`, `BODY`.
- Resolves response binding separately as `RESPONSE`.

### Validation
- Standard annotations become direct `ApiConditionDraft` results.
- Unsupported/custom annotations become `ValidationCandidate`.
- Validator layer and service/domain hints also become `ValidationCandidate`.

### Candidate Boundary
- `CandidateChunk` is the only allowed LLM input unit.
- Missing evidence, missing trace, or invalid IDs are filtered before normalization.

### Normalization
- Prompt generation, model invocation, and response validation are explicit components.
- Invalid LLM outputs are retried or rejected without contaminating direct extraction results.

### Assembly and Output
- Final `ApiCondition` results preserve evidence, confidence, source trace, and provenance.
- OpenAPI and ApiCondition JSON are both emitted.
- File output is the initial adapter behind `ResultStorePort`.

## Main Services
- `ContractExtractionApplicationService`
- `RepositoryIngestionService`
- `SpringStaticScanService`
- `ValidationExtractionService`
- `CandidateChunkGenerationService`
- `NormalizationService`
- `ContractAssemblyService`
- `OutputService`

## Main Domain Models
- `RepositorySource`
- `ApiEndpoint`
- `ValidationCandidate`
- `CandidateChunk`
- `LlmNormalizationResult`
- `ApiCondition`
- `OpenApiContract`

## Package Strategy
Top-level packages:

```text
ingestion
analysis
candidate
normalization
contract
output
common
```

Within each top-level package:

```text
application
domain
port
adapter
support
```

## Traceability and Review
- Every extractor output must include source trace or a structured failure reason.
- Every normalized result must reference `candidateId`.
- Every final `ApiCondition` must be traceable either to direct annotation mapping or to validated normalization evidence.
- Partial failures are preserved as review artifacts instead of being silently dropped.

## Review Focus
- Are component boundaries consistent with the stories, especially `S-04`, `S-05`, and `S-06`?
- Is the pre-LLM safety boundary explicit enough?
- Is `ResultStorePort` sufficient for PoC plus later DB expansion?
- Are `TypeResolver` and `SourceTraceResolver` scoped correctly as shared support components?
