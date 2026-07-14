package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.util.*;

public final class RuleQualityGate {
    public QualityGateResult evaluate(EvaluationReport report, QualityGateConfig config,
                                      int productionHardcodedValues, int unintendedRegressions) {
        EvaluationMetrics metrics = report.metrics(); List<QualityGateViolation> violations = new ArrayList<>();
        minimum(violations, "CANDIDATE_PRECISION", metrics.candidatePrecision(), config.minimumCandidatePrecision());
        minimum(violations, "SEMANTIC_PRECISION", metrics.semanticPrecision(), config.minimumSemanticPrecision());
        minimum(violations, "SUPPORTED_SCOPE_RECALL", metrics.supportedScopeRecall(), config.minimumSupportedScopeRecall());
        minimum(violations, "EVIDENCE_TRACE_RATE", metrics.evidenceTraceRate(), config.minimumEvidenceTraceRate());
        maximum(violations, "FALSE_POSITIVE_COUNT", metrics.falsePositiveCount(), config.maximumFalsePositives());
        minimum(violations, "CROSS_DATASET_RULE_REUSE", metrics.crossDatasetReusedRuleCount(),
            config.minimumCrossDatasetReusedRules());
        maximum(violations, "UNSUPPORTED_RESOLVED", metrics.unsupportedResolvedCount(),
            config.maximumUnsupportedResolved());
        maximum(violations, "PRODUCTION_HARDCODED_VALUES", productionHardcodedValues,
            config.maximumProductionHardcodedValues());
        maximum(violations, "UNINTENDED_REGRESSIONS", unintendedRegressions,
            config.maximumUnintendedRegressions());
        return new QualityGateResult(violations.isEmpty(), violations);
    }
    private void minimum(List<QualityGateViolation> result, String code, double actual, double expected) {
        if (actual < expected) result.add(new QualityGateViolation(code, actual + " < " + expected));
    }
    private void maximum(List<QualityGateViolation> result, String code, int actual, int expected) {
        if (actual > expected) result.add(new QualityGateViolation(code, actual + " > " + expected));
    }
}
