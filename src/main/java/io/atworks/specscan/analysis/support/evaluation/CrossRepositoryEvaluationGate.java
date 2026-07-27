package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.util.*;

/** Final evaluation-only decision gate; Java remains authoritative and is always compared. */
public final class CrossRepositoryEvaluationGate {
    public CrossRepositoryEvaluationReport evaluate(int repositories, int endpoints, int yamlResolved,
            int javaResolved, int exactMatches, int fallbackCount, int holdoutMatches) {
        List<String> diagnostics = new ArrayList<>();
        if (repositories == 0 || endpoints == 0) diagnostics.add("CORPUS_SCOPE_EMPTY");
        if (yamlResolved < javaResolved) diagnostics.add("YAML_COVERAGE_GAP");
        if (exactMatches < javaResolved) diagnostics.add("JAVA_PARITY_DIFF");
        if (fallbackCount > 0) diagnostics.add("FALLBACK_USED");
        if (holdoutMatches == 0) diagnostics.add("HOLDOUT_NOT_GENERALIZED");
        YamlEvaluationVerdict verdict = diagnostics.isEmpty() ? YamlEvaluationVerdict.GO
            : (diagnostics.contains("JAVA_PARITY_DIFF") || diagnostics.contains("CORPUS_SCOPE_EMPTY")
                ? YamlEvaluationVerdict.NO_GO : YamlEvaluationVerdict.PARTIAL);
        return new CrossRepositoryEvaluationReport(verdict, repositories, endpoints, yamlResolved,
            javaResolved, exactMatches, fallbackCount, holdoutMatches, diagnostics);
    }
}
