package io.atworks.apiintelligence.port.out;

import java.nio.file.Path;

public interface RunArtifactPort {

    Path createRun(Path outputRoot, String runId);

    void writeJson(Path runDirectory, String relativeName, Object value);
}
