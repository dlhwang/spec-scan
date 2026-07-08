# Services

## Service Layer Principles
- Keep orchestration in application services.
- Keep AST traversal and extraction details inside feature-specific extractors.
- Keep external technology dependencies behind ports/adapters.
- Preserve evidence and source trace across all service boundaries.

## Primary Application Services

### `RepositoryIngestionService`
- Role: entry point for repository acquisition and workspace preparation
- Input: repository URL
- Output: `RepositorySource`
- Depends on:
  - `RepositoryFetcherPort`
  - `WorkspacePreparerPort`

### `SpringStaticScanService`
- Role: analyze repository structure and produce endpoint/type inventory
- Input: `RepositorySource`
- Output: `StaticScanResult`
- Depends on:
  - `EndpointExtractor`
  - `TypeResolver`
  - `SourceTraceResolver`

### `ValidationExtractionService`
- Role: gather validation evidence from annotation, validator, and service/domain layers
- Input: `StaticScanResult`
- Output: `ValidationExtractionResult`
- Depends on:
  - `AnnotationConditionExtractor`
  - `ValidatorCandidateExtractor`
  - `ServiceHintExtractor`
  - shared `TypeResolver`
  - shared `SourceTraceResolver`

### `CandidateChunkGenerationService`
- Role: transform extracted candidates into validated LLM input units
- Input: extracted `ValidationCandidate` collection plus endpoint context
- Output: `ChunkGenerationResult`
- Depends on:
  - `CandidateChunkGenerator`
  - `CandidateChunkValidator`
  - `CandidateIdGenerator`

### `NormalizationService`
- Role: submit valid chunks to LLM and classify results as accepted, retried, or rejected
- Input: `List<CandidateChunk>`
- Output: `NormalizationBatchResult`
- Depends on:
  - `PromptBuilder`
  - `LlmNormalizationAdapter`
  - `LlmResponseValidator`

### `ContractAssemblyService`
- Role: combine direct annotation conditions and validated normalized results into final contract outputs
- Input: endpoint catalog, direct conditions, validated normalization results
- Output: `AssemblyResult`
- Depends on:
  - `ApiConditionAssembler`
  - `OpenApiAssembler`

### `OutputService`
- Role: persist final and optional debug artifacts
- Input: `AssemblyResult`
- Output: `OutputManifest`
- Depends on:
  - `ResultStorePort`

## End-to-End Orchestration

### `ContractExtractionApplicationService`
- Role: top-level use case orchestrator for the PoC CLI
- Pipeline:
  1. call `RepositoryIngestionService`
  2. call `SpringStaticScanService`
  3. call `ValidationExtractionService`
  4. call `CandidateChunkGenerationService`
  5. call `NormalizationService`
  6. call `ContractAssemblyService`
  7. call `OutputService`
- Output:
  - OpenAPI artifact
  - ApiCondition artifact
  - optional candidate/chunk/rejection debug artifacts

## Failure Isolation Rules
- Repository clone failure stops before scan and returns structured ingestion failure.
- Static scan failures are isolated per endpoint or source file where possible.
- Invalid candidates are filtered before normalization and kept as debug output.
- LLM schema/semantic failures do not corrupt direct annotation-derived conditions.
- Partial endpoint failures are reported separately from successful outputs.

## Storage Strategy
- Current implementation target: file-based output
- Abstraction point: `ResultStorePort`
- Future extension options:
  - relational DB persistence
  - REST API submission
  - object storage archival
