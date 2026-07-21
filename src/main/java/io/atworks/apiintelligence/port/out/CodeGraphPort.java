package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.config.ApiIntelligenceConfiguration.GraphSettings;
import io.atworks.apiintelligence.domain.api.DiscoveredApi;
import io.atworks.apiintelligence.domain.graph.CodeGraph;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;

public interface CodeGraphPort {

    CodeGraph build(DiscoveredApi api, SourceWorkspace workspace, GraphSettings limits);
}
