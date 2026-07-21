package io.atworks.apiintelligence.adapter.javaparser;

import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.evidence.EvidenceIdGenerator;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.graph.CodeNode;
import io.atworks.apiintelligence.domain.source.SourceLocation;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import io.atworks.apiintelligence.port.out.EvidencePort;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SourceEvidenceAdapter implements EvidencePort {

    public List<Evidence> collect(CodeGraph graph, SourceWorkspace workspace) {
        List<Evidence> out = new ArrayList<>();
        for (CodeNode n : graph.nodes()) {
            SourceLocation l = n.sourceLocation();
            if (l == null || "unknown".equals(l.relativePath())) {
                continue;
            }
            try {
                String snippet = SourceSnippetReader.read(
                    workspace.root().resolve(l.relativePath()), l);
                out.add(
                    new Evidence(EvidenceIdGenerator.generate(graph.apiId(), l, n.kind().name()),
                        graph.apiId(), n.kind().name(), l, snippet, List.of(n.id())));
            } catch (IOException | RuntimeException e) {
                throw new IllegalStateException("EVIDENCE_BUILD_FAILED", e);
            }
        }
        return out.stream().sorted(Comparator.comparing(Evidence::evidenceId)).toList();
    }
}
