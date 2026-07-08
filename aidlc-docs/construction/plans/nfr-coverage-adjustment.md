# NFR Coverage Adjustment

## Purpose
Functional Design 이후 NFR 범위를 `UOW-05`에만 한정하지 않고, 실제 비기능 리스크가 큰 unit 중심으로 재배치한다.

## User-Guided NFR Priority

### UOW-02 Endpoint and Type Analysis
- Primary emphasis:
  - Functional Design
- NFR when needed:
  - parser performance
  - partial endpoint failure isolation
  - source trace accuracy

### UOW-03 Validation Candidate Extraction
- Primary emphasis:
  - Functional Design
- NFR when needed:
  - extraction accuracy
  - evidence preservation
  - false positive control

### UOW-04 Candidate Chunk and Normalization
- Primary emphasis:
  - Functional Design
  - NFR Requirements
  - NFR Design
- High-priority NFR themes:
  - LLM response stability
  - retry behavior
  - schema validation
  - cost and caching
  - failure isolation

### UOW-05 Contract Assembly and Output
- Primary emphasis:
  - Functional Design
  - NFR Requirements
- High-priority NFR themes:
  - deterministic artifact generation
  - manifest reliability
  - partial success reporting
  - artifact traceability and reproducibility

## Execution Adjustment
1. Treat `UOW-04` as the main NFR-heavy unit before moving deeper into NFR Design.
2. Keep `UOW-02` and `UOW-03` NFR coverage lightweight and targeted rather than over-expanding them.
3. Preserve `UOW-05` NFR artifacts because final output determinism and traceability still matter.
4. Avoid pretending every unit needs the same NFR depth.
