package io.atworks.apiintelligence.adapter.fact;

import io.atworks.apiintelligence.adapter.javaparser.SourceSnippetReader;
import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.evidence.EvidenceIdGenerator;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.specscan.analysis.domain.fact.FactCodeGraph;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.SourceRange;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FactGraphEvidenceAdapter {

    public List<Evidence> collect(String apiId, FactCodeGraph graph, Path workspaceRoot) {
        List<Evidence> evidence = new ArrayList<>();
        for (FactNode node : graph.nodes()) {
            SourceLocation location = toLocation(node.sourceRange());
            try {
                String snippet = SourceSnippetReader.read(
                    workspaceRoot.resolve(location.relativePath()), location);
                String kind = node.type().name();
                evidence.add(new Evidence(
                    EvidenceIdGenerator.generate(apiId, location, kind), apiId, kind, location,
                    snippet, List.of(node.id())));
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("EVIDENCE_BUILD_FAILED: " + node.id(), e);
            }
        }
        return evidence.stream().sorted(Comparator.comparing(Evidence::evidenceId)).toList();
    }

    private SourceLocation toLocation(SourceRange range) {
        return new SourceLocation(range.relativePath(), range.startLine(), range.startColumn(),
            range.endLine(), range.endColumn());
    }
}
