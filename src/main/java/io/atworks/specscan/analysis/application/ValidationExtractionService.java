package io.atworks.specscan.analysis.application;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ValidationExtractionService {

    static {
        com.github.javaparser.StaticJavaParser.getConfiguration()
            .setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);
    }

    public ValidationExtractionResult extract(StaticScanResult staticScanResult, RepositorySource repositorySource) throws IngestionException {
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

        for (ApiEndpoint endpoint : staticScanResult.endpoints()) {
            // 1. Request bindings 중 DTO(BODY 및 QUERY 복잡 객체) 탐색
            for (RequestBinding binding : endpoint.requestBindings()) {
                String typeStr = binding.type();
                typeResolver.resolveClassDeclaration(typeStr).ifPresent(dtoClass -> {
                    Path dtoFile = getFilePath(dtoClass, workspacePath);
                    AnnotationConditionExtractor annotationExtractor = new AnnotationConditionExtractor(workspacePath, dtoFile);

                    try {
                        // 1-1. Annotation 기반 Direct 및 Candidate 추출
                        List<ApiConditionDraft> drafts = annotationExtractor.extractDirectConditions(dtoClass);
                        directConditions.addAll(drafts);

                        List<ValidationCandidate> annCandidates = annotationExtractor.extractValidationCandidates(dtoClass);
                        candidates.addAll(annCandidates);

                        // 1-2. 커스텀 어노테이션이 있는 경우 연동된 ConstraintValidator 분석
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

            // 2. 컨트롤러 메소드 내의 서비스 체인(MethodCall) 탐색 및 Service Hint 추출
            Optional<ClassOrInterfaceDeclaration> controllerClassOpt = typeResolver.resolveClassDeclaration(endpoint.controllerClass());
            controllerClassOpt.ifPresent(controllerDecl -> {
                controllerDecl.getMethodsByName(endpoint.controllerMethod()).forEach(method -> {
                    method.findAll(MethodCallExpr.class).forEach(call -> {
                        String calledMethod = call.getNameAsString();
                        call.getScope().ifPresent(scope -> {
                            String scopeVar = scope.toString();
                            // 컨트롤러 내 필드 선언 목록에서 scopeVar(예: userService)의 타입 획득
                            String serviceType = findFieldType(controllerDecl, scopeVar);
                            if (serviceType != null) {
                                try {
                                    List<ValidationCandidate> serviceHints = serviceExtractor.extractFromServiceMethod(serviceType, calledMethod);
                                    candidates.addAll(serviceHints);
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
        }

        return new ValidationExtractionResult(
            directConditions,
            candidates,
            warnings
        );
    }

    private String findFieldType(ClassOrInterfaceDeclaration clazz, String fieldName) {
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.getVariables().stream().anyMatch(v -> v.getNameAsString().equals(fieldName))) {
                return field.getElementType().asString();
            }
        }
        return null;
    }

    private Path getFilePath(ClassOrInterfaceDeclaration clazz, Path workspacePath) {
        if (clazz.findCompilationUnit().isPresent() && clazz.findCompilationUnit().get().getStorage().isPresent()) {
            return clazz.findCompilationUnit().get().getStorage().get().getPath();
        }
        return workspacePath;
    }
}
