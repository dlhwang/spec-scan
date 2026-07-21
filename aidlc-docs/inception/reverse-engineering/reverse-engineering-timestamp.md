# Reverse Engineering Metadata

**Analysis Date**: 2026-07-21T16:16:00+09:00
**Analyzer**: Unified AI-DLC Engine
**Workspace Path**: `D:\workspace\auto-oas`
**Total Source Files Analyzed**: ~110 Java Source Files in `src/main/java`

## Artifacts Generated

- [x] business-overview.md
- [x] architecture.md
- [x] code-structure.md
- [x] api-documentation.md
- [x] component-inventory.md
- [x] technology-stack.md
- [x] dependencies.md
- [x] code-quality-assessment.md
- [x] reverse-engineering-timestamp.md

## Key Architectural Summary
- **Fact CodeGraph Construction Pipeline**: JavaParser AST -> `FactMethodVisitor` (Control Flow / Scope Isolation) -> `FactExpressionVisitor` (Nodes & Edge Binding) -> `DtoSchemaGraphBuilder` (DTO Schema Expansion) -> `ExceptionHandlerFactScanner` (Exception-HTTP Status Mapping).
- **Graph-to-Output Transformation Pipeline**: `FactGraphIndex` & `SemanticContext` -> Semantic Classifiers (`BinaryConstraint`, `OptionalLookup`, `OptimisticLock`, `PasswordEncoder`, `Authorization`, `StandardGuard`) -> `DefaultGraphRuleEngine` -> `RuleOutputService` -> `OpenApiAssemblyService` (`openapi.yaml`, `api-spec-analysis.json`, `api-execution-model.json`, `validation-evidence-graph.json`).
