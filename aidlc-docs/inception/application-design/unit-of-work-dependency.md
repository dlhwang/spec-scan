# Unit of Work Dependency

## Execution Order
1. `UOW-01` Repository Ingestion
2. `UOW-02` Endpoint and Type Analysis
3. `UOW-03` Validation Candidate Extraction
4. `UOW-04` Candidate Chunk and Normalization
5. `UOW-05` Contract Assembly and Output

## Dependency Matrix

| Unit | Depends On | Dependency Type | Why |
| --- | --- | --- | --- |
| `UOW-01` | none | none | entry point unit |
| `UOW-02` | `UOW-01` | hard | requires prepared repository source |
| `UOW-03` | `UOW-02` | hard | requires endpoint, type, and source trace context |
| `UOW-04` | `UOW-03` | hard | requires direct candidates and evidence-bearing candidate inventory |
| `UOW-05` | `UOW-02`, `UOW-03`, `UOW-04` | hard | assembles final contract from endpoint metadata, direct conditions, and validated normalization results |

## Shared Assets and Coupling
- Shared support contracts:
  - `TypeResolver`
  - `SourceTraceResolver`
  - endpoint context model
  - candidate identity rules
- Stable handoff models:
  - `RepositorySource`
  - `ApiEndpoint`
  - `ApiConditionDraft`
  - `ValidationCandidate`
  - `CandidateChunk`
  - `ValidatedNormalizationResult`
  - `ApiCondition`

## Parallelism Opportunities
- After `UOW-02` core endpoint and type contracts are stable:
  - parts of `UOW-03` can proceed independently by extractor
  - scaffolding for `UOW-05` output models can begin in parallel
- After `UOW-03` candidate schemas are stable:
  - `UOW-04` can proceed independently from file output implementation details

## Risk Concentration
- Highest coupling:
  - `UOW-02` -> `UOW-03`
  - `UOW-03` -> `UOW-04`
- Highest semantic risk:
  - `UOW-04`, because chunk validity and LLM response validation determine downstream quality
- Highest integration visibility:
  - `UOW-05`, because OpenAPI and ApiCondition artifacts are the final review surface

## Dependency Guardrails
- `UOW-04` must not bypass `ValidationCandidate` and operate directly on arbitrary raw AST snippets.
- `UOW-05` must consume validated normalization results, not raw LLM responses.
- `UOW-03` must preserve enough evidence and trace to make `UOW-04` deterministic and reviewable.

## Verification Notes
- Every downstream unit should be testable with fixtures produced by upstream handoff models.
- Partial failures must remain attached to the producing unit instead of being silently collapsed into later stages.
