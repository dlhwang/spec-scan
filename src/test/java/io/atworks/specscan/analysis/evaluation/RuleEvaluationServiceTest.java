package io.atworks.specscan.analysis.evaluation;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.candidate.BusinessRuleCandidateFactory;
import io.atworks.specscan.analysis.support.evaluation.*;
import io.atworks.specscan.analysis.support.rule.pack.OptionalLookupFailureRule;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluationServiceTest {
    private final RuleEvaluationService evaluator = new RuleEvaluationService();

    @Test void computesReproducibleMetricsAndPassesInitialQualityGate() throws IOException {
        EvaluationCase alpha = positive("optional-lookup-alpha", "synthetic-alpha", candidate("alpha"));
        EvaluationCase holdout = positive("optional-lookup-renamed-holdout", "holdout-renamed-beta", candidate("beta"));
        EvaluationCase negative = new EvaluationCase(new GoldenRuleLabel("unrelated-string-matches", "negative",
            false, true, Set.of(), Set.of(), false), List.of());
        EvaluationCase boundary = new EvaluationCase(new GoldenRuleLabel("unresolved-receiver-boundary", "boundary",
            true, false, Set.of(), Set.of(), false), List.of());

        List<EvaluationCase> cases = List.of(alpha, holdout, negative, boundary);
        EvaluationReport report = evaluator.evaluate(cases);
        EvaluationReport reordered = evaluator.evaluate(List.of(boundary, negative, holdout, alpha));

        assertThat(report.metrics()).satisfies(metrics -> {
            assertThat(metrics.candidatePrecision()).isEqualTo(1.0);
            assertThat(metrics.semanticPrecision()).isEqualTo(1.0);
            assertThat(metrics.supportedScopeRecall()).isEqualTo(1.0);
            assertThat(metrics.evidenceTraceRate()).isEqualTo(1.0);
            assertThat(metrics.falsePositiveCount()).isZero();
            assertThat(metrics.crossDatasetReusedRuleCount()).isEqualTo(1);
        });
        assertThat(report.failures()).singleElement()
            .extracting(EvaluationFailure::classification).isEqualTo(FailureClassification.INTENTIONALLY_UNSUPPORTED);
        assertThat(reordered.deterministicFingerprint()).isEqualTo(report.deterministicFingerprint());
        assertThat(new RuleQualityGate().evaluate(report, QualityGateConfig.initial(), 0, 0).passed()).isTrue();
        String reportJson = new EvaluationReportWriter().toJson(report);
        assertThat(reportJson)
            .contains("candidatePrecision", "deterministicFingerprint", "INTENTIONALLY_UNSUPPORTED");
        writeReportWhenRequested(reportJson);
    }

    @Test void qualityGateReportsHardcodingAndRegressionInsteadOfHidingThem() {
        EvaluationReport report = evaluator.evaluate(List.of(
            positive("alpha", "dataset-a", candidate("one")),
            positive("beta", "dataset-b", candidate("two"))));
        QualityGateResult result = new RuleQualityGate().evaluate(report, QualityGateConfig.initial(), 1, 1);
        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).extracting(QualityGateViolation::code)
            .contains("PRODUCTION_HARDCODED_VALUES", "UNINTENDED_REGRESSIONS");
    }

    @Test void manifestKeepsPositiveNegativeBoundaryAndHoldoutDatasetsSeparate() throws IOException {
        String manifest = resource("rule-based-static-analysis/evaluation/golden-labels.json");
        assertThat(manifest).contains("synthetic-alpha", "holdout-renamed-beta", "negative", "boundary")
            .contains(OptionalLookupFailureRule.ID);
        assertThat(resource("rule-based-static-analysis/evaluation/positive/alpha/CatalogLookupService.java"))
            .contains("orElseThrow");
        assertThat(resource("rule-based-static-analysis/evaluation/holdout/beta/RegistryLookupService.java"))
            .contains("orElseThrow").doesNotContain("CatalogLookupService", "candidate");
    }

    private EvaluationCase positive(String id, String dataset, BusinessRuleCandidate candidate) {
        return new EvaluationCase(new GoldenRuleLabel(id, dataset, true, true,
            Set.of(OptionalLookupFailureRule.ID), Set.of(BusinessRuleCategory.EXISTENCE), false), List.of(candidate));
    }
    private BusinessRuleCandidate candidate(String suffix) {
        List<EvidenceRef> evidence = List.of(
            new EvidenceRef("predicate-" + suffix, "src/" + suffix + ".java", 1, 1, 1, 10,
                EvidenceRole.PREDICATE, "optional"),
            new EvidenceRef("failure-" + suffix, "src/" + suffix + ".java", 1, 1, 1, 10,
                EvidenceRole.FAILURE_OUTCOME, "orElseThrow"));
        return new BusinessRuleCandidateFactory().create("predicate:" + suffix, OptionalLookupFailureRule.ID,
            BusinessRuleCategory.EXISTENCE, ExtractionStatus.EXTRACTED, SemanticStatus.RESOLVED,
            TargetResolutionStatus.NOT_APPLICABLE,
            new NormalizedConstraint(ConstraintKind.CONTROL_FLOW_ONLY, null, null, List.of(), "call-" + suffix),
            1.0, evidence, List.of());
    }
    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull(); return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private void writeReportWhenRequested(String json) throws IOException {
        String configured = System.getProperty("specscan.evaluation.report.path");
        if (configured == null || configured.isBlank()) return;
        Path path = Path.of(configured); Files.createDirectories(path.getParent()); Files.writeString(path, json);
    }
}
