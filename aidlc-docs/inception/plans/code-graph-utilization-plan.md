# Code Graph Utilization Plan

## Purpose
- Define where code graph techniques can improve the current PoC.
- Keep scope at inception level: usage points, expected gains, and practical limits.
- Avoid replacing the current AST-first pipeline unless the added complexity is justified.

## Current Baseline
- The current pipeline already uses Java AST parsing for:
  - endpoint extraction
  - DTO and annotation analysis
  - validator extraction
  - service hint extraction
- This means the project already has a strong syntax-level model.
- The main gap is not parsing source files themselves, but connecting symbols and calls across files and layers with higher precision.

## What “Code Graph” Means in This Project
- Treat code graph as a semantic graph built from AST and symbol resolution, not a separate storage technology requirement.
- Recommended graph node examples:
  - controller class
  - controller method
  - request DTO
  - DTO field
  - service class
  - service method
  - validator class
  - exception type
  - repository method
- Recommended edge examples:
  - `DECLARES`
  - `CALLS`
  - `USES_TYPE`
  - `BINDS_REQUEST`
  - `VALIDATES`
  - `THROWS`
  - `RETURNS`
  - `MAPS_TO_FIELD`

## Recommended Usage Points

### 1. Endpoint to Business Logic Trace Strengthening
- Best insertion point: after `SpringStaticScanService`, before or inside `ValidationExtractionService`.
- Use the graph to connect:
  - controller method -> injected service field -> service method
  - service method -> downstream helper/domain/repository methods
  - method -> thrown exception -> business rule evidence
- Improvement:
  - today, service hint extraction is likely strongest for direct controller-to-service calls
  - a graph allows controlled multi-hop traversal and more stable trace reconstruction
- Expected gain:
  - better extraction of indirect business rules
  - better evidence paths for “why this rule belongs to this endpoint”

### 2. DTO-to-Validation Source Mapping
- Best insertion point: inside annotation and validator extraction flow.
- Use the graph to connect:
  - request binding -> DTO type -> field
  - custom annotation -> constraint validator
  - validator logic -> referenced field or condition
- Improvement:
  - current extraction can identify annotations and validators
  - a graph can preserve exact linkage between field, annotation, validator class, and resulting candidate
- Expected gain:
  - fewer ambiguous candidates
  - clearer source trace for normalization and final reporting

### 3. Cross-Layer Rule Discovery
- Best insertion point: `ValidationExtractionService` as an optional deeper analysis stage.
- Use the graph to follow:
  - controller -> service -> domain -> exception
  - controller -> service -> repository predicate or existence check
  - controller -> mapper -> DTO/entity conversion path
- Improvement:
  - rules currently hidden behind helper methods or layered delegation become reachable
- Expected gain:
  - increased recall for business constraints such as uniqueness checks, state guards, and permission preconditions

### 4. Candidate Quality Scoring and Filtering
- Best insertion point: before candidate chunk generation.
- Use graph-derived metadata such as:
  - hop distance from endpoint
  - number of supporting edges
  - whether a condition terminates in throw/reject/validation failure
  - whether the same rule is observed from multiple paths
- Improvement:
  - candidate filtering becomes evidence-based instead of purely syntax-snippet-based
- Expected gain:
  - better precision before LLM normalization
  - lower noise in candidate chunks

### 5. Evidence-Rich Output and Debuggability
- Best insertion point: assembly and debug artifact output.
- Use the graph to emit:
  - endpoint-to-rule trace path
  - supporting nodes and source locations
  - confidence or extraction rationale
- Improvement:
  - final output becomes easier to review and challenge
- Expected gain:
  - stronger traceability
  - easier regression testing and operator inspection

## Where Code Graph Is Not the First Priority

### Repository Ingestion
- Code graph adds little value here.
- Source root detection, workspace preparation, and repository fetch should remain simple.

### Basic Endpoint Enumeration
- AST-only extraction is already a good fit.
- Introducing graph traversal too early here would add complexity without clear return.

### Final OpenAPI Assembly
- Assembly should consume already validated facts.
- Graph logic should stay upstream and provide evidence, not dominate output assembly.

## Phased Adoption Recommendation

### Phase 1. Trace Graph for Existing Extraction
- Build only the minimal graph needed for current components.
- Scope:
  - class/method nodes
  - field injection links
  - direct method call edges
  - DTO/field/annotation links
- Goal:
  - improve service hint extraction and DTO-validator mapping without redesigning the whole pipeline

### Phase 2. Multi-Hop Business Rule Traversal
- Add bounded-depth traversal for:
  - service-to-domain calls
  - helper delegation
  - exception propagation hints
- Goal:
  - extract indirect business rules while keeping runtime and false positives under control

### Phase 3. Candidate Scoring and Explainability
- Add graph-backed ranking, confidence, and trace artifacts.
- Goal:
  - improve candidate quality and reduce normalization noise

## Practical Constraints
- Full-program call graph precision in Java is expensive and imperfect.
- Static analysis will still struggle with:
  - reflection
  - dynamic proxies
  - framework magic outside source-visible paths
  - runtime bean selection
  - generated code not present in the repository
- Because of this, the graph should be:
  - bounded
  - evidence-oriented
  - optimized for contract extraction, not for perfect program understanding

## Recommendation Summary
- Use code graph as a precision layer on top of the current AST pipeline.
- Apply it first in `ValidationExtractionService`, not in ingestion or final assembly.
- Prioritize three concrete wins:
  - endpoint-to-service-to-rule trace reconstruction
  - DTO/custom-validator linkage
  - graph-based candidate filtering and evidence scoring
- Do not attempt a full repository-wide “knowledge graph” as the first step.
- The best near-term outcome is higher-quality API condition extraction with stronger traceability and controlled complexity.
