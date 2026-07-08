# Components

## Design Style
- Top-level structure: pipeline-first
- Internal structure: domain model + port/interface oriented
- Packaging style: hybrid feature/pipeline packages with internal role separation

## Core Domain Models
- `RepositorySource`: cloned repository location, source roots, build hints, ingestion metadata
- `ApiEndpoint`: HTTP method, resolved path, controller method trace, request bindings, response binding
- `ValidationCandidate`: raw validation evidence extracted from custom annotation, validator, or service/domain logic
- `CandidateChunk`: LLM input unit with endpoint context, candidateId, evidence, source trace, related fields
- `LlmNormalizationResult`: validated LLM output mapped by candidateId with operator, expected, confidence, reason
- `ApiCondition`: final structured validation condition with location, operator, expected, evidence, confidence, trace
- `OpenApiContract`: generated OpenAPI document model or serialized representation

## Pipeline Components

### 1. Repository Ingestion Component
- Purpose: accept GitHub repository URL and prepare static analysis workspace
- Responsibilities:
  - validate repository input
  - clone or hydrate repository into temporary workspace
  - detect source roots and build layout
  - capture ingestion metadata without executing project code
- Key ports:
  - `RepositoryFetcherPort`
  - `WorkspacePreparerPort`

### 2. Spring Static Scan Component
- Purpose: build endpoint and type inventory from Spring codebase
- Responsibilities:
  - locate controllers and request mappings
  - resolve request parameter locations: `HEADER`, `PATH`, `QUERY`, `BODY`
  - resolve response schema as separate `RESPONSE` binding
  - collect DTO and method signature references for later extraction
- Internal subcomponents:
  - `EndpointExtractor`
  - `TypeResolver`
  - `SourceTraceResolver`

### 3. Validation Extraction Component
- Purpose: extract validation evidence in three layers
- Responsibilities:
  - map standard Bean Validation annotations directly to `ApiCondition` candidates
  - capture unsupported/custom annotations as `ValidationCandidate`
  - extract validator-layer evidence from `ConstraintValidator`, Spring `Validator`, `@InitBinder`, `rejectValue`, `reject`
  - extract service/domain hints from `if` conditions, `BusinessException`, `ErrorCode`, and method chain context
- Internal subcomponents:
  - `AnnotationConditionExtractor`
  - `ValidatorCandidateExtractor`
  - `ServiceHintExtractor`
  - shared `TypeResolver`
  - shared `SourceTraceResolver`

### 4. Candidate Chunk Generation Component
- Purpose: isolate LLM pre-processing responsibility
- Responsibilities:
  - assemble small JSON chunks by endpoint or validator method scope
  - assign stable `candidateId`
  - include endpoint context, source trace, evidence, related target field metadata
  - reject invalid candidates before LLM handoff
- Validation rules:
  - no missing evidence
  - no missing source trace
  - no duplicate `candidateId`

### 5. LLM Normalization Component
- Purpose: normalize raw validation evidence into structured rules
- Responsibilities:
  - build prompt payload from `CandidateChunk`
  - invoke LLM adapter
  - validate raw response against schema and semantic guardrails
  - emit accepted, retried, or rejected normalization outcomes
- Internal subcomponents:
  - `PromptBuilder`
  - `LlmNormalizationAdapter`
  - `LlmResponseValidator`

### 6. Contract Assembly Component
- Purpose: build final `ApiCondition` set and OpenAPI output
- Responsibilities:
  - merge static annotation results and validated LLM normalization results
  - preserve evidence, confidence, source trace, and normalization provenance
  - assemble endpoint-level validation maps
  - create OpenAPI output and internal JSON output

### 7. Output Component
- Purpose: persist PoC outputs without hard-wiring storage strategy into application services
- Responsibilities:
  - write OpenAPI JSON/YAML
  - write `ApiCondition` JSON
  - optionally write candidate/chunk/debug artifacts for review
- Ports:
  - `ResultStorePort`
- Initial adapter:
  - `FileResultStoreAdapter`

## Cross-Cutting Support Components

### TypeResolver
- Shared type lookup and field resolution utility used by endpoint and validation extractors

### SourceTraceResolver
- Shared source trace builder for class, method, file, and line mapping

### SchemaValidationSupport
- JSON schema and enum validation support for normalization responses

### CandidateIdGenerator
- Deterministic candidate identity generation based on endpoint, extractor source, and trace

## Proposed Package Shape
```text
com.autooas
  ingestion/
  analysis/
  candidate/
  normalization/
  contract/
  output/
  common/
```

Within each feature package:

```text
application/
domain/
port/
adapter/
support/
```
