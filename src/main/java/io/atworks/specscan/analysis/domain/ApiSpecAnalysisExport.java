package io.atworks.specscan.analysis.domain;

import java.util.List;

public record ApiSpecAnalysisExport(
    List<ApiVersionRecord> apiVersions,
    List<ParameterRecord> parameters,
    List<ApiValueValidationRecord> valueValidations
) {}
