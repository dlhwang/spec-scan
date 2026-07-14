package io.atworks.specscan.analysis.domain.evaluation;

public record EvaluationFailure(String labelId, FailureClassification classification, String details) {}
