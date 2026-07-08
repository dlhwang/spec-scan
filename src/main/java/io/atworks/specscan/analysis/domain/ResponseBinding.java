package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record ResponseBinding(
    String type,
    SourceTrace sourceTrace
) {}
