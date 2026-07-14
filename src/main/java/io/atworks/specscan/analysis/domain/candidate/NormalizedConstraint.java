package io.atworks.specscan.analysis.domain.candidate;

import java.util.List;
import java.util.Objects;

public record NormalizedConstraint(ConstraintKind kind, String targetPath, String operator,
                                   List<String> expectedValues, String expectedSource) {
    public NormalizedConstraint {
        Objects.requireNonNull(kind, "kind");
        expectedValues = List.copyOf(Objects.requireNonNull(expectedValues, "expectedValues"));
    }
}
