package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.domain.evidence.Evidence;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import java.util.List;

public interface EvidencePort {

    List<Evidence> collect(CodeGraph graph, SourceWorkspace workspace);
}
