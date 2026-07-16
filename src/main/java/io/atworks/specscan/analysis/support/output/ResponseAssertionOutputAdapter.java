package io.atworks.specscan.analysis.support.output;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import java.util.*;

final class ResponseAssertionOutputAdapter {
    void add(BusinessRuleCandidate candidate, NormalizedConstraint constraint,
             List<ExecutableCondition> assertions, List<CandidateOutputDiagnostic> diagnostics) {
        if (constraint == null || !OutputConstraintSupport.isExecutable(constraint)) {
            diagnostics.add(diagnostic("RESPONSE_ASSERTION_UNSUPPORTED",
                "Response guarantee requires an executable constraint", candidate));
            return;
        }
        String rawPath = constraint.targetPath();
        String location = "$status".equals(rawPath) || "status".equals(rawPath) ? "STATUS" : "BODY";
        String path = blank(rawPath) ? "$body" : rawPath.startsWith("$") ? rawPath : "$." + rawPath;
        assertions.add(new ExecutableCondition(location, path, constraint.operator(),
            constraint.expectedValues(), constraint.expectedSource(), candidate.ruleId(), candidate.confidence(),
            candidate.evidence()));
    }

    private CandidateOutputDiagnostic diagnostic(String code, String message, BusinessRuleCandidate candidate) {
        return new CandidateOutputDiagnostic(code, message, candidate.candidateId(), candidate.ruleId());
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
