# NFR Requirements

## Scope
These non-functional requirements apply to `UOW-05`, the final contract assembly and output stage that produces OpenAPI, ApiCondition, manifest, and review/debug artifacts from already prepared analysis and normalization inputs.

## Performance

### NFR-01 Assembly Performance
- For a sample repository with dozens of endpoints, final assembly should complete within a few seconds.
- This includes:
  - final `ApiCondition` assembly
  - OpenAPI generation
  - ApiCondition JSON generation
  - manifest generation
- LLM invocation time is explicitly out of scope for this unit.

### NFR-02 Deterministic Processing
- `UOW-05` should behave deterministically for the same validated inputs.
- Re-running assembly on identical direct conditions and normalization outputs should produce stable outputs.

## Reliability and Availability

### NFR-03 Partial Success Availability
- Failure in some endpoints or conditions must not block successful endpoint outputs.
- Successful OpenAPI and ApiCondition artifacts must still be produced when possible.

### NFR-04 Failure Visibility
- Failed endpoints, rejected normalizations, skipped candidates, and conflicts must remain visible in output artifacts and manifest summaries.

### NFR-05 Machine-Readable Manifest
- Manifest output must be stable and machine-readable.
- It must separate:
  - successful endpoints
  - failed endpoints
  - skipped candidates
  - rejected normalizations
  - conflicts

## Reproducibility and Traceability

### NFR-06 End-to-End Reproducibility
- Final outputs must be traceable to:
  - `sourceRunId`
  - manifest
  - accepted normalization results
  - rejected normalization results
  - conflict records
  - retry summaries
  - chunk references

### NFR-07 Provenance Preservation
- Final `ApiCondition` output must preserve evidence, confidence, provenance, and review metadata needed to explain how each rule was assembled.

## Security and Data Minimization

### NFR-08 Minimal Debug Retention
- Debug artifacts must be limited to evidence-centered data and source trace.
- Preserve only what is necessary for review and reproducibility.

### NFR-09 No Source Dump Output
- Final outputs and debug artifacts must not include full repository source dumps.
- Unnecessary duplicate snippets and unrelated file content are prohibited.

### NFR-10 Sensitive Raw Artifact Restraint
- Raw snippet or response retention is allowed only where necessary for debugging, rejection review, or conflict analysis.

## Maintainability

### NFR-11 Stable Artifact Shape
- Artifact structures must use explicit models and stable schemas.
- Changes to manifest or output shapes should remain reviewable and testable.

### NFR-12 Stable Ordering
- Serialized outputs must use stable field ordering to support snapshot tests and artifact diffs.

### NFR-13 Output Store Abstraction
- Persistence must remain abstracted behind `ResultStorePort` to support future DB or API-based storage without rewriting assembly logic.

## Observability and Quality Verification

### NFR-14 Generation Summary Metrics
- Generation summary or manifest must include:
  - artifact count
  - failed endpoint count
  - rejected normalization count
  - conflict count
  - skipped candidate count
  - generation duration

### NFR-15 Artifact Validation Visibility
- Artifact-by-artifact validation result must be recorded so reviewers and downstream tools can identify invalid or partial outputs.

### NFR-16 Reviewability
- The output package must support demo review, regression diagnosis, and downstream automation without requiring manual reconstruction of hidden intermediate state.
