package io.atworks.specscan.analysis.support;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import io.atworks.specscan.analysis.domain.ApiConditionDraft;
import io.atworks.specscan.analysis.domain.ValidationCandidate;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AnnotationConditionExtractor {

    private final Path workspaceRoot;
    private final Path currentFile;

    public AnnotationConditionExtractor(Path workspaceRoot, Path currentFile) {
        this.workspaceRoot = workspaceRoot;
        this.currentFile = currentFile;
    }

    public List<ApiConditionDraft> extractDirectConditions(ClassOrInterfaceDeclaration dtoClass) {
        List<ApiConditionDraft> list = new ArrayList<>();
        dtoClass.getFields().forEach(field -> {
            if (field.getVariables().isEmpty()) {
                return;
            }
            String fieldName = field.getVariable(0).getNameAsString();
            field.getAnnotations().forEach(ann -> {
                String name = ann.getNameAsString();
                if (isStandardValidationAnnotation(name)) {
                    String operator = resolveStandardOperator(name);
                    String expected = resolveExpectedValue(ann);
                    String evidence = ann.toString() + " " + field.toString().trim();
                    SourceTrace trace = SourceTraceResolver.resolve(ann, workspaceRoot, currentFile);
                    list.add(new ApiConditionDraft(fieldName, operator, expected, evidence, trace));
                }
            });
        });
        return list;
    }

    public List<ValidationCandidate> extractValidationCandidates(ClassOrInterfaceDeclaration dtoClass) {
        List<ValidationCandidate> list = new ArrayList<>();
        dtoClass.getFields().forEach(field -> {
            if (field.getVariables().isEmpty()) {
                return;
            }
            String fieldName = field.getVariable(0).getNameAsString();
            field.getAnnotations().forEach(ann -> {
                String name = ann.getNameAsString();
                if (!isStandardValidationAnnotation(name) && !isIgnoredAnnotation(name)) {
                    String candidateId = "cand-ann-" + UUID.randomUUID().toString().substring(0, 8);
                    String evidenceSnippet = ann.toString() + " " + field.toString().trim();
                    SourceTrace trace = SourceTraceResolver.resolve(ann, workspaceRoot, currentFile);
                    list.add(new ValidationCandidate(
                        candidateId,
                        "CUSTOM_ANNOTATION",
                        fieldName,
                        evidenceSnippet,
                        1.0,
                        trace
                    ));
                }
            });
        });
        return list;
    }

    private boolean isStandardValidationAnnotation(String name) {
        return name.equals("NotNull") || name.equals("NotEmpty") || name.equals("NotBlank") ||
               name.equals("Size") || name.equals("Min") || name.equals("Max") ||
               name.equals("Pattern") || name.equals("Email") ||
               name.equals("AssertTrue") || name.equals("AssertFalse");
    }

    private boolean isIgnoredAnnotation(String name) {
        // getter/setter/constructor 등 Lombok이나 자바 표준 애노테이션
        return name.equals("Getter") || name.equals("Setter") || name.equals("Builder") ||
               name.equals("NoArgsConstructor") || name.equals("AllArgsConstructor") ||
               name.equals("Override") || name.equals("Deprecated");
    }

    private String resolveStandardOperator(String name) {
        switch (name) {
            case "NotNull": return "NOT_NULL";
            case "NotEmpty": return "NOT_EMPTY";
            case "NotBlank": return "NOT_BLANK";
            case "Size": return "SIZE";
            case "Min": return "MIN";
            case "Max": return "MAX";
            case "Pattern": return "PATTERN";
            case "Email": return "EMAIL";
            case "AssertTrue": return "ASSERT_TRUE";
            case "AssertFalse": return "ASSERT_FALSE";
            default: return name.toUpperCase();
        }
    }

    private String resolveExpectedValue(AnnotationExpr ann) {
        if (ann instanceof SingleMemberAnnotationExpr single) {
            return single.getMemberValue().toString();
        } else if (ann instanceof NormalAnnotationExpr normal) {
            List<String> list = new ArrayList<>();
            for (MemberValuePair pair : normal.getPairs()) {
                list.add(pair.getNameAsString() + "=" + pair.getValue().toString());
            }
            return String.join(", ", list);
        }
        return "true"; // 파라미터가 없는 경우 기본 true
    }
}
