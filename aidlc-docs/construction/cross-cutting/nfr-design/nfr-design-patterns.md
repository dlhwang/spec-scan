# NFR Design Patterns

## Overview
These patterns apply across the full PoC pipeline from repository ingestion through final output generation. They convert the cross-cutting NFR requirements into concrete non-functional design guidance.

## 1. Stage-Scoped Failure Containment
- Purpose:
  - prevent one stage failure from collapsing unrelated successful work
- Pattern:
  - each stage emits success, failure, or partial-success records
  - downstream stages consume valid prior outputs without requiring perfect global success
- Applied stages:
  - `UOW-01`: repository/workspace failure isolated per run
  - `UOW-02`: endpoint-level partial failure
  - `UOW-03`: extractor-level partial failure
  - `UOW-04`: candidate/chunk/retry/rejection separation
  - `UOW-05`: endpoint assembly failure separated from successful output

## 2. Retry Plus Validation Gating
- Purpose:
  - contain non-determinism and malformed responses at the LLM boundary
- Pattern:
  - validation is split into three gates:
    - candidate gate before LLM invocation
    - LLM response gate after invocation
    - artifact gate before final persistence
  - retry is only allowed after explicit validation failure classification
  - bounded retry count
  - accepted output must pass structural and semantic validators
  - repeated invalid or fabricated responses become rejected artifacts
- Primary application:
  - `UOW-04`
- Secondary application:
  - `UOW-05` artifact validation before final persistence

## 3. Multi-Level Provenance Pattern
- Purpose:
  - make outputs reproducible and reviewable across the whole pipeline
- Pattern:
  - run-level provenance:
    - `sourceRunId`
  - chunk-level provenance:
    - `chunkId`, `candidateId`, `inputHash`, `promptVersion`
  - artifact-level provenance:
    - artifact type, generatedAt, upstream references, sourceRunId
- Primary application:
  - `UOW-04`, `UOW-05`

## 4. Deterministic Serialization and Stable Output
- Purpose:
  - support snapshot tests, artifact diffing, and review consistency
- Pattern:
  - explicit output models
  - stable field ordering
  - deterministic JSON serialization for all non-LLM-generated artifacts
- Applied stages:
  - `UOW-01`, `UOW-02`, `UOW-03`, `UOW-05`

## 5. Cache-Keyed Normalization Reuse
- Purpose:
  - reduce redundant LLM cost and improve reproducibility
- Pattern:
  - derive cache key from `inputHash` + `promptVersion`
  - cacheable results:
    - accepted normalization results that passed full validation gate
    - explicitly marked reusable rejection classes if the rejection is deterministic and schema-stable
  - non-cacheable results:
    - retryable failures
    - partial or truncated responses
    - responses tied to obsolete promptVersion or candidate schema
  - cache bypass conditions must be explicit and observable
  - invalidate on prompt contract or candidate schema change
  - expose hit/miss metrics
- Primary application:
  - `UOW-04`

## 6. Minimal Evidence Retention
- Purpose:
  - preserve enough debug value without storing unnecessary code or data
- Pattern:
  - store minimal evidence snippet + source trace
  - forbid repository-wide source dump artifacts
  - forbid unnecessary duplicated file body storage
  - allow raw request/response only where required for retry/rejection/conflict review
- Applied stages:
  - `UOW-03`, `UOW-04`, `UOW-05`

## 7. Reviewable Manifest Pattern
- Purpose:
  - support both machine consumption and human review
- Pattern:
  - manifest separates success, failure, skipped, rejected, conflict states
  - artifact-by-artifact validation status included
  - summary counters included for downstream automation
- Primary application:
  - `UOW-05`

## 8. Accuracy over Forced Resolution
- Purpose:
  - avoid false certainty in static or inferred analysis
- Pattern:
  - unresolved or candidate state preferred over wrong hard resolution
  - confidence must accompany inferred outputs
  - failure record only when candidate preservation is impossible
- Applied stages:
  - `UOW-02`, `UOW-03`
