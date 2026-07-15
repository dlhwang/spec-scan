package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.rule.RuleLayer;
import java.util.*;

public final class InitialRuleCatalog {
    private static final Set<EvidenceRole> CONTROL_FLOW = Set.of(
        EvidenceRole.PREDICATE, EvidenceRole.FAILURE_OUTCOME);
    private static final Set<EvidenceRole> CALL = Set.of(
        EvidenceRole.PREDICATE, EvidenceRole.FAILURE_OUTCOME, EvidenceRole.CALL);

    private InitialRuleCatalog() {}

    public static List<RuleDescriptor> descriptors() {
        return List.of(
            descriptor(EnumAllowedValueGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.STATE_PRECONDITION, CONTROL_FLOW),
            descriptor(InputDomainMismatchGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.INVARIANT, CONTROL_FLOW),
            descriptor(NullRejectionGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.INVARIANT, CONTROL_FLOW),
            descriptor(EmptyRejectionGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.INVARIANT, CALL),
            descriptor(DelegatedGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.STATE_PRECONDITION, CALL),
            descriptor(AuthorizationGuardCallRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.AUTHORIZATION, CALL),
            descriptor(OptionalLookupFailureRule.ID, "jdk-idiom", RuleLayer.JDK_IDIOM,
                BusinessRuleCategory.EXISTENCE, CALL),
            descriptor(PasswordEncoderMatchFailureRule.ID, "spring", RuleLayer.SPRING,
                BusinessRuleCategory.AUTHENTICATION, CALL),
            descriptor(SpringDataFindByIdOrElseThrowRule.ID, "spring-data-jpa", RuleLayer.SPRING_DATA_JPA,
                BusinessRuleCategory.EXISTENCE, CALL));
    }

    private static RuleDescriptor descriptor(String id, String pack, RuleLayer layer,
                                             BusinessRuleCategory category, Set<EvidenceRole> evidence) {
        return new RuleDescriptor(id, pack, layer, category, true, evidence);
    }
}
