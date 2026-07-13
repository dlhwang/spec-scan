# Application Design

## Overview
This PoC uses a hybrid architecture. The top-level structure follows the end-to-end pipeline so the extraction flow is immediately visible. Inside each stage, responsibilities are divided around domain models and port/adapter boundaries so the design can expand without collapsing into one large analysis service.

The pipeline is:

1. Source Ingestion
2. Static Scan
3. Validation Extraction
4. Candidate Chunk Generation
5. LLM Normalization
6. Contract Assembly (Precondition/Assertion classification & AUTH/RESOURCE Filtering)
7. Output Persistence

## Architectural Decisions
- Use Java as the primary implementation language.
- Keep analysis static-only; no target repository code execution.
- Separate standard Bean Validation mapping from candidate-based LLM normalization.
- Keep candidate chunk generation as its own bounded stage before LLM invocation.
- Split LLM integration into `PromptBuilder`, `LlmNormalizationAdapter`, and `LlmResponseValidator`.
- Introduce `ResultStorePort` even though the first implementation is file-based.
- Use shared `TypeResolver` and `SourceTraceResolver` to avoid duplicated trace logic across extractors.
- **Contract Model Separation & Reuse**: Reuse the existing `ApiCondition` field schema, but add a `conditionType` (PRECONDITION | ASSERTION) discriminator to conceptually separate request preconditions and response assertions.
- **Execution Test Gating & Filtering**: Explicitly filter out and exclude AUTH (e.g., currentUser), RESOURCE (e.g., order.state), repository existence checks, and optimistic lock validations from the final `requestPreconditions` and `responseAssertions` during the assembly stage, while preserving them in internal static analysis evidence trace logs.

## Component Summary

### Ingestion
- Accepts GitHub repository URL.
- Produces `RepositorySource`.
- Hides clone/workspace mechanics behind ports.

### Analysis
- Extracts Spring endpoints, HTTP methods, paths, and request/response type bindings.
- Resolves request parameter locations as `HEADER`, `PATH`, `QUERY`, `BODY` (mapping to `PRECONDITION`).
- Resolves response validation as `STATUS`, `HEADER`, `BODY` (mapping to `ASSERTION`).

### Validation
- Standard annotations become direct `ApiConditionDraft` results.
- Unsupported/custom annotations become `ValidationCandidate`.
- Validator layer and service/domain hints also become `ValidationCandidate` (retaining trace logic).

### Candidate Boundary
- `CandidateChunk` is the only allowed LLM input unit.
- Missing evidence, missing trace, or invalid IDs are filtered before normalization.

### Normalization
- Prompt generation, model invocation, and response validation are explicit components.
- Invalid LLM outputs are retried or rejected without contaminating direct extraction results.

### Assembly and Output
- Final `ApiCondition` results are classified into `requestPreconditions` and `responseAssertions` using the `conditionType` field.
- During assembly, the `AUTH` / `RESOURCE` exclusion filter is run.
- OpenAPI and the categorized ApiCondition JSON are both emitted.
- File output is the initial adapter behind `ResultStorePort`.

## Main Services
- `ContractExtractionApplicationService`
- `RepositoryIngestionService`
- `SpringStaticScanService`
- `ValidationExtractionService`
- `CandidateChunkGenerationService`
- `NormalizationService`
- `ContractAssemblyService` (handles filtering and classification)
- `OutputService`

## Main Domain Models
- `RepositorySource`
- `ApiEndpoint`
- `ValidationCandidate`
- `CandidateChunk`
- `LlmNormalizationResult`
- `ApiCondition` (includes `conditionType: PRECONDITION | ASSERTION`)
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
- Excluded AUTH/RESOURCE nodes remain traceable in evidence logs for developers' reference, but are excluded from the test assertion model.
- Partial failures are preserved as review artifacts instead of being silently dropped.

## Review Focus
- Is the `conditionType` distinction processed correctly in `ContractAssemblyService`?
- Are `AUTH` and `RESOURCE` constraints successfully stripped out of the final JSON output while preserving evidence trace logs?
- Is the response assertion schema (`STATUS`, `HEADER`, `BODY`) granular enough to serve as a reliable test oracle?
- Are `TypeResolver` and `SourceTraceResolver` scoped correctly as shared support components?

