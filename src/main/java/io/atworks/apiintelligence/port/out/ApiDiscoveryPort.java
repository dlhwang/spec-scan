package io.atworks.apiintelligence.port.out;

import io.atworks.apiintelligence.domain.api.DiscoveredApi;
import io.atworks.apiintelligence.domain.source.SourceWorkspace;
import java.util.List;

public interface ApiDiscoveryPort {

    List<DiscoveredApi> discover(SourceWorkspace workspace);
}
