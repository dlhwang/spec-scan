package io.atworks.specscan.analysis.support.semantic;

import io.atworks.specscan.analysis.domain.semantic.*;
import java.util.Optional;

public interface SemanticConstraintClassifier {
    String id();
    Optional<SemanticConstraintMatch> classify(SemanticContext context, SemanticPredicate predicate);
}
