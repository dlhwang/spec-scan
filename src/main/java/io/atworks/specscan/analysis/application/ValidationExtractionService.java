package io.atworks.specscan.analysis.application;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import io.atworks.specscan.analysis.domain.*;
import io.atworks.specscan.analysis.support.*;
import io.atworks.specscan.ingestion.domain.IngestionErrorCode;
import io.atworks.specscan.ingestion.domain.IngestionException;
import io.atworks.specscan.ingestion.domain.IngestionWarning;
import io.atworks.specscan.ingestion.domain.RepositorySource;
import io.atworks.specscan.ingestion.domain.WorkspaceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ValidationExtractionService {

    private static final Pattern GETTER_CHAIN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.get[A-Z][A-Za-z0-9_]*\\(\\))+");
    private static final Pattern GETTER_PATTERN = Pattern.compile("\\.get([A-Z][A-Za-z0-9_]*)\\(\\)");
    private static final Pattern FIELD_COMPARISON_PATTERN = Pattern.compile("\\b([a-z][A-Za-z0-9_]*)\\s*(==|!=|<=|>=|<|>)");

    static {
        com.github.javaparser.StaticJavaParser.getConfiguration()
            .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);
    }

    public ValidationExtractionResult extract(StaticScanResult staticScanResult, RepositorySource repositorySource) throws IngestionException {
        long extractionStartedAt = System.nanoTime();
        List<ApiConditionDraft> directConditions = new ArrayList<>();
        List<ValidationCandidate> candidates = new ArrayList<>();
        List<IngestionWarning> warnings = new ArrayList<>();

        WorkspaceContext workspace = repositorySource.workspaceContext();
        if (workspace == null || workspace.workspacePath() == null) {
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Workspace context is not available"
            );
        }

        Path workspacePath = Paths.get(workspace.workspacePath());
        List<Path> sourceRoots = repositorySource.sourceRoots().stream()
            .map(r -> workspacePath.resolve(r.rootPath()))
            .filter(Files::exists)
            .collect(Collectors.toList());

        TypeResolver typeResolver = new TypeResolver(sourceRoots);
        ValidatorCandidateExtractor validatorExtractor = new ValidatorCandidateExtractor(workspacePath, sourceRoots);
        ServiceHintExtractor serviceExtractor = new ServiceHintExtractor(workspacePath, sourceRoots);

        int endpointCount = staticScanResult.endpoints().size();
        System.out.printf("  [Step 3] Prepared %d source roots; scanning %d endpoints%n", sourceRoots.size(), endpointCount);
        int endpointNumber = 0;
        for (ApiEndpoint endpoint : staticScanResult.endpoints()) {
            endpointNumber++;
            long endpointStartedAt = System.nanoTime();
            int conditionsBefore = directConditions.size();
            int candidatesBefore = candidates.size();
            int warningsBefore = warnings.size();
            System.out.printf(
                "  [Step 3][%d/%d] START %s %s (%s#%s)%n",
                endpointNumber,
                endpointCount,
                endpoint.httpMethod(),
                endpoint.path(),
                endpoint.controllerClass(),
                endpoint.controllerMethod()
            );

            EndpointTargetIndex targetIndex = buildEndpointTargetIndex(endpoint, typeResolver);

            for (RequestBinding binding : endpoint.requestBindings()) {
                if (binding.targetLocation() != BindingLocation.BODY) {
                    continue;
                }

                String typeStr = binding.type();
                typeResolver.resolveClassDeclaration(typeStr).ifPresent(dtoClass -> {
                    Path dtoFile = AstLookupUtils.getFilePath(dtoClass, workspacePath);
                    AnnotationConditionExtractor annotationExtractor = new AnnotationConditionExtractor(workspacePath, dtoFile);

                    try {
                        List<ApiConditionDraft> drafts = annotationExtractor.extractDirectConditions(dtoClass);
                        directConditions.addAll(drafts);

                        List<ValidationCandidate> annCandidates = annotationExtractor.extractValidationCandidates(dtoClass);
                        candidates.addAll(annCandidates);

                        annCandidates.forEach(cand -> {
                            List<ValidationCandidate> valCandidates = validatorExtractor.extractFromCustomAnnotation(
                                cand.evidenceSnippet().split(" ")[0].replace("@", ""),
                                cand.targetPath(),
                                dtoFile
                            );
                            candidates.addAll(valCandidates);
                        });

                    } catch (Exception e) {
                        warnings.add(new IngestionWarning(
                            "EXTRACTION_FAILED",
                            "Failed to extract annotations for DTO " + typeStr + ": " + e.getMessage(),
                            workspacePath.relativize(dtoFile).toString().replace("\\", "/"),
                            "LOW"
                        ));
                    }
                });
            }

            Optional<ClassOrInterfaceDeclaration> controllerClassOpt = typeResolver.resolveClassDeclaration(endpoint.controllerClass());
            controllerClassOpt.ifPresent(controllerDecl -> {
                controllerDecl.getMethodsByName(endpoint.controllerMethod()).forEach(method -> {
                    method.findAll(MethodCallExpr.class).forEach(call -> {
                        String calledMethod = call.getNameAsString();
                        call.getScope().ifPresent(scope -> {
                            String scopeVar = scope.toString();
                            String serviceType = AstLookupUtils.findFieldType(controllerDecl, scopeVar);
                            if (serviceType != null) {
                                try {
                                    long serviceScanStartedAt = System.nanoTime();
                                    System.out.printf("    - Service scan START %s.%s%n", serviceType, calledMethod);
                                    List<ValidationCandidate> serviceHints = serviceExtractor.extractFromServiceMethod(serviceType, calledMethod);
                                    candidates.addAll(resolveServiceHintCandidates(serviceHints, endpoint, targetIndex, warnings));
                                    System.out.printf(
                                        "    - Service scan DONE  %s.%s: %d hints (%s)%n",
                                        serviceType,
                                        calledMethod,
                                        serviceHints.size(),
                                        elapsed(serviceScanStartedAt)
                                    );
                                } catch (Exception e) {
                                    warnings.add(new IngestionWarning(
                                        "SERVICE_SCAN_FAILED",
                                        "Failed to extract hints from service " + serviceType + "." + calledMethod + ": " + e.getMessage(),
                                        endpoint.controllerClass(),
                                        "LOW"
                                    ));
                                }
                            }
                        });
                    });
                });
            });

            String operationKey = io.atworks.specscan.analysis.domain.output.OperationKey.of(endpoint).externalKey();
            for (int candidateIndex = candidatesBefore; candidateIndex < candidates.size(); candidateIndex++) {
                ValidationCandidate candidate = candidates.get(candidateIndex);
                if (candidate.operationKey() == null) {
                    candidates.set(candidateIndex, candidate.withOperationKey(operationKey));
                }
            }

            System.out.printf(
                "  [Step 3][%d/%d] DONE  +%d conditions, +%d candidates, +%d warnings (%s)%n",
                endpointNumber,
                endpointCount,
                directConditions.size() - conditionsBefore,
                candidates.size() - candidatesBefore,
                warnings.size() - warningsBefore,
                elapsed(endpointStartedAt)
            );
        }

        System.out.printf(
            "  [Step 3] Completed %d endpoints: %d conditions, %d candidates, %d warnings (%s)%n",
            endpointCount,
            directConditions.size(),
            candidates.size(),
            warnings.size(),
            elapsed(extractionStartedAt)
        );

        return new ValidationExtractionResult(
            directConditions,
            candidates,
            warnings
        );
    }

    private static String elapsed(long startedAt) {
        double seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        return String.format(java.util.Locale.ROOT, "%.1fs", seconds);
    }

    private List<ValidationCandidate> resolveServiceHintCandidates(
        List<ValidationCandidate> serviceHints,
        ApiEndpoint endpoint,
        EndpointTargetIndex targetIndex,
        List<IngestionWarning> warnings
    ) {
        List<ValidationCandidate> resolved = new ArrayList<>();
        for (ValidationCandidate candidate : serviceHints) {
            ServiceHintResolution resolution = resolveServiceHintTarget(candidate, targetIndex);
            if (resolution.targetPath() != null) {
                resolved.add(new ValidationCandidate(
                    candidate.candidateId(),
                    candidate.sourceType(),
                    resolution.targetPath(),
                    candidate.evidenceSnippet(),
                    candidate.confidence(),
                    candidate.sourceTrace()
                ));
                continue;
            }

            warnings.add(new IngestionWarning(
                resolution.ambiguous() ? "SERVICE_HINT_AMBIGUOUS" : "SERVICE_HINT_REJECTED",
                resolution.ambiguous()
                    ? "Skipped ambiguous service hint target for " + endpoint.path() + ": " + candidate.evidenceSnippet()
                    : "Skipped unresolved service hint target for " + endpoint.path() + ": " + candidate.evidenceSnippet(),
                endpoint.path(),
                "MEDIUM",
                Map.of(
                    "endpoint", endpoint.httpMethod() + " " + endpoint.path(),
                    "candidateId", candidate.candidateId(),
                    "targetPath", String.valueOf(candidate.targetPath()),
                    "reasonCategory", resolution.ambiguous() ? "AMBIGUOUS_GRAPH_EVIDENCE" : "NO_QUALIFYING_RULE"
                )
            ));
        }
        return resolved;
    }

    private ServiceHintResolution resolveServiceHintTarget(ValidationCandidate candidate, EndpointTargetIndex targetIndex) {
        Set<String> uniqueLeafMatches = new LinkedHashSet<>();
        boolean ambiguous = false;

        for (String token : extractTargetTokens(candidate)) {
            String normalized = normalizeTargetToken(token);
            if (normalized.isBlank()) {
                continue;
            }

            String exactOrEquivalent = findExactOrEquivalentTarget(normalized, targetIndex);
            if (exactOrEquivalent != null) {
                return new ServiceHintResolution(exactOrEquivalent, false);
            }

            List<String> matches = findLeafMatches(normalized, targetIndex);
            if (matches.size() == 1) {
                uniqueLeafMatches.add(matches.get(0));
            } else if (matches.size() > 1) {
                ambiguous = true;
            }
        }

        if (uniqueLeafMatches.size() == 1) {
            return new ServiceHintResolution(uniqueLeafMatches.iterator().next(), false);
        }
        if (uniqueLeafMatches.size() > 1) {
            return new ServiceHintResolution(null, true);
        }

        String intrinsicTarget = inferIntrinsicServiceHintTarget(candidate.evidenceSnippet());
        if (!intrinsicTarget.isBlank()) {
            return new ServiceHintResolution(intrinsicTarget, false);
        }
        return new ServiceHintResolution(null, ambiguous);
    }

    private List<String> extractTargetTokens(ValidationCandidate candidate) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String evidence = candidate.evidenceSnippet();
        if (evidence != null && !evidence.isBlank()) {
            Matcher chainMatcher = GETTER_CHAIN_PATTERN.matcher(evidence);
            while (chainMatcher.find()) {
                String getterPath = toGetterPath(chainMatcher.group());
                if (!getterPath.isBlank()) {
                    tokens.add(getterPath);
                }
            }

            Matcher fieldComparisonMatcher = FIELD_COMPARISON_PATTERN.matcher(evidence);
            while (fieldComparisonMatcher.find()) {
                tokens.add(fieldComparisonMatcher.group(1));
            }
        }

        tokens.add(candidate.targetPath());
        return new ArrayList<>(tokens);
    }

    private String toGetterPath(String expression) {
        List<String> segments = new ArrayList<>();
        Matcher matcher = GETTER_PATTERN.matcher(expression);
        while (matcher.find()) {
            segments.add(decapitalize(matcher.group(1)));
        }
        return String.join(".", segments);
    }

    private EndpointTargetIndex buildEndpointTargetIndex(ApiEndpoint endpoint, TypeResolver typeResolver) {
        Set<String> fullPaths = new LinkedHashSet<>();
        Set<String> parameterTargets = new LinkedHashSet<>();

        for (RequestBinding binding : endpoint.requestBindings()) {
            if (binding.targetLocation() != BindingLocation.BODY) {
                parameterTargets.add(binding.parameterName());
            }

            Map<String, Object> schema = typeResolver.resolveExpandedSchema(binding.type(), List.of(), List.of());
            collectPropertyPaths("", schema, fullPaths);
        }

        Map<String, List<String>> pathsByLeaf = new LinkedHashMap<>();
        for (String path : fullPaths) {
            pathsByLeaf.computeIfAbsent(leafName(path), _k -> new ArrayList<>()).add(path);
        }
        return new EndpointTargetIndex(fullPaths, parameterTargets, pathsByLeaf);
    }

    @SuppressWarnings("unchecked")
    private void collectPropertyPaths(String prefix, Map<String, Object> schema, Set<String> fullPaths) {
        if (schema == null || schema.isEmpty()) {
            return;
        }

        Object propertiesObj = schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> properties) {
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                if (!(entry.getKey() instanceof String propertyName) || !(entry.getValue() instanceof Map<?, ?> childSchemaRaw)) {
                    continue;
                }
                Map<String, Object> childSchema = (Map<String, Object>) childSchemaRaw;
                String path = prefix.isBlank() ? propertyName : prefix + "." + propertyName;
                fullPaths.add(path);
                collectPropertyPaths(path, childSchema, fullPaths);
            }
        }

        Object itemsObj = schema.get("items");
        if (itemsObj instanceof Map<?, ?> itemsRaw) {
            collectPropertyPaths(prefix + "[*]", (Map<String, Object>) itemsRaw, fullPaths);
        }
    }

    private String normalizeTargetToken(String token) {
        if (token == null) {
            return "";
        }
        String normalized = token.trim();
        if (normalized.isEmpty() || normalized.equals("unknown")) {
            return "";
        }
        if (normalized.startsWith("$.")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private String findExactOrEquivalentTarget(String normalized, EndpointTargetIndex targetIndex) {
        for (String path : targetIndex.fullPaths()) {
            if (path.equals(normalized) || areEquivalentTargets(path, normalized)) {
                return path;
            }
        }
        for (String parameter : targetIndex.parameterTargets()) {
            if (parameter.equals(normalized) || areEquivalentTargets(parameter, normalized)) {
                return parameter;
            }
        }
        return null;
    }

    private List<String> findLeafMatches(String normalized, EndpointTargetIndex targetIndex) {
        List<String> matches = new ArrayList<>(targetIndex.pathsByLeaf().getOrDefault(leafName(normalized), List.of()));
        if (!matches.isEmpty()) {
            return matches;
        }
        List<String> equivalentMatches = new ArrayList<>();
        for (String path : targetIndex.fullPaths()) {
            if (areEquivalentTargets(leafName(path), normalized)) {
                equivalentMatches.add(path);
            }
        }
        return equivalentMatches;
    }

    private boolean areEquivalentTargets(String left, String right) {
        return canonicalTarget(left).equals(canonicalTarget(right));
    }

    private String canonicalTarget(String token) {
        String normalized = normalizeTargetToken(token)
            .toLowerCase()
            .replace("[*]", "")
            .replace(".", "")
            .replace("_", "")
            .replace("-", "")
            .replace("number", "no");
        return normalized;
    }

    private String leafName(String path) {
        String normalized = normalizeTargetToken(path);
        if (normalized.isBlank()) {
            return "";
        }
        int arrayIndex = normalized.lastIndexOf("[*].");
        if (arrayIndex != -1) {
            return normalized.substring(arrayIndex + 4);
        }
        int dotIndex = normalized.lastIndexOf('.');
        return dotIndex == -1 ? normalized : normalized.substring(dotIndex + 1);
    }

    private String inferIntrinsicServiceHintTarget(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return "";
        }
        String normalized = evidence.replaceAll("\\s+", "").toLowerCase();
        if (normalized.contains("permission") || normalized.contains("authorized") || normalized.contains("role")) {
            return "currentUser";
        }
        if (normalized.contains("state") || normalized.contains("status") || normalized.contains("shipped")
            || normalized.contains("cancelled") || normalized.contains("canceled")) {
            return "order.state";
        }
        return "";
    }

    private String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private record EndpointTargetIndex(
        Set<String> fullPaths,
        Set<String> parameterTargets,
        Map<String, List<String>> pathsByLeaf
    ) {}

    private record ServiceHintResolution(String targetPath, boolean ambiguous) {}
}
