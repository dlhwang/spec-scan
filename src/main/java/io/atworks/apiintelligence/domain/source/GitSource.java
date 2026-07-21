package io.atworks.apiintelligence.domain.source;

import java.net.URI;
import java.util.Objects;

public record GitSource(URI url, RevisionType revisionType, String revision) implements
    AnalysisSource {

    public GitSource {
        url = Objects.requireNonNull(url);
        String s = url.getScheme();
        if (!("http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s))) {
            throw new IllegalArgumentException("git URL must use HTTP or HTTPS");
        }
        revision = revision == null ? null : revision.trim();
        if ((revisionType == null) != (revision == null || revision.isBlank())) {
            throw new IllegalArgumentException("revision type and value must be provided together");
        }
        if (revision != null && revision.isBlank()) {
            revision = null;
        }
    }
}
