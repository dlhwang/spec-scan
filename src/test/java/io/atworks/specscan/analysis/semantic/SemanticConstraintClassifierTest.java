package io.atworks.specscan.analysis.semantic;

import io.atworks.specscan.analysis.domain.candidate.*;
import io.atworks.specscan.analysis.domain.fact.*;
import io.atworks.specscan.analysis.domain.semantic.*;
import io.atworks.specscan.analysis.support.semantic.*;
import io.atworks.specscan.analysis.support.rule.pack.OptionalLookupFailureRule;
import io.atworks.specscan.analysis.support.rule.pack.SpringDataFindByIdOrElseThrowRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SemanticConstraintClassifierTest {
    @Test void directNullAndRequireNonNullProduceEquivalentConstraints() {
        Fixture direct = binary("==", nullNode(), "value", true);
        Fixture guard = guard("java.util.Objects.requireNonNull(java.lang.Object)", "value");

        SemanticConstraintMatch directMatch = classify(new BinaryConstraintClassifier(), direct);
        SemanticConstraintMatch guardMatch = classify(new StandardGuardMethodClassifier(
            StandardGuardMethodRegistry.defaults()), guard);

        assertThat(directMatch.constraint()).extracting(NormalizedConstraint::kind,
            NormalizedConstraint::targetPath, NormalizedConstraint::operator,
            NormalizedConstraint::expectedValues)
            .containsExactly(ConstraintKind.INPUT_LITERAL, "$", "NOT_NULL", List.of());
        assertThat(guardMatch.constraint()).extracting(NormalizedConstraint::kind,
            NormalizedConstraint::targetPath, NormalizedConstraint::operator,
            NormalizedConstraint::expectedValues)
            .containsExactly(ConstraintKind.INPUT_LITERAL, "$", "NOT_NULL", List.of());
    }

    @Test void failedLessThanOrEqualBecomesExclusiveMinimumRequirement() {
        FactNode zero = new FactNode("constant", FactNodeType.LITERAL, range(), "0",
            TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload("0", "IntegerLiteralExpr"));
        Fixture fixture = binary("<=", zero, "deposit", true);

        assertThat(classify(new BinaryConstraintClassifier(), fixture).constraint())
            .extracting(NormalizedConstraint::targetPath, NormalizedConstraint::operator,
                NormalizedConstraint::expectedValues)
            .containsExactly("$", "GT", List.of("0"));
    }

    @Test void reversesComparisonWhenLiteralIsOnTheLeft() {
        FactNode zero = new FactNode("constant", FactNodeType.LITERAL, range(), "0",
            TypeResolution.notApplicable(), new FactNodePayload.LiteralPayload("0", "IntegerLiteralExpr"));
        Fixture fixture = binaryWithOperands(">=", zero, input("deposit"), true);

        assertThat(classify(new BinaryConstraintClassifier(), fixture).constraint())
            .extracting(NormalizedConstraint::operator, NormalizedConstraint::expectedValues)
            .containsExactly("GT", List.of("0"));
    }

    @Test void registryRejectsSameMethodNameFromAnotherOwner() {
        Fixture custom = guard("sample.Objects.requireNonNull(java.lang.Object)", "value");
        SemanticContext context = new SemanticContext(custom.graph());

        assertThat(new StandardGuardMethodClassifier(StandardGuardMethodRegistry.defaults())
            .classify(context, context.normalize(custom.predicate()))).isEmpty();
    }

    @Test void emptyRegistryRequiresTheTrueBranchToFail() {
        SemanticConstraintClassifier classifier = new StandardGuardMethodClassifier(
            StandardGuardMethodRegistry.defaults());
        Fixture rejected = booleanGuard("org.springframework.util.ObjectUtils.isEmpty(java.lang.Object)", true);
        Fixture accepted = booleanGuard("org.springframework.util.ObjectUtils.isEmpty(java.lang.Object)", false);

        assertThat(classify(classifier, rejected).constraint())
            .extracting(NormalizedConstraint::targetPath, NormalizedConstraint::operator)
            .containsExactly("$", "NOT_EMPTY");
        SemanticContext acceptedContext = new SemanticContext(accepted.graph());
        assertThat(classifier.classify(acceptedContext, acceptedContext.normalize(accepted.predicate()))).isEmpty();
    }

    @Test void springDataLookupProducesOneSpecificShadowClassification() {
        Fixture fixture = optionalLookup("java.util.Optional.orElseThrow()", "findById",
            "org.springframework.data.repository.CrudRepository.findById(java.lang.Object)");
        SemanticContext context = new SemanticContext(fixture.graph());
        SemanticPredicate semantic = context.normalize(fixture.predicate());

        assertThat(new OptionalLookupFailureRule().match(fixture.graph(), fixture.predicate())).hasSize(1);
        assertThat(new SpringDataFindByIdOrElseThrowRule().match(fixture.graph(), fixture.predicate())).hasSize(1);
        assertThat(new OptionalLookupClassifier().classifyLookup(context, semantic))
            .hasValueSatisfying(classification -> {
                assertThat(classification.kind()).isEqualTo(OptionalLookupKind.SPRING_DATA_FIND_BY_ID);
                assertThat(classification.lookupCallNodeId()).isEqualTo("lookup");
                assertThat(classification.semanticMatch().constraint().targetPath()).isEqualTo("$");
                assertThat(classification.semanticMatch().constraint().operator()).isEqualTo("EXISTS");
            });
    }

    @Test void genericJdkOptionalRemainsTheFallbackKind() {
        Fixture fixture = optionalLookup("java.util.Optional.orElseThrow()", "findByEmail",
            "sample.UserStore.findByEmail(java.lang.String)");
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new OptionalLookupClassifier().classifyLookup(context, context.normalize(fixture.predicate())))
            .hasValueSatisfying(classification ->
                assertThat(classification.kind()).isEqualTo(OptionalLookupKind.JDK_OPTIONAL));
    }

    @Test void customRepositoryOptionalLookupKeepsRepositorySemantics() {
        Fixture fixture = optionalLookup("java.util.Optional.orElseThrow()", "findByUserId",
            "sample.ParticipantRepository.findByUserId(java.lang.Long)");
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new OptionalLookupClassifier().classifyLookup(context, context.normalize(fixture.predicate())))
            .hasValueSatisfying(classification -> {
                assertThat(classification.kind())
                    .isEqualTo(OptionalLookupKind.SPRING_DATA_REPOSITORY_LOOKUP);
                assertThat(classification.semanticMatch().constraint().targetPath()).isEqualTo("$");
            });
    }

    @Test void localSpringDataRepositoryDeclarationKeepsSpecificLookupKind() {
        Fixture fixture = optionalLookup("java.util.Optional.orElseThrow()", "findById",
            "sample.OrderRepository.findById(java.lang.String)");
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new OptionalLookupClassifier().classifyLookup(context, context.normalize(fixture.predicate())))
            .hasValueSatisfying(classification ->
                assertThat(classification.kind()).isEqualTo(OptionalLookupKind.SPRING_DATA_FIND_BY_ID));
    }

    @Test void sameNamedCustomOptionalTerminalIsRejected() {
        Fixture fixture = optionalLookup("sample.Optional.orElseThrow()", "findById",
            "sample.Repository.findById(java.lang.Object)");
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new OptionalLookupClassifier().classifyLookup(context, context.normalize(fixture.predicate())))
            .isEmpty();
    }

    @Test void defaultAuthorizationRegistryDoesNotGuessFromCanMethodName() {
        Fixture fixture = authorizationGuard("canEdit", "sample.Policy.canEdit(sample.Document)",
            "MethodCallExpr");
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new AuthorizationGuardClassifier(AuthorizationGuardRegistry.defaults())
            .classify(context, context.normalize(fixture.predicate()))).isEmpty();
    }

    @Test void explicitAuthorizationDescriptorPreservesNamedPermission() {
        Fixture fixture = authorizationGuard("canCancel", "sample.Policy.canCancel(sample.Order)", "!");
        AuthorizationGuardRegistry registry = new AuthorizationGuardRegistry(List.of(
            new AuthorizationGuardDescriptor("sample.Policy.canCancel", false,
                "currentUser", "HAS_ORDER_CANCELLATION_PERMISSION")));
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new AuthorizationGuardClassifier(registry)
            .classify(context, context.normalize(fixture.predicate())))
            .hasValueSatisfying(match -> assertThat(match.constraint())
                .extracting(NormalizedConstraint::targetPath, NormalizedConstraint::operator)
                .containsExactly("currentUser", "HAS_ORDER_CANCELLATION_PERMISSION"));
    }

    @Test void springPasswordMismatchLinksInputAndStoredPassword() {
        Fixture fixture = passwordGuard(
            "org.springframework.security.crypto.password.PasswordEncoder.matches(java.lang.CharSequence, java.lang.String)",
            "!");

        assertThat(classify(new PasswordEncoderSemanticClassifier(), fixture))
            .satisfies(match -> {
                assertThat(match.constraint()).extracting(NormalizedConstraint::kind,
                    NormalizedConstraint::targetPath, NormalizedConstraint::operator,
                    NormalizedConstraint::expectedSource)
                    .containsExactly(ConstraintKind.RUNTIME_DEPENDENT, "$", "PASSWORD_MATCH", "stored");
                assertThat(match.resolutionQuality()).isEqualTo(ResolutionQuality.PARTIAL);
            });
    }

    @Test void sameNamedCustomPasswordMatcherIsRejected() {
        Fixture fixture = passwordGuard(
            "sample.PasswordEncoder.matches(java.lang.CharSequence, java.lang.String)", "!");
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(new PasswordEncoderSemanticClassifier()
            .classify(context, context.normalize(fixture.predicate()))).isEmpty();
    }

    @Test void inputDomainMismatchProducesEqualityWithoutVersionNameGuessing() {
        Fixture fixture = inputDomainGuard("requestVersion", "aggregateVersion", "!=", true);
        SemanticContext context = new SemanticContext(fixture.graph());

        assertThat(classify(new InputDomainEqualityClassifier(), fixture).constraint())
            .extracting(NormalizedConstraint::kind, NormalizedConstraint::targetPath,
                NormalizedConstraint::operator, NormalizedConstraint::expectedSource)
            .containsExactly(ConstraintKind.INPUT_TO_DOMAIN, "$", "EQUALS", "domain");
        assertThat(new OptimisticLockClassifier().classify(context,
            context.normalize(fixture.predicate()))).isEmpty();
    }

    private SemanticConstraintMatch classify(SemanticConstraintClassifier classifier, Fixture fixture) {
        SemanticContext context = new SemanticContext(fixture.graph());
        return classifier.classify(context, context.normalize(fixture.predicate())).orElseThrow();
    }

    private Fixture binary(String operator, FactNode expected, String inputName, boolean failureOnThen) {
        return binaryWithOperands(operator, input(inputName), expected, failureOnThen);
    }

    private Fixture binaryWithOperands(String operator, FactNode left, FactNode right, boolean failureOnThen) {
        FactNode root = root();
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range(),
            left.snippet() + " " + operator + " " + right.snippet(), TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("BinaryExpr", operator));
        FactNode parameter = parameter();
        FactNode failure = outcome();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, condition, left, right, parameter, failure), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("left", condition, left, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("right", condition, right, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("read", left.type() == FactNodeType.FIELD_ACCESS ? left : right,
                parameter, FactEdgeType.READS, -1, "DECLARATION"),
            edge("failure", condition, failure,
                failureOnThen ? FactEdgeType.THEN_OUTCOME : FactEdgeType.ELSE_OUTCOME, -1, "THROW")));
        return new Fixture(graph, predicate(graph, condition, failure));
    }

    private Fixture guard(String signature, String inputName) {
        FactNode root = root();
        FactNode call = new FactNode("guard", FactNodeType.METHOD_CALL, range(),
            "requireNonNull(" + inputName + ")", TypeResolution.resolvedSignature(signature),
            new FactNodePayload.MethodCallPayload("requireNonNull", 1, false));
        FactNode input = input(inputName); FactNode parameter = parameter();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(), List.of(root, call, input, parameter), List.of(
            edge("call", root, call, FactEdgeType.CALLS, -1, "CALL"),
            edge("argument", call, input, FactEdgeType.OPERAND_OF, 0, "ARGUMENT"),
            edge("read", input, parameter, FactEdgeType.READS, -1, "DECLARATION")));
        return new Fixture(graph, predicate(graph, call, call));
    }

    private Fixture booleanGuard(String signature, boolean failureOnThen) {
        FactNode root = root();
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range(), "isEmpty(value)",
            TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("MethodCallExpr", "MethodCallExpr"));
        FactNode call = new FactNode("guard", FactNodeType.METHOD_CALL, range(), "isEmpty(value)",
            TypeResolution.resolvedSignature(signature),
            new FactNodePayload.MethodCallPayload("isEmpty", 1, false));
        FactNode input = input("value"); FactNode parameter = parameter(); FactNode failure = outcome();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, condition, call, input, parameter, failure), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("call-operand", condition, call, FactEdgeType.OPERAND_OF, 0, "CALL"),
            edge("argument", call, input, FactEdgeType.OPERAND_OF, 0, "ARGUMENT"),
            edge("read", input, parameter, FactEdgeType.READS, -1, "DECLARATION"),
            edge("failure", condition, failure,
                failureOnThen ? FactEdgeType.THEN_OUTCOME : FactEdgeType.ELSE_OUTCOME, -1, "THROW")));
        return new Fixture(graph, predicate(graph, condition, failure));
    }

    private Fixture optionalLookup(String terminalSignature, String lookupName, String lookupSignature) {
        FactNode root = root();
        FactNode terminal = new FactNode("terminal", FactNodeType.METHOD_CALL, range(),
            "lookup.orElseThrow()", TypeResolution.resolvedSignature(terminalSignature),
            new FactNodePayload.MethodCallPayload("orElseThrow", 0, false));
        FactNode lookup = new FactNode("lookup", FactNodeType.METHOD_CALL, range(),
            "repository." + lookupName + "(id)", TypeResolution.resolvedSignature(lookupSignature),
            new FactNodePayload.MethodCallPayload(lookupName, 1, false));
        FactNode input = input("id"); FactNode parameter = parameter();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, terminal, lookup, input, parameter), List.of(
            edge("call", root, terminal, FactEdgeType.CALLS, -1, "CALL"),
            edge("receiver", terminal, lookup, FactEdgeType.OPERAND_OF, -1, "RECEIVER"),
            edge("argument", lookup, input, FactEdgeType.OPERAND_OF, 0, "ARGUMENT"),
            edge("read", input, parameter, FactEdgeType.READS, -1, "DECLARATION")));
        PredicateCandidate candidate = predicate(graph, terminal, terminal);
        candidate = new PredicateCandidate(candidate.candidateId(), candidate.graphId(),
            candidate.conditionNodeId(), PredicateType.LOOKUP_CHAIN, candidate.extractionStatus(),
            candidate.evidence(), candidate.diagnostics());
        return new Fixture(graph, candidate);
    }

    private Fixture authorizationGuard(String methodName, String signature, String rootOperator) {
        FactNode root = root();
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range(),
            "!".equals(rootOperator) ? "!" + methodName + "(target)" : methodName + "(target)",
            TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload(
                "!".equals(rootOperator) ? "UnaryExpr" : "MethodCallExpr", rootOperator));
        FactNode call = new FactNode("authorization-call", FactNodeType.METHOD_CALL, range(),
            methodName + "(target)", TypeResolution.resolvedSignature(signature),
            new FactNodePayload.MethodCallPayload(methodName, 1, false));
        FactNode target = input("target"); FactNode failure = outcome();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, condition, call, target, failure), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("call-operand", condition, call, FactEdgeType.OPERAND_OF, 0, "CALL"),
            edge("argument", call, target, FactEdgeType.OPERAND_OF, 0, "ARGUMENT"),
            edge("failure", condition, failure, FactEdgeType.THEN_OUTCOME, -1, "THROW")));
        return new Fixture(graph, predicate(graph, condition, failure));
    }

    private Fixture passwordGuard(String signature, String rootOperator) {
        FactNode root = root();
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range(), "!matches(raw, stored)",
            TypeResolution.notApplicable(), new FactNodePayload.ConditionPayload("UnaryExpr", rootOperator));
        FactNode call = new FactNode("password-call", FactNodeType.METHOD_CALL, range(), "matches(raw, stored)",
            TypeResolution.resolvedSignature(signature), new FactNodePayload.MethodCallPayload("matches", 2, false));
        FactNode raw = input("raw");
        FactNode stored = new FactNode("stored", FactNodeType.FIELD_ACCESS, range(), "user.password",
            TypeResolution.unresolved("DECLARATION_NOT_RESOLVED"),
            new FactNodePayload.FieldAccessPayload("password", "FieldAccessExpr"));
        FactNode parameter = parameter(); FactNode failure = outcome();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, condition, call, raw, stored, parameter, failure), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("call-operand", condition, call, FactEdgeType.OPERAND_OF, 0, "CALL"),
            edge("raw-argument", call, raw, FactEdgeType.OPERAND_OF, 0, "ARGUMENT"),
            edge("stored-argument", call, stored, FactEdgeType.OPERAND_OF, 1, "ARGUMENT"),
            edge("read", raw, parameter, FactEdgeType.READS, -1, "DECLARATION"),
            edge("failure", condition, failure, FactEdgeType.THEN_OUTCOME, -1, "THROW")));
        return new Fixture(graph, predicate(graph, condition, failure));
    }

    private Fixture inputDomainGuard(String inputName, String domainName, String operator,
                                     boolean failureOnThen) {
        FactNode root = root(); FactNode input = input(inputName); FactNode parameter = parameter();
        FactNode domain = new FactNode("domain", FactNodeType.FIELD_ACCESS, range(), domainName,
            TypeResolution.resolvedType("long"),
            new FactNodePayload.FieldAccessPayload(domainName, "FieldAccessExpr"));
        FactNode condition = new FactNode("condition", FactNodeType.CONDITION, range(),
            inputName + " " + operator + " " + domainName, TypeResolution.notApplicable(),
            new FactNodePayload.ConditionPayload("BinaryExpr", operator));
        FactNode failure = outcome();
        FactCodeGraph graph = new FactCodeGraph("graph", root.id(),
            List.of(root, condition, input, domain, parameter, failure), List.of(
            edge("control", root, condition, FactEdgeType.CONTROLS, -1, "IF"),
            edge("left", condition, input, FactEdgeType.OPERAND_OF, 0, "LEFT"),
            edge("right", condition, domain, FactEdgeType.OPERAND_OF, 1, "RIGHT"),
            edge("read", input, parameter, FactEdgeType.READS, -1, "DECLARATION"),
            edge("failure", condition, failure,
                failureOnThen ? FactEdgeType.THEN_OUTCOME : FactEdgeType.ELSE_OUTCOME, -1, "THROW")));
        return new Fixture(graph, predicate(graph, condition, failure));
    }

    private PredicateCandidate predicate(FactCodeGraph graph, FactNode condition, FactNode outcome) {
        EvidenceRef evidence = new EvidenceRef(condition.id(), range().relativePath(), 1, 1, 1, 20,
            EvidenceRole.PREDICATE, condition.snippet());
        return new PredicateCandidate("predicate", graph.graphId(), condition.id(), PredicateType.COMPOSITE,
            ExtractionStatus.EXTRACTED, List.of(evidence), List.of());
    }

    private FactNode input(String name) {
        return new FactNode("input", FactNodeType.FIELD_ACCESS, range(), name,
            TypeResolution.unresolved("DECLARATION_NOT_RESOLVED"),
            new FactNodePayload.FieldAccessPayload(name, "NameExpr"));
    }
    private FactNode parameter() {
        return new FactNode("parameter", FactNodeType.PARAMETER, range(), "long value",
            TypeResolution.resolvedType("long"), new FactNodePayload.ParameterPayload("value", 0, "long"));
    }
    private FactNode nullNode() {
        return new FactNode("constant", FactNodeType.NULL_LITERAL, range(), "null",
            TypeResolution.notApplicable(), new FactNodePayload.NullLiteralPayload());
    }
    private FactNode root() {
        return new FactNode("root", FactNodeType.API_METHOD, range(), "api()",
            TypeResolution.resolvedSignature("sample.Api.api()"),
            new FactNodePayload.MethodPayload("sample.Api", "api()", true));
    }
    private FactNode outcome() {
        return new FactNode("outcome", FactNodeType.THROW, range(), "throw failure",
            TypeResolution.notApplicable(), new FactNodePayload.OutcomePayload("THROW", "ThrowStmt"));
    }
    private FactEdge edge(String id, FactNode source, FactNode target, FactEdgeType type,
                          int ordinal, String role) {
        return new FactEdge(id, source.id(), target.id(), type, ordinal, role);
    }
    private SourceRange range() { return new SourceRange("src/Test.java", 1, 1, 1, 20); }
    private record Fixture(FactCodeGraph graph, PredicateCandidate predicate) {}
}
