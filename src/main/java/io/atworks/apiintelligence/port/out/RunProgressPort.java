package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.application.AnalysisRunSnapshot;
import io.atworks.apiintelligence.domain.run.AnalysisRunStatus;

public interface RunProgressPort {

    AnalysisRunSnapshot create(String runId);

    AnalysisRunSnapshot transition(String runId, AnalysisRunStatus status);

    AnalysisRunSnapshot get(String runId);
}
