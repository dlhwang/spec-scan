# Unit of Work Story Map

## Story to Unit Mapping

| Story | Title | Assigned Unit | Why |
| --- | --- | --- | --- |
| `S-01` | Repository Input and Source Ingestion | `UOW-01` | source acquisition and workspace preparation is a standalone entry unit |
| `S-02` | Spring Endpoint and Type Extraction | `UOW-02` | endpoint/type context is a foundational analysis layer shared by all later units |
| `S-03` | Annotation Based Validation Extraction | `UOW-03` | direct annotation mapping and unsupported annotation fallback both belong to validation evidence generation |
| `S-04` | Validator Layer Candidate Extraction | `UOW-03` | validator evidence shares the same pre-LLM candidate generation boundary |
| `S-05` | Service/Domain Validation Hint Extraction | `UOW-03` | service/domain hints are also pre-LLM validation evidence and share trace/evidence handling |
| `S-06` | Candidate Chunk Generation and Invalid Handling | `UOW-04` | this is the explicit boundary before LLM normalization |
| `S-07` | LLM Normalization and Result Validation | `UOW-04` | prompt build, adapter call, and validated normalization belong to the same guarded normalization unit |
| `S-08` | ApiCondition/OpenAPI Assembly and Demo Verification | `UOW-05` | final contract materialization and artifact output is a separate review-facing unit |

## Epic to Unit View

| Epic | Related Stories | Units |
| --- | --- | --- |
| `E-01` | `S-01` | `UOW-01` |
| `E-02` | `S-02`, `S-03` | `UOW-02`, `UOW-03` |
| `E-03` | `S-04`, `S-05`, `S-06` | `UOW-03`, `UOW-04` |
| `E-04` | `S-07`, `S-08` | `UOW-04`, `UOW-05` |

## Story Coverage Validation
- Every story is assigned to at least one unit.
- No story crosses both pre-LLM and post-LLM ownership without an explicit handoff model.
- `S-06` is the deliberate handoff point between extraction-oriented units and normalization-oriented units.

## Development Sequence by Story
1. `S-01` -> `UOW-01`
2. `S-02` -> `UOW-02`
3. `S-03`, `S-04`, `S-05` -> `UOW-03`
4. `S-06`, `S-07` -> `UOW-04`
5. `S-08` -> `UOW-05`

## Review Notes
- This mapping preserves the user-requested split between endpoint/type extraction and validation extraction.
- This mapping also preserves the split between candidate generation concerns and final contract assembly concerns.
- Output is intentionally grouped with contract assembly, not with normalization.
