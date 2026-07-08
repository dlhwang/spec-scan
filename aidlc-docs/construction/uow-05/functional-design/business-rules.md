# Business Rules

## Assembly Rules

### BR-001 Direct Condition Authority
- Conditions derived from authoritative direct static mapping must remain authoritative in final assembly.

### BR-002 Normalized Result Usage
- Accepted normalized results may add or supplement conditions.
- Accepted normalized results must not overwrite authoritative direct conditions.

### BR-003 Conflict Handling
- If a normalized result contradicts an authoritative direct condition, preserve the direct condition and create conflict record.

## OpenAPI Mapping Rules

### BR-004 Schema-Compatible Validation Mapping
- Map compatible validation to OpenAPI when possible:
  - required
  - minLength
  - maxLength
  - minimum
  - maximum
  - pattern

### BR-005 Non-Representable Validation Preservation
- Validation that does not fit naturally into OpenAPI schema must remain only in internal `ApiCondition`.

### BR-006 Cross-Artifact Traceability
- OpenAPI and `ApiCondition` outputs must remain trace-linked using endpointId, operationId, and targetPath.

## Provenance Rules

### BR-007 Final ApiCondition Provenance
- Final `ApiCondition` must preserve:
  - evidence
  - confidence
  - source trace
  - llmReason
  - llmGenerated

### BR-008 Extended Provenance
- Also preserve:
  - normalizedBy
  - evidenceSource
  - reviewStatus
  - conflict flag

## Partial Success Rules

### BR-009 Partial Output Allowed
- Failure on some endpoints must not block successful endpoint output.

### BR-010 Failure Record Preservation
- Failed endpoint assembly results must be stored as separate failure records.

### BR-011 Manifest Summary Separation
- Manifest must distinguish:
  - successful endpoints
  - failed endpoints
  - skipped candidates
  - rejected normalizations
  - conflicts

## Persistence Rules

### BR-012 Result Store Abstraction
- Output persistence must remain behind `ResultStorePort`.

### BR-013 Standard Artifact Set
- Standard artifact types include:
  - OpenAPI output
  - ApiCondition output
  - normalization rejection output
  - conflict output
  - manifest output

### BR-014 Artifact Metadata Standard
- Each artifact must preserve metadata such as:
  - path
  - type
  - generatedAt
  - sourceRunId
  - endpointCount

## Review and Debug Rules

### BR-015 Debug Artifact Preservation
- Preserve:
  - rejected normalization output
  - invalid candidate output
  - failure summary
  - chunk JSON
  - retry history summary
  - conflict records
  - output manifest

### BR-016 Evidence-Centered Debug Scope
- Debug artifacts should preserve candidate evidence and source trace.
- They should not expand into storing the entire source codebase as output artifact.

## Quality Rules

### BR-017 No Silent Conflict Resolution
- Conflicts between authoritative direct conditions and normalized results must never be silently merged or overwritten.

### BR-018 Reviewability Requirement
- Final output package must allow a reviewer to understand:
  - what was generated
  - what failed
  - what was rejected
  - what conflicted
  - where each rule came from
