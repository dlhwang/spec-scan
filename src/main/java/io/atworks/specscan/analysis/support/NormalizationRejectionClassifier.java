package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.ValidationCandidate;

public final class NormalizationRejectionClassifier {
    public String classify(ValidationCandidate candidate) {
        if (candidate.sourceTrace() == null || candidate.sourceTrace().fileRelativePath() == null) {
            return "NORMALIZATION_EVIDENCE_MISSING";
        }
        if (candidate.operationKey() == null || candidate.operationKey().isBlank()) {
            return "NORMALIZATION_ENDPOINT_BINDING_FAILED";
        }
        if (candidate.targetPath() == null || candidate.targetPath().isBlank()) {
            return "NORMALIZATION_TARGET_UNRESOLVED";
        }
        if (candidate.evidenceSnippet() == null || candidate.evidenceSnippet().isBlank()) {
            return "NORMALIZATION_CONSTRAINT_UNRESOLVED";
        }
        return "NORMALIZATION_RULE_UNSUPPORTED";
    }
}
