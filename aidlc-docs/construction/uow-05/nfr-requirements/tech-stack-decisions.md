# Tech Stack Decisions

## Primary Language
- Use Java for `UOW-05` implementation.
- Rationale:
  - aligns with the rest of the PoC
  - keeps domain models, assembly logic, and serializer behavior in one language/runtime

## Serialization
- Use Jackson as the primary JSON serialization library.
- Rationale:
  - standard Java ecosystem choice
  - strong support for explicit models
  - suitable for deterministic JSON output when configured carefully

## Serializer Requirements
- Configure serializer determinism.
- Use stable field ordering for:
  - OpenAPI JSON
  - ApiCondition JSON
  - manifest JSON
  - debug artifacts
- Rationale:
  - stable diff output
  - reproducible regression tests
  - easier review of generated artifacts

## Output Models
- Use explicit domain/output models for:
  - OpenAPI artifact
  - ApiCondition artifact
  - normalization rejection artifact
  - conflict artifact
  - manifest artifact
  - debug/review artifact references
- Rationale:
  - improves schema stability
  - avoids ad hoc map-based payload drift

## Persistence Strategy
- Default implementation:
  - file-based artifact output
- Abstraction:
  - `ResultStorePort`
- Future expansion targets:
  - DB persistence
  - API server storage
  - external artifact repository integration

## Artifact Packaging
- Standardize artifact metadata:
  - `path`
  - `type`
  - `generatedAt`
  - `sourceRunId`
  - `endpointCount`
  - `recordCount` where applicable
- Rationale:
  - consistent downstream consumption
  - simpler manifest generation
  - easier validation and debugging

## Debug and Review Storage Policy
- Store:
  - rejected normalization artifacts
  - invalid candidate artifacts
  - failure summary artifacts
  - chunk JSON references
  - retry history summaries
  - conflict records
  - manifest
- Do not store:
  - entire source code dump
  - unnecessary repeated snippets
  - unrelated file content

## Validation and Observability Support
- Ensure the output layer can record:
  - generation duration
  - conflict count
  - skipped candidate count
  - artifact validation result
- Rationale:
  - strengthens PoC review quality
  - supports automated acceptance checks
  - improves failure triage
