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
                BusinessRuleCategory.STATE_PRECONDITION, RuleEffect.BUSINESS_RESTRICTION, CONTROL_FLOW),
            descriptor(InputDomainMismatchGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.INVARIANT, RuleEffect.BUSINESS_RESTRICTION, CONTROL_FLOW),
            descriptor(NullRejectionGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.INVARIANT, RuleEffect.REQUEST_REQUIREMENT, CONTROL_FLOW),
            descriptor(EmptyRejectionGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.INVARIANT, RuleEffect.REQUEST_REQUIREMENT, CALL),
            descriptor(DelegatedGuardRule.ID, "java-language", RuleLayer.JAVA_LANGUAGE,
                BusinessRuleCategory.STATE_PRECONDITION, RuleEffect.REQUEST_REQUIREMENT, CALL),
            descriptor(OptionalLookupFailureRule.ID, "jdk-idiom", RuleLayer.JDK_IDIOM,
                BusinessRuleCategory.EXISTENCE, RuleEffect.BUSINESS_RESTRICTION, CALL),
            descriptor(PasswordEncoderMatchFailureRule.ID, "spring", RuleLayer.SPRING,
                BusinessRuleCategory.AUTHENTICATION, RuleEffect.BUSINESS_RESTRICTION, CALL),
            descriptor(SpringDataFindByIdOrElseThrowRule.ID, "spring-data-jpa", RuleLayer.SPRING_DATA_JPA,
                BusinessRuleCategory.EXISTENCE, RuleEffect.BUSINESS_RESTRICTION, CALL));
    }

    private static RuleDescriptor descriptor(String id, String pack, RuleLayer layer,
                                             BusinessRuleCategory category, RuleEffect effect,
                                             Set<EvidenceRole> evidence) {
        return new RuleDescriptor(id, pack, layer, category, effect, true, evidence);
    }
}
