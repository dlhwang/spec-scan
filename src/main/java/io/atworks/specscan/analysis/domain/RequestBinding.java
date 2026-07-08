package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;

public record RequestBinding(
    String parameterName,
    BindingLocation targetLocation,
    String type,
    boolean isRequired,
    String description,
    String example,
    String defaultValue,
    List<String> enumValues,
    SourceTrace sourceTrace
) {}
