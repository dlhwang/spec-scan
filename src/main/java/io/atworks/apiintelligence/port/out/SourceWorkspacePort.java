package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.domain.source.AnalysisSource;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;

public interface SourceWorkspacePort {

    SourceWorkspace acquire(AnalysisSource source);
}
