package io.atworks.specscan.analysis.domain;

public record ApiVersionRecord(
    long id,
    long apiId,
    int version,
    String method,
    String endpoint,
    String contentType,
    String jsonRequestBody,
    String xmlRequestBody,
    String requestExample,
    String responseExample,
    String preScript,
    String postScript,
    boolean isDeleted
) {}
