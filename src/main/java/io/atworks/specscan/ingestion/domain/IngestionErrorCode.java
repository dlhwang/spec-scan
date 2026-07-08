package io.atworks.specscan.ingestion.domain;

public enum IngestionErrorCode {
    INVALID_REPOSITORY_URL,
    UNSUPPORTED_REPOSITORY_SOURCE,
    WORKSPACE_CREATE_FAILED,
    CLONE_FAILED,
    STATIC_ANALYSIS_POLICY_VIOLATION
}
