package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.rule.*;
import java.util.*;

public final class InitialRulePacks {
    private InitialRulePacks() {}

    public static List<RulePack> all() {
        return List.of(javaLanguage(), jdkIdioms(), spring(), springDataJpa());
    }
    public static RulePack javaLanguage() {
        return new RulePack("java-language", true, List.of(
            new EnumAllowedValueGuardRule(), new InputDomainMismatchGuardRule(), new NullRejectionGuardRule(),
            new EmptyRejectionGuardRule(),
            new DelegatedGuardRule(), new AuthorizationGuardCallRule()),
            RulePrecedence.none());
    }
    public static RulePack jdkIdioms() {
        return new RulePack("jdk-idiom", true, List.of(new OptionalLookupFailureRule()), RulePrecedence.none());
    }
    public static RulePack spring() {
        return new RulePack("spring", true, List.of(new PasswordEncoderMatchFailureRule()), RulePrecedence.none());
    }
    public static RulePack springDataJpa() {
        return new RulePack("spring-data-jpa", true, List.of(new SpringDataFindByIdOrElseThrowRule()),
            new RulePrecedence(Map.of(SpringDataFindByIdOrElseThrowRule.ID, 10)));
    }
}
