package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;

public record RequestBinding(
    String parameterName,
    BindingLocation targetLocation,
    String type,
    boolean isRequired,
    SourceTrace sourceTrace
) {}
