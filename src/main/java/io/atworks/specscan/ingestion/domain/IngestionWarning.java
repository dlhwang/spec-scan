package io.atworks.specscan.ingestion.domain;

import java.util.Map;

public record IngestionWarning(
    String warningCode,
    String message,
    String relatedPath,
    String severity,
    Map<String, String> details
) {
    public IngestionWarning(String warningCode, String message, String relatedPath, String severity) {
        this(warningCode, message, relatedPath, severity, Map.of());
    }
}
