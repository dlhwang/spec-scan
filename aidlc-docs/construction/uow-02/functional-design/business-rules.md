# Business Rules

## Controller Detection Rules

### BR-001 Standard Controller Detection
- Treat classes with `@RestController` as resolved controller candidates.
- Treat classes with `@Controller` plus request mapping semantics as resolved controller candidates.
- Support `@RequestMapping` combinations at class and method level.

### BR-002 Meta-Annotation Preservation
- If controller semantics depend on meta-annotation or custom composed mapping and cannot be fully resolved, preserve the class or method as candidate/unresolved instead of discarding it.

## Endpoint Mapping Rules

### BR-003 Effective Path Resolution
- Final endpoint path is derived from class-level and method-level mapping combination.
- HTTP method must be resolved from mapping annotation semantics when available.

### BR-004 Mapping Ambiguity Handling
- If path or HTTP method cannot be safely resolved, record unresolved mapping candidate or failure record with trace and confidence.

## Request Binding Rules

### BR-005 Explicit Binding Priority
- Resolve these annotations as authoritative:
  - `@PathVariable` -> `PATH`
  - `@RequestParam` -> `QUERY`
  - `@RequestHeader` -> `HEADER`
  - `@RequestBody` -> `BODY`

### BR-006 Convention-Based Heuristics
- Limited Spring convention heuristics may be applied when annotation is omitted.
- Heuristic binding must not be treated as fully certain unless confidence threshold is met.

### BR-007 Unresolved Binding Preservation
- If a parameter location cannot be confidently resolved, preserve an unresolved binding record with evidence and trace.

## Response Binding Rules

### BR-008 Response Binding Classification
- Response outputs are modeled separately from request target locations under `RESPONSE`.
- Distinguish:
  - plain body return
  - `ResponseEntity<T>`
  - generic wrapper response
  - `void`
  - no-content candidate

### BR-009 Success and Error Response Metadata
- Response binding metadata should preserve separate success and error response candidates when detectable or inferable from surrounding semantics.

## Type Resolution Rules

### BR-010 Deep Type Resolution
- Resolve:
  - declared DTO/class types
  - generic wrappers
  - collections
  - nested fields

### BR-011 Unresolved Type Preservation
- If type resolution is incomplete due to unresolved imports, third-party types, symbol issues, or complex generic structures, store an unresolved descriptor instead of dropping the type.

## Source Trace Rules

### BR-012 Trace Coverage
- Preserve trace for:
  - controller class
  - endpoint method
  - parameter binding
  - response binding
  - resolved type or unresolved descriptor origin

### BR-013 Trace Metadata
- Trace should include file/class/method context.
- When available, include line number and resolution confidence.

## Partial Failure Rules

### BR-014 Endpoint-Level Isolation
- A failure on one endpoint must not fail the entire `UOW-02` analysis result.

### BR-015 Failure Category Preservation
- Preserve separate failure categories:
  - `ENDPOINT_DETECTION_FAILED`
  - `REQUEST_MAPPING_RESOLUTION_FAILED`
  - `REQUEST_BINDING_RESOLUTION_FAILED`
  - `RESPONSE_BINDING_RESOLUTION_FAILED`
  - `TYPE_RESOLUTION_FAILED`

### BR-016 Failure Record Semantics
- Each failure record must include:
  - category
  - message
  - source trace
  - recoverability
  - confidence

## Quality Rules

### BR-017 Confidence over Forced Resolution
- If safe resolution is not possible, emit candidate or unresolved output with confidence rather than incorrect definitive output.

### BR-018 Analysis Output Readiness
- Output from `UOW-02` must be detailed enough for downstream validation extraction to connect DTO fields, endpoint context, and source trace without reparsing prior logic assumptions.
