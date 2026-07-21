package io.atworks.specscan.analysis.domain.fact;

import java.util.List;

public record FactGraphBuildResult(List<FactCodeGraph> graphs, List<FactGraphDiagnostic> diagnostics) {
    public FactGraphBuildResult {
        graphs = List.copyOf(graphs);
        diagnostics = List.copyOf(diagnostics);
    }
}
