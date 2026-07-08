package io.atworks.specscan.analysis.domain;

import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.List;

public record ApiEndpoint(
    String httpMethod,
    String path,
    String controllerClass,
    String controllerMethod,
    List<RequestBinding> requestBindings,
    ResponseBinding responseBinding,
    SourceTrace sourceTrace
) {}
