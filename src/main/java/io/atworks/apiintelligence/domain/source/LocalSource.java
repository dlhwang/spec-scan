package io.atworks.apiintelligence.domain.source;

import java.nio.file.Path;
import java.util.Objects;

public record LocalSource(Path path) implements AnalysisSource {

    public LocalSource {
        path = Objects.requireNonNull(path).toAbsolutePath().normalize();
    }
}
