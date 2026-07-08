# Cross-Cutting NFR Requirements

## Purpose
This document captures non-functional requirements that apply across the full PoC pipeline, not only to a single unit of work. It complements unit-level NFR artifacts and is intended to guide the next NFR Design stage.

## Scope
Applies across:
- `UOW-01` Repository Ingestion
- `UOW-02` Endpoint and Type Analysis
- `UOW-03` Validation Candidate Extraction
- `UOW-04` Candidate Chunk and Normalization
- `UOW-05` Contract Assembly and Output

## Cross-Cutting NFR Themes

### CC-NFR-01 Static-Analysis-Only Safety
- The PoC must not execute target repository build, test, run, or script commands.
- All stages must preserve the static-analysis-only contract established during ingestion.

### CC-NFR-02 Failure Isolation
- Failure in one repository, endpoint, extractor, chunk, or output artifact must not automatically corrupt unrelated successful results.
- Partial success must be preserved wherever meaningful.

### CC-NFR-03 Evidence and Traceability
- Every major derived output must remain traceable to source evidence, source trace, and confidence.
- This applies to endpoint extraction, validation evidence extraction, normalization outcomes, and final contract artifacts.

### CC-NFR-04 Determinism Where Possible
- Deterministic stages such as ingestion metadata generation, endpoint analysis, direct condition mapping, chunk generation, and final output serialization must produce stable outputs for the same inputs.
- Non-deterministic LLM behavior must be fenced with retry, validation, and artifact preservation.

### CC-NFR-05 Reproducibility
- The full pipeline should be reproducible through stable identifiers and artifacts, including:
  - `sourceRunId`
  - candidate/chunk identity
  - prompt version
  - input hash
  - manifest and artifact metadata

### CC-NFR-06 Partial PBT Applicability
- Property-based testing is selectively applied to pure or near-pure transformations such as:
  - annotation-to-condition mapping
  - operator conversion
  - JSON serialization/deserialization
  - OpenAPI transformation
  - artifact manifest consistency

### CC-NFR-07 Parser and Extraction Accuracy
- `UOW-02` and `UOW-03` prioritize extraction accuracy, unresolved-case preservation, and false-positive control over aggressive forced resolution.

### CC-NFR-08 LLM Boundary Integrity
- `UOW-04` is the strict pre/post-LLM boundary.
- Only evidence-backed candidates may cross into normalization.
- LLM output must never be treated as authoritative without validation.

### CC-NFR-09 Output Reviewability
- Final outputs must support both human review and machine consumption.
- Success, failure, skipped, rejected, and conflict states must be explicit.

### CC-NFR-10 Data Minimization
- Debug and reproducibility artifacts should preserve minimal required evidence and trace.
- The system must avoid storing full source dumps or unnecessary duplication.

## Unit Emphasis

### UOW-02
- parser performance
- partial endpoint failure handling
- source trace accuracy

### UOW-03
- extraction accuracy
- evidence preservation
- false positive control

### UOW-04
- LLM response stability
- retry policy
- schema and semantic validation
- cost and caching
- failure isolation

### UOW-05
- artifact determinism
- manifest reliability
- final output traceability

## Relationship to Unit-Level NFR
- Use this document for shared system-wide design constraints.
- Use unit-level NFR docs for implementation-specific emphasis where a single unit has deeper requirements.
