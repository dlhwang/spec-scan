# Business Rules

## Input Contract Rules

### BR-001 Allowed Repository Source
- Only GitHub repository HTTPS URLs are accepted.
- Allowed patterns:
  - `https://github.com/{owner}/{repo}`
  - `https://github.com/{owner}/{repo}.git`
- Optional branch, tag, or commit must be supplied as explicit structured fields, not encoded through unsupported URL variants.

### BR-002 Rejected Repository Source
- Reject:
  - local filesystem paths
  - non-GitHub hosts
  - archive download URLs
  - release asset URLs
  - raw file URLs
  - SSH repository URLs
  - arbitrary non-URL strings
- Rejection result must be a hard failure with machine-readable error code.

## Workspace Rules

### BR-003 Isolated Workspace by Default
- Every ingestion execution creates a new temporary workspace.
- A previous execution workspace must not be reused implicitly.

### BR-004 Cache as Extension Point
- Cache key strategy may be defined for future optimization.
- Cache reuse is not part of default PoC behavior.

## Source Inventory Rules

### BR-005 Preserve Multiple Source Roots
- Ingestion must preserve a full source root inventory.
- It must not collapse the repository into a single chosen root during ingestion.

### BR-006 Source Root Metadata
- Each source root candidate should include:
  - root path
  - module name
  - build tool hint
  - `src/main/java` presence
  - Java file count
  - Spring annotation candidate count
  - selection priority

### BR-007 Excluded Path Traceability
- Excluded directories or modules must be recorded with exclusion reason.
- Downstream stages must be able to explain why some modules or paths were not analyzed.

## Output Rules

### BR-008 Minimum Ingestion Output
- Ingestion output must contain:
  - original repository URL
  - normalized repository identity
  - workspace path
  - detected source roots
  - build tool hint
  - Java file inventory summary
  - excluded paths
  - warnings
  - ingestion metadata

### BR-009 Safety Hint Propagation
- Ingestion output must carry a static-analysis-only safety hint for downstream stages.

## Error and Warning Rules

### BR-010 Hard Failure Classification
- The following are hard failures:
  - `INVALID_REPOSITORY_URL`
  - `UNSUPPORTED_REPOSITORY_SOURCE`
  - `WORKSPACE_CREATE_FAILED`
  - `CLONE_FAILED`
  - `STATIC_ANALYSIS_POLICY_VIOLATION`
- Hard failure stops ingestion result progression.

### BR-011 Soft Warning Classification
- The following may be warnings:
  - `SOURCE_ROOT_NOT_FOUND`
  - `JAVA_FILE_SCAN_FAILED`
  - partial path scan issues
  - non-standard but readable project layout
- Warning result may still produce a usable `RepositorySource`.

### BR-012 Machine-Readable Error Codes
- Every hard failure and warning class must map to a stable machine-readable code.
- Human-readable messages are required, but tests and workflow decisions should depend on codes.

## Static-Analysis-Only Safety Rules

### BR-013 Allowed Actions
- Allowed actions:
  - repository clone
  - directory traversal
  - source/config/build file reads
  - inventory generation
  - metadata extraction

### BR-014 Forbidden Actions
- Forbidden actions:
  - `gradle build`
  - `mvn test`
  - `npm install`
  - application run
  - test execution
  - repository script execution
  - arbitrary binary execution
  - any action causing side effects inside the target repository

### BR-015 Policy Violation Handling
- If a requested ingestion flow would require forbidden actions, ingestion must fail with `STATIC_ANALYSIS_POLICY_VIOLATION`.
