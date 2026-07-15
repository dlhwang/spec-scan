package io.atworks.specscan.analysis.domain.evaluation;

public record EvaluationMetrics(double candidatePrecision, double semanticPrecision,
                                double supportedScopeRecall, int falsePositiveCount,
                                double evidenceTraceRate, double targetResolutionRate,
                                double typeResolutionFallbackRate, double unresolvedRate,
                                double unsupportedRate, int crossDatasetReusedRuleCount,
                                int unsupportedResolvedCount,
                                int observedCandidateCount) {}
