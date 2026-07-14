package io.atworks.specscan.analysis.domain.fact;

public record FactGraphDiagnostic(String apiMethod, DiagnosticSeverity severity, String reason, boolean truncated, FactGraphTraversalBudget configuredBudget, FactGraphTraversalStats observed, SourceRange sourceRange, String details) {}
