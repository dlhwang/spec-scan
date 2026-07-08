package io.atworks.specscan.ingestion.domain;

import java.util.List;

public record SafetyPolicyHint(
    List<String> allowedActions,
    List<String> forbiddenActions,
    String policyVersion
) {}
