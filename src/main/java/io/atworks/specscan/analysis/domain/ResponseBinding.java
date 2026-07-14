package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ResponseBinding(
    String type,
    SourceTrace sourceTrace,
    Integer explicitStatus,
    String statusSource,
    java.util.Map<String, String> explicitHeaders
) {
    public ResponseBinding(String type, SourceTrace sourceTrace) {
        this(type, sourceTrace, null, null, java.util.Map.of());
    }
    public ResponseBinding(String type, SourceTrace sourceTrace, Integer explicitStatus, String statusSource) {
        this(type, sourceTrace, explicitStatus, statusSource, java.util.Map.of());
    }
    public ResponseBinding {
        explicitHeaders = java.util.Map.copyOf(explicitHeaders == null ? java.util.Map.of() : explicitHeaders);
    }
}
