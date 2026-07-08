# Business Rules

## Chunk Grouping Rules

### BR-001 Chunk Scope
- Chunk grouping must support:
  - endpoint scope
  - validator method scope

### BR-002 Source Kind Metadata
- Each chunk must preserve source sub-grouping metadata such as:
  - `BEAN_VALIDATION_UNSUPPORTED`
  - `CONSTRAINT_VALIDATOR`
  - `SPRING_VALIDATOR`
  - `SERVICE_DOMAIN_HINT`

## Pre-LLM Filtering Rules

### BR-003 Mandatory Candidate Integrity
- Before LLM submission, each candidate must have:
  - evidence
  - source trace
  - `candidateId`

### BR-004 Extended Candidate Validation
- Also validate:
  - endpoint linkage
  - enum/domain validity
  - duplicate `candidateId`

### BR-005 Invalid Candidate Exclusion
- Candidates failing pre-LLM integrity checks must not be sent to the LLM.
- Invalid candidates must be preserved as invalid records for review.

## Prompt Contract Rules

### BR-006 Fixed JSON Input Envelope
- Prompt input must use a fixed JSON envelope, not free-form prompt-only structure.

### BR-007 Allowed Enums
- Prompt contract must explicitly provide allowed:
  - operator enum
  - confidence enum
  - target location enum

### BR-008 Forbidden Behaviors
- Prompt contract must explicitly forbid:
  - evidence-free rule generation
  - new candidateId generation
  - enum-outside-value usage
  - non-JSON output
  - reasoning from source code not present in supplied evidence

## Response Validation Rules

### BR-009 Structural Validation
- Validate:
  - JSON parse success
  - schema conformity
  - enum conformity
  - candidateId conformity

### BR-010 Evidence and Rule Creation Guard
- Response must not create rules without evidence.
- Response must not introduce candidates that were not present in the input envelope.

### BR-011 Semantic Consistency Validation
- Validate:
  - targetPath consistency with evidence or static facts
  - consistency between candidate type and produced rule
  - no contradiction with known chunk metadata

## Retry and Rejection Rules

### BR-012 Retry-Eligible Failures
- Retry may be attempted for:
  - parse failure
  - schema failure
  - enum failure
  - candidateId mismatch
  - semantic inconsistency
  - low-quality response

### BR-013 Immediate or Bounded Rejection
- Reject when:
  - evidence-free rule creation occurs repeatedly
  - rule is unrelated to input candidate
  - bounded retry limit is exceeded

### BR-014 Retry Metadata
- Each retry cycle must preserve:
  - prompt version
  - input hash
  - attempt count

## Result Preservation Rules

### BR-015 Accepted and Rejected Persistence
- Accepted and rejected normalization results must both be preserved.

### BR-016 Diagnostic Preservation
- Preserve:
  - raw request
  - raw response
  - retry history
  - validator findings

## Quality Rules

### BR-017 LLM Is Normalizer, Not Inventor
- The LLM normalizes supplied evidence; it does not invent unsupported business rules.

### BR-018 Reproducibility Requirement
- Normalization outcome must remain reproducible enough to explain which prompt contract and validation findings produced acceptance, retry, or rejection.
