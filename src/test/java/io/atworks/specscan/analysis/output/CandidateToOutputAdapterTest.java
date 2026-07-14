package io.atworks.specscan.analysis.output;

import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.output.*;
import io.atworks.specscan.analysis.support.candidate.BusinessRuleCandidateFactory;
import io.atworks.specscan.analysis.support.output.CandidateToOutputAdapter;
import io.atworks.specscan.ingestion.domain.SourceTrace;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateToOutputAdapterTest {
    private final CandidateToOutputAdapter adapter = new CandidateToOutputAdapter();

    @Test void classifiesRequestLiteralRuntimeAndUnresolvedCandidatesWithoutGuessing() {
        BusinessRuleCandidate request = candidate("request", BusinessRuleCategory.RANGE,
            SemanticStatus.RESOLVED, TargetResolutionStatus.RESOLVED,
            new NormalizedConstraint(ConstraintKind.INPUT_LITERAL, "request.quantity", "GREATER_THAN",
                List.of("0"), "literal-node"), List.of());
        BusinessRuleCandidate runtime = candidate("runtime", BusinessRuleCategory.AUTHENTICATION,
            SemanticStatus.RESOLVED, TargetResolutionStatus.UNRESOLVED,
            new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT, null, null, List.of(), "matches-call"),
            List.of(diagnostic("TARGET_UNRESOLVED")));
        BusinessRuleCandidate unresolved = candidate("unknown", BusinessRuleCategory.UNKNOWN,
            SemanticStatus.UNRESOLVED, TargetResolutionStatus.UNRESOLVED, null,
            List.of(diagnostic("NO_RULE_MATCHED")));

        EndpointRuleOutput output = adapter.adapt(endpoint(), List.of(runtime, unresolved, request));

        assertThat(output.requestPreconditions()).singleElement().satisfies(condition -> {
            assertThat(condition.targetLocation()).isEqualTo("BODY");
            assertThat(condition.targetPath()).isEqualTo("$.quantity");
            assertThat(condition.expectedValues()).containsExactly("0");
        });
        assertThat(output.responseAssertions()).isEmpty();
        assertThat(output.excludedBusinessRules()).singleElement().satisfies(rule -> {
            assertThat(rule.reasonCode()).isEqualTo("RUNTIME_DEPENDENT");
            assertThat(rule.targetPath()).isNull();
        });
        assertThat(output.diagnostics()).extracting(CandidateOutputDiagnostic::code)
            .containsExactly("SEMANTIC_UNRESOLVED");
    }

    @Test void inputToDomainIsNotPromotedToARequestOnlyCondition() {
        BusinessRuleCandidate candidate = candidate("inventory", BusinessRuleCategory.CAPACITY,
            SemanticStatus.RESOLVED, TargetResolutionStatus.RESOLVED,
            new NormalizedConstraint(ConstraintKind.INPUT_TO_DOMAIN, "request.quantity", "LESS_THAN_OR_EQUAL",
                List.of(), "stock-origin"), List.of());
        EndpointRuleOutput output = adapter.adapt(endpoint(), List.of(candidate));
        assertThat(output.requestPreconditions()).isEmpty();
        assertThat(output.excludedBusinessRules()).singleElement()
            .extracting(ExcludedBusinessRule::reasonCode).isEqualTo("EXTERNAL_STATE_REQUIRED");
    }

    private BusinessRuleCandidate candidate(String id, BusinessRuleCategory category, SemanticStatus semantic,
                                            TargetResolutionStatus target, NormalizedConstraint constraint,
                                            List<CandidateDiagnostic> diagnostics) {
        return new BusinessRuleCandidateFactory().create("predicate:" + id, "RULE:" + id, category,
            ExtractionStatus.EXTRACTED, semantic, target, constraint, 0.9, List.of(evidence(id)), diagnostics);
    }
    private CandidateDiagnostic diagnostic(String code) {
        return new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING, code, code, "node");
    }
    private EvidenceRef evidence(String id) {
        return new EvidenceRef("node:" + id, "src/Test.java", 1, 1, 1, 10,
            EvidenceRole.PREDICATE, "evidence");
    }
    private ApiEndpoint endpoint() {
        SourceTrace trace = new SourceTrace("src/Api.java", 1, 2);
        RequestBinding body = new RequestBinding("request", BindingLocation.BODY, "sample.Request", true,
            null, null, null, List.of(), trace);
        return new ApiEndpoint("POST", "/items", "sample.Api", "create", List.of(body),
            new ResponseBinding("sample.Response", trace), trace);
    }
}
