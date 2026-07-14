package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ResponseBinding(
    String type,
    SourceTrace sourceTrace,
    Integer explicitStatus,
    String statusSource
) {
    public ResponseBinding(String type, SourceTrace sourceTrace) {
        this(type, sourceTrace, null, null);
    }
}
