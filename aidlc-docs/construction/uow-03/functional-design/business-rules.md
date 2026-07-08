# Business Rules

## Direct Mapping Rules

### BR-001 Supported Direct Mapping Set
- Direct mapping should support:
  - `@NotNull`
  - `@NotBlank`
  - `@Size`
  - `@Pattern`
  - `@Min`
  - `@Max`
  - `@Positive`
  - `@PositiveOrZero`
  - `@Negative`
  - `@NegativeOrZero`
  - `@Email`
  - `@NotEmpty`

### BR-002 Direct Mapping Output
- Supported explicit annotation semantics produce `ApiConditionDraft`.
- Direct-mapped results should carry strong evidence and high confidence.

## Unsupported and Custom Annotation Rules

### BR-003 Unsupported Annotation Preservation
- Unsupported annotations must not be dropped.
- Preserve them as `ValidationCandidate` with raw metadata.

### BR-004 Custom Annotation Metadata
- Preserve:
  - annotation name
  - annotation attributes
  - applied target
  - target field or parameter path
  - source trace
  - unresolved reason when needed

### BR-005 Constraint Validator Linkage
- If a custom annotation uses `@Constraint(validatedBy = ...)`, preserve linked validator metadata.
- If linkage cannot be fully resolved, keep best-effort linkage and unresolved reason.

## Validator-Layer Rules

### BR-006 Validator Extraction Coverage
- Extract from:
  - `ConstraintValidator#isValid`
  - Spring `Validator#validate`
  - `@InitBinder`
  - `errors.rejectValue`
  - `errors.reject`

### BR-007 Validator Binding Trace
- Validator-derived candidates must preserve how the validator is connected to the endpoint or request model.
- Binding trace may come from explicit binder registration, annotation linkage, or type support matching.

### BR-008 Validator Confidence
- Use higher confidence for explicit validator attachment.
- Use medium or low confidence for inferred validator linkage.

## Service and Domain Hint Rules

### BR-009 Hint Extraction Coverage
- Extract candidates from:
  - `if` condition snippets
  - `throw new BusinessException(...)`
  - `ErrorCode`
  - repository lookup guards
  - method chain context

### BR-010 Hypothesis Candidate Preservation
- If a hint cannot be directly tied to a request field, preserve it as hypothesis candidate instead of discarding it.
- Hypothesis candidates must use lower confidence unless stronger evidence is available.

## Evidence and Confidence Rules

### BR-011 Mandatory Evidence Fields
- Every direct condition and candidate must preserve:
  - evidence
  - source trace
  - source kind

### BR-012 Extended Evidence Fields
- Also preserve:
  - extraction confidence
  - reasoning note
  - unresolved reason when applicable

### BR-013 Confidence Semantics
- `HIGH` for explicit annotation semantics or explicit validator linkage
- `MEDIUM` for partially inferred but well-supported evidence
- `LOW` for hypothesis-driven service/domain hints or weak correlations

## Partial Failure Rules

### BR-014 Extractor Isolation
- Annotation extraction, validator extraction, and service hint extraction must fail independently.
- One extractor failure must not invalidate successful output from the others.

### BR-015 Output Separation
- Results must be split into:
  - direct condition drafts
  - validation candidates
  - extraction failures

### BR-016 Ambiguity Handling
- If a case is ambiguous but still evidence-bearing, keep it as candidate.
- If extraction cannot even produce a minimally traceable candidate, emit failure record.

## Readiness Rules

### BR-017 Pre-LLM Readiness
- Candidate output must be rich enough for chunk generation and later normalization without reparsing original source from scratch.

### BR-018 No Silent Loss
- Unsupported annotations, weak validator links, and indirect service hints must not be silently discarded if traceable evidence exists.
