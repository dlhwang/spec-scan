package io.atworks.specscan.analysis.recipe;

import io.atworks.specscan.analysis.domain.recipe.*;
import io.atworks.specscan.analysis.support.recipe.FrameworkPackActivationService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FrameworkPackActivationTest {
    private final FrameworkCatalogEntry optional = new FrameworkCatalogEntry(
        "SPRING_DATA_OPTIONAL", "com.acme.AccountRepository", "findById", "return", "OPTIONAL_LOOKUP_FAILURE");
    private final FrameworkPackActivationService service = new FrameworkPackActivationService();

    @Test void activatesWhenIdentityTypeAndGraphEvidenceAreProven() {
        FrameworkPackDecision result = service.evaluate("spring-data", optional,
            new FrameworkEvidence("com.acme.AccountRepository", "findById", "java.util.Optional", true));
        assertThat(result.activated()).isTrue();
    }

    @Test void sameMethodNameWithoutOwnerTypeDoesNotActivate() {
        FrameworkPackDecision result = service.evaluate("spring-data", optional,
            new FrameworkEvidence("com.other.Unrelated", "findById", "java.util.Optional", true));
        assertThat(result.activated()).isFalse().isEqualTo(false);
        assertThat(result.status()).isEqualTo("UNRESOLVED");
    }

    @Test void delegatedGuardNeverGuessesWithoutGraphPath() {
        FrameworkPackDecision result = service.evaluate("delegated-guard", optional,
            new FrameworkEvidence("com.acme.AccountRepository", "findById", "java.util.Optional", false));
        assertThat(result.activated()).isFalse();
        assertThat(result.diagnostic()).isEqualTo("UNRESOLVED_DELEGATED_GUARD");
    }

    @Test void typeResolutionFailureIsNotReplacedByNameMatch() {
        FrameworkPackDecision result = service.evaluate("spring-data", optional,
            new FrameworkEvidence("com.acme.AccountRepository", "findById", "", true));
        assertThat(result.diagnostic()).isEqualTo("TYPE_RESOLUTION_REQUIRED");
    }
}
