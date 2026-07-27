package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import io.atworks.specscan.analysis.support.evaluation.BaselineEvaluationFacade;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class ApprovedBaselineEvaluationTest {
    @TempDir Path temporary;

    @Test void approvedBaselineRunsThreeTimesAndReportsExpectedReplacementGaps() throws Exception {
        var workspace = BaselineTestWorkspace.materialize(temporary);
        Path baseline = workspace.root().resolve("baseline-manifest.json");
        BaselineRunRequest request = new BaselineRunRequest(BaselineRunRequest.RunPurpose.G01_DETERMINISM,
            workspace.manifest(), baseline, EvaluationProfile.g01(), "java-baseline-u01", workspace.root(),
            workspace.evaluationRoot(), workspace.reportRoot());

        EvaluationResultBundle result = new BaselineEvaluationFacade().evaluate(request);

        assertThat(result.determinism()).isNotNull();
        assertThat(result.determinism().deterministic()).isTrue();
        assertThat(result.determinism().invocationCount()).isEqualTo(3);
        assertThat(result.overallStatus()).isEqualTo(EvaluationResultBundle.OverallStatus.FAIL);
        assertThat(result.runResult().diff().changes()).anySatisfy(change ->
            assertThat(change.type()).isEqualTo(BaselineDiff.ChangeType.CHANGED));
    }
}
