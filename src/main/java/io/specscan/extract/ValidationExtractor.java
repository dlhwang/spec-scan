package io.specscan.extract;

import io.specscan.model.Condition;
import io.specscan.model.Operator;
import io.specscan.model.TypeSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns bean-validation annotations captured on a {@link TypeSchema} tree into
 * {path, operator, expected} conditions. Also emits enum-membership constraints.
 */
public final class ValidationExtractor {

    private ValidationExtractor() {
    }

    /** Walks a request schema rooted at "$" for the given location (BODY, QUERY, ...). */
    public static List<Condition> fromSchema(TypeSchema schema, boolean validated, String location) {
        List<Condition> out = new ArrayList<>();
        if (schema != null) walk(schema, "$", location, validated, out, 0);
        return out;
    }

    public static List<Condition> fromParamAnnotations(String paramPath, String location,
                                                       List<TypeSchema.FieldAnnotation> annotations) {
        List<Condition> out = new ArrayList<>();
        for (TypeSchema.FieldAnnotation a : annotations) {
            map(paramPath, location, a, true, out, null);
        }
        return out;
    }

    private static void walk(TypeSchema schema, String path, String location, boolean validated,
                             List<Condition> out, int depth) {
        if (schema == null || depth > 6) return;
        if (schema.fields != null) {
            for (Map.Entry<String, TypeSchema> e : schema.fields.entrySet()) {
                TypeSchema child = e.getValue();
                String childPath = path + "." + e.getKey();
                boolean array = child != null && "array".equals(child.type);
                for (TypeSchema.FieldAnnotation a : child == null ? List.<TypeSchema.FieldAnnotation>of() : child.annotations) {
                    map(childPath, location, a, validated, out, child);
                }
                if (child != null && child.enumValues != null && !child.enumValues.isEmpty()) {
                    out.add(new Condition(childPath, Operator.IN, child.enumValues, location,
                            "type-constraint", "enum " + shortName(child.javaType), null));
                }
                if (child != null && child.fields != null) {
                    walk(child, childPath, location, validated, out, depth + 1);
                }
                if (array && child.items != null) {
                    String itemPath = childPath + "[*]";
                    if (child.items.enumValues != null && !child.items.enumValues.isEmpty()) {
                        out.add(new Condition(itemPath, Operator.IN, child.items.enumValues, location,
                                "type-constraint", "enum " + shortName(child.items.javaType), null));
                    }
                    walk(child.items, itemPath, location, validated, out, depth + 1);
                }
            }
        }
    }

    private static void map(String path, String location, TypeSchema.FieldAnnotation a,
                            boolean validated, List<Condition> out, TypeSchema schema) {
        String note = validated ? null : "(주의: 컨트롤러 파라미터에 @Valid/@Validated 미적용)";
        String src = "bean-validation";
        Object message = a.members().get("message");
        String desc = join(message != null ? String.valueOf(message) : annotationLabel(a), note);

        switch (a.name()) {
            case "NotNull" -> out.add(cond(path, Operator.NOT_NULL, null, location, src, desc));
            case "NotEmpty" -> out.add(cond(path, Operator.NOT_EMPTY, null, location, src, desc));
            case "NotBlank" -> out.add(cond(path, Operator.NOT_BLANK, null, location, src, desc));
            case "Null" -> out.add(cond(path, Operator.NULL, null, location, src, desc));
            case "Size", "Length" -> {
                Object min = a.members().get("min");
                Object max = a.members().get("max");
                if (min != null) out.add(cond(path, Operator.GOE, min, location, src, desc));
                if (max != null) out.add(cond(path, Operator.LOE, max, location, src, desc));
            }
            case "Min", "DecimalMin" -> out.add(cond(path, Operator.GOE, a.members().getOrDefault("value", a.members().get("value")), location, src, desc));
            case "Max", "DecimalMax" -> out.add(cond(path, Operator.LOE, a.members().get("value"), location, src, desc));
            case "Positive" -> out.add(cond(path, Operator.GT, 0, location, src, desc));
            case "PositiveOrZero" -> out.add(cond(path, Operator.GOE, 0, location, src, desc));
            case "Negative" -> out.add(cond(path, Operator.LT, 0, location, src, desc));
            case "NegativeOrZero" -> out.add(cond(path, Operator.LOE, 0, location, src, desc));
            case "Email" -> out.add(cond(path, Operator.FORMAT, "email", location, src, desc));
            case "Pattern" -> out.add(cond(path, Operator.MATCHES, a.members().get("regexp"), location, src, desc));
            case "Past" -> out.add(cond(path, Operator.PAST, null, location, src, desc));
            case "PastOrPresent" -> out.add(cond(path, Operator.PAST_OR_PRESENT, null, location, src, desc));
            case "Future" -> out.add(cond(path, Operator.FUTURE, null, location, src, desc));
            case "FutureOrPresent" -> out.add(cond(path, Operator.FUTURE_OR_PRESENT, null, location, src, desc));
            case "AssertTrue" -> out.add(cond(path, Operator.TRUE, null, location, src, desc));
            case "AssertFalse" -> out.add(cond(path, Operator.FALSE, null, location, src, desc));
            default -> {
                // not a validation annotation — ignore
            }
        }
    }

    private static Condition cond(String path, Operator op, Object expected, String location,
                                  String source, String description) {
        return new Condition(path, op, expected, location, source, description, null);
    }

    private static String annotationLabel(TypeSchema.FieldAnnotation a) {
        return "@" + a.name() + (a.members().isEmpty() ? "" : a.members().toString());
    }

    private static String join(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " " + b;
    }

    private static String shortName(String qname) {
        if (qname == null) return null;
        return qname.substring(qname.lastIndexOf('.') + 1);
    }
}
