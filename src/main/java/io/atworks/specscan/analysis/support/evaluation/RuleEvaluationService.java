package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.evaluation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class RuleEvaluationService {
    public EvaluationReport evaluate(List<EvaluationCase> cases, int legacyNewDisagreementCount) {
        List<EvaluationCase> ordered = cases.stream().sorted(Comparator.comparing(c -> c.label().id())).toList();
        int observed = 0, candidateTrue = 0, falsePositives = 0, resolved = 0, semanticCorrect = 0;
        int supportedLabels = 0, recalledLabels = 0, traced = 0, targetRequired = 0, targetResolved = 0;
        int partial = 0, unresolved = 0, unsupported = 0, unsupportedResolved = 0;
        Map<String, Set<String>> ruleDatasets = new HashMap<>();
        List<EvaluationFailure> failures = new ArrayList<>();

        for (EvaluationCase evaluation : ordered) {
            GoldenRuleLabel label = evaluation.label(); List<BusinessRuleCandidate> candidates = evaluation.observed();
            observed += candidates.size();
            if (label.expectedCandidate()) candidateTrue += candidates.size(); else falsePositives += candidates.size();
            boolean semanticMatch = false;
            for (BusinessRuleCandidate candidate : candidates) {
                if (candidate.semanticStatus() == SemanticStatus.RESOLVED) {
                    resolved++;
                    if (matches(label, candidate)) { semanticCorrect++; semanticMatch = true; }
                    ruleDatasets.computeIfAbsent(candidate.ruleId(), ignored -> new HashSet<>()).add(label.dataset());
                } else if (candidate.semanticStatus() == SemanticStatus.UNRESOLVED) unresolved++;
                if (candidate.extractionStatus() == ExtractionStatus.PARTIAL) partial++;
                if (candidate.extractionStatus() == ExtractionStatus.UNSUPPORTED) {
                    unsupported++;
                    if (candidate.semanticStatus() == SemanticStatus.RESOLVED) unsupportedResolved++;
                }
                if (hasTrace(candidate)) traced++;
                if (label.targetRequired()) {
                    targetRequired++;
                    if (candidate.targetStatus() == TargetResolutionStatus.RESOLVED) targetResolved++;
                }
            }
            if (label.expectedCandidate() && label.supportedScope()) {
                supportedLabels++;
                if (semanticMatch) recalledLabels++;
            }
            classifyFailures(label, candidates, semanticMatch, failures);
        }
        int reused = (int) ruleDatasets.values().stream().filter(datasets -> datasets.size() >= 2).count();
        EvaluationMetrics metrics = new EvaluationMetrics(
            ratio(candidateTrue, candidateTrue + falsePositives), ratio(semanticCorrect, resolved),
            ratio(recalledLabels, supportedLabels), falsePositives, ratio(traced, observed),
            ratio(targetResolved, targetRequired), ratio(partial, observed), ratio(unresolved, observed),
            ratio(unsupported, observed), reused, legacyNewDisagreementCount, unsupportedResolved, observed);
        return new EvaluationReport(metrics, failures, fingerprint(ordered, legacyNewDisagreementCount));
    }

    private boolean matches(GoldenRuleLabel label, BusinessRuleCandidate candidate) {
        boolean ruleAccepted = label.acceptedRuleIds().isEmpty() || label.acceptedRuleIds().contains(candidate.ruleId());
        boolean categoryAccepted = label.acceptedCategories().isEmpty()
            || label.acceptedCategories().contains(candidate.category());
        return label.expectedCandidate() && ruleAccepted && categoryAccepted;
    }
    private boolean hasTrace(BusinessRuleCandidate candidate) {
        Set<EvidenceRole> roles = new HashSet<>(); candidate.evidence().forEach(ref -> roles.add(ref.role()));
        return roles.contains(EvidenceRole.PREDICATE) && roles.contains(EvidenceRole.FAILURE_OUTCOME);
    }
    private void classifyFailures(GoldenRuleLabel label, List<BusinessRuleCandidate> candidates,
                                  boolean semanticMatch, List<EvaluationFailure> failures) {
        if (!label.expectedCandidate() && !candidates.isEmpty()) failures.add(new EvaluationFailure(label.id(),
            FailureClassification.CANDIDATE_DETECTION_DEFECT, "negative label produced candidates"));
        if (label.expectedCandidate() && label.supportedScope() && candidates.isEmpty()) failures.add(new EvaluationFailure(
            label.id(), FailureClassification.CANDIDATE_DETECTION_DEFECT, "supported label was not detected"));
        else if (label.expectedCandidate() && label.supportedScope() && !semanticMatch) failures.add(new EvaluationFailure(
            label.id(), FailureClassification.GRAPH_RULE_DEFECT, "detected candidate did not match accepted semantics"));
        if (label.targetRequired() && candidates.stream().noneMatch(candidate ->
                candidate.targetStatus() == TargetResolutionStatus.RESOLVED)) failures.add(new EvaluationFailure(
            label.id(), FailureClassification.TARGET_ORIGIN_DEFECT, "required target was not resolved"));
        if (label.expectedCandidate() && !label.supportedScope() && candidates.isEmpty()) failures.add(new EvaluationFailure(
            label.id(), FailureClassification.INTENTIONALLY_UNSUPPORTED, "label is outside the supported scope"));
    }
    private double ratio(int numerator, int denominator) { return denominator == 0 ? 1.0 : (double) numerator / denominator; }
    private String fingerprint(List<EvaluationCase> cases, int disagreements) {
        StringBuilder canonical = new StringBuilder("legacy=").append(disagreements);
        for (EvaluationCase evaluation : cases) {
            canonical.append('|').append(evaluation.label().id());
            evaluation.observed().stream().sorted(Comparator.comparing(BusinessRuleCandidate::candidateId))
                .forEach(candidate -> canonical.append(':').append(candidate.candidateId()).append('/')
                    .append(candidate.ruleId()).append('/').append(candidate.semanticStatus()).append('/')
                    .append(candidate.targetStatus()));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
