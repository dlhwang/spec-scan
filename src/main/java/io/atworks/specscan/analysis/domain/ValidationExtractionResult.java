package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.IngestionWarning;
import java.util.List;

public record ValidationExtractionResult(
    List<ApiConditionDraft> directConditions,
    List<ValidationCandidate> candidates,
    List<IngestionWarning> warnings
) {}
