package io.atworks.specscan.analysis.support.candidate;

import io.atworks.specscan.analysis.domain.candidate.EvidenceRef;
import io.atworks.specscan.analysis.domain.candidate.EvidenceRole;
import io.atworks.specscan.analysis.domain.fact.FactNode;
import io.atworks.specscan.analysis.domain.fact.SourceRange;

public final class EvidenceMapper {
    public EvidenceRef fromFact(FactNode node, EvidenceRole role) {
        SourceRange range = node.sourceRange();
        return new EvidenceRef(node.id(), range.relativePath(), range.startLine(), range.startColumn(),
            range.endLine(), range.endColumn(), role, node.snippet());
    }
}
