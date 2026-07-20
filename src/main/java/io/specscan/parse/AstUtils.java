package io.specscan.parse;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.CharLiteralExpr;
import com.github.javaparser.ast.expr.DoubleLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Small AST helpers shared by extractors. */
public final class AstUtils {

    private AstUtils() {
    }

    /** Finds an annotation by its simple (last-segment) name, e.g. "RequestBody". */
    public static Optional<AnnotationExpr> annotation(NodeWithAnnotations<?> node, String... simpleNames) {
        for (AnnotationExpr a : node.getAnnotations()) {
            String id = a.getName().getIdentifier();
            for (String want : simpleNames) {
                if (id.equals(want)) return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    public static boolean hasAnnotation(NodeWithAnnotations<?> node, String... simpleNames) {
        return annotation(node, simpleNames).isPresent();
    }

    /** All member values of an annotation. Single-member value is keyed "value". */
    public static Map<String, Object> annotationMembers(AnnotationExpr a) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (a instanceof SingleMemberAnnotationExpr s) {
            out.put("value", literalValue(s.getMemberValue()));
        } else if (a instanceof NormalAnnotationExpr n) {
            n.getPairs().forEach(p -> out.put(p.getNameAsString(), literalValue(p.getValue())));
        }
        return out;
    }

    public static Object annotationMember(AnnotationExpr a, String... names) {
        Map<String, Object> members = annotationMembers(a);
        for (String n : names) {
            if (members.containsKey(n)) return members.get(n);
        }
        return null;
    }

    /** Best-effort literal evaluation. Enum constants become their simple name. */
    public static Object literalValue(Expression e) {
        if (e == null) return null;
        if (e instanceof StringLiteralExpr s) return s.asString();
        if (e instanceof IntegerLiteralExpr i) return i.asNumber();
        if (e instanceof LongLiteralExpr l) return l.asNumber();
        if (e instanceof DoubleLiteralExpr d) return d.asDouble();
        if (e instanceof BooleanLiteralExpr b) return b.getValue();
        if (e instanceof CharLiteralExpr c) return c.asChar();
        if (e instanceof NullLiteralExpr) return null;
        if (e instanceof UnaryExpr u && u.getOperator() == UnaryExpr.Operator.MINUS) {
            Object inner = literalValue(u.getExpression());
            if (inner instanceof Number n) return negate(n);
        }
        if (e instanceof FieldAccessExpr f) return f.getNameAsString();   // Enum constant / static const
        if (e instanceof NameExpr n) return n.getNameAsString();
        if (e instanceof ArrayInitializerExpr arr) {
            List<Object> values = new ArrayList<>();
            arr.getValues().forEach(v -> values.add(literalValue(v)));
            return values;
        }
        return e.toString();
    }

    /** True when the expression is a compile-time literal (not just name-resolvable). */
    public static boolean isLiteral(Expression e) {
        if (e instanceof UnaryExpr u && u.getOperator() == UnaryExpr.Operator.MINUS) {
            return isLiteral(u.getExpression());
        }
        return e instanceof StringLiteralExpr || e instanceof IntegerLiteralExpr
                || e instanceof LongLiteralExpr || e instanceof DoubleLiteralExpr
                || e instanceof BooleanLiteralExpr || e instanceof CharLiteralExpr
                || e instanceof NullLiteralExpr
                || e instanceof FieldAccessExpr fae && isUpperConst(fae.getNameAsString());
    }

    private static boolean isUpperConst(String name) {
        return name.equals(name.toUpperCase());
    }

    private static Number negate(Number n) {
        if (n instanceof Integer i) return -i;
        if (n instanceof Long l) return -l;
        if (n instanceof Double d) return -d;
        return -n.doubleValue();
    }

    /** file:line descriptor for provenance. */
    public static String at(Node node) {
        String file = node.findCompilationUnit()
                .flatMap(CompilationUnit::getStorage)
                .map(s -> s.getPath().getFileName().toString())
                .orElse("?");
        int line = node.getRange().map(r -> r.begin.line).orElse(0);
        return file + ":" + line;
    }

    /** Strips a getter prefix: getFoo -> foo, isBar -> bar, record accessor foo() -> foo. */
    public static Optional<String> propertyOfAccessor(String methodName, int argCount) {
        if (argCount != 0) return Optional.empty();
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Optional.of(decap(methodName.substring(3)));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Optional.of(decap(methodName.substring(2)));
        }
        // record-style accessor: any other no-arg call is treated as accessor by callers
        return Optional.of(methodName);
    }

    public static String decap(String s) {
        if (s.isEmpty() || (s.length() > 1 && Character.isUpperCase(s.charAt(1)))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}
