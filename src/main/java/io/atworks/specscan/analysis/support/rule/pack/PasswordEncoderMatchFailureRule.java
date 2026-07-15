package io.atworks.specscan.analysis.support.rule.pack;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.rule.*;
import java.util.List;

public final class PasswordEncoderMatchFailureRule implements GraphRule {
    public static final String ID = "SPRING_SECURITY_PASSWORD_MATCH_FAILURE";
    public String id() { return ID; }
    public RuleLayer layer() { return RuleLayer.SPRING; }
    public List<BusinessRuleCandidate> match(FactCodeGraph graph, PredicateCandidate predicate) {
        StructuralRuleSupport support = new StructuralRuleSupport(graph);
        FactNode condition = support.node(predicate.conditionNodeId());
        if (condition == null || !(condition.payload() instanceof FactNodePayload.ConditionPayload payload)
                || !rejectsMismatch(condition, payload, support)) return List.of();
        FactNode call = support.callOperand(condition.id()).orElse(null);
        if (call == null || !isPasswordMatch(call.typeResolution())) return List.of();
        List<FactNode> arguments = support.callArguments(call.id());
        FactNode input = arguments.size() > 0 ? arguments.get(0) : null;
        FactNode stored = arguments.size() > 1 ? arguments.get(1) : null;
        boolean targetResolved = input != null && support.readsParameter(input);
        NormalizedConstraint constraint = new NormalizedConstraint(ConstraintKind.RUNTIME_DEPENDENT,
            targetResolved ? input.snippet() : null, null, List.of(), call.id());
        List<EvidenceRef> evidence = support.evidence(predicate, call, EvidenceRole.CALL);
        if (targetResolved) evidence = support.evidence(predicate, call, EvidenceRole.CALL,
            input, EvidenceRole.INPUT_ORIGIN);
        if (stored != null && support.originKey(stored).isPresent())
            evidence = targetResolved
                ? support.evidence(predicate, call, EvidenceRole.CALL, input, EvidenceRole.INPUT_ORIGIN,
                    stored, EvidenceRole.DOMAIN_ORIGIN)
                : support.evidence(predicate, call, EvidenceRole.CALL, stored, EvidenceRole.DOMAIN_ORIGIN);
        List<CandidateDiagnostic> diagnostics = new java.util.ArrayList<>(predicate.diagnostics());
        if (!targetResolved) diagnostics.add(new CandidateDiagnostic(CandidateDiagnosticSeverity.WARNING,
            "PASSWORD_INPUT_ORIGIN_UNRESOLVED", "Password input origin could not be linked to an API parameter",
            call.id()));
        return List.of(support.resolved(predicate, ID, BusinessRuleCategory.AUTHENTICATION,
            targetResolved ? TargetResolutionStatus.RESOLVED : TargetResolutionStatus.UNRESOLVED,
            constraint, 1.0, evidence, diagnostics));
    }
    private boolean isPasswordMatch(TypeResolution resolution) {
        String signature = resolution.resolvedSignature();
        return resolution.status() == TypeResolutionStatus.RESOLVED && signature != null
            && signature.contains("org.springframework.security.crypto.password")
            && signature.endsWith(".matches(java.lang.CharSequence, java.lang.String)");
    }
    private boolean rejectsMismatch(FactNode condition, FactNodePayload.ConditionPayload payload,
                                    StructuralRuleSupport support) {
        if ("MethodCallExpr".equals(payload.astKind())) return support.failureOnElse(condition.id());
        if ("!".equals(payload.rootOperator())) return support.failureOnThen(condition.id());
        FactNode booleanLiteral = support.operands(condition.id()).stream()
            .filter(node -> node.payload() instanceof FactNodePayload.LiteralPayload literal
                && "BooleanLiteralExpr".equals(literal.literalKind())).findFirst().orElse(null);
        if (booleanLiteral == null) return false;
        String value = ((FactNodePayload.LiteralPayload) booleanLiteral.payload()).value();
        return ("==".equals(payload.rootOperator()) && "false".equals(value) && support.failureOnThen(condition.id()))
            || ("==".equals(payload.rootOperator()) && "true".equals(value) && support.failureOnElse(condition.id()))
            || ("!=".equals(payload.rootOperator()) && "false".equals(value) && support.failureOnElse(condition.id()));
    }
}
