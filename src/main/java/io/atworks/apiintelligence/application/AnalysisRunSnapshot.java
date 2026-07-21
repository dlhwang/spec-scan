package io.atworks.apiintelligence.application;

import io.atworks.apiintelligence.domain.run.AnalysisRunStatus;

public record AnalysisRunSnapshot(String runId, AnalysisRunStatus status, int totalApis,
                                  int completedApis, String error) {

}
