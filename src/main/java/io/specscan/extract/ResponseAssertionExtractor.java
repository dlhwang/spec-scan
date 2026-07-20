package io.specscan.extract;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import io.specscan.model.Condition;
import io.specscan.model.Operator;
import io.specscan.model.TypeSchema;
import io.specscan.parse.AstUtils;
import io.specscan.parse.ProjectIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Follows the success return path of a handler (through project service calls, static
 * factories, builders and constructors) to discover which response fields are populated,
 * and emits response assertions such as {$.result, not_empty}.
 */
public class ResponseAssertionExtractor {

    public record Result(Integer status, String actualTypeQName, List<Condition> assertions) {
    }

    private static final int MAX_DEPTH = 5;
    public static final Map<String, Integer> HTTP_STATUS = Map.ofEntries(
            Map.entry("OK", 200), Map.entry("CREATED", 201), Map.entry("ACCEPTED", 202),
            Map.entry("NO_CONTENT", 204), Map.entry("BAD_REQUEST", 400), Map.entry("UNAUTHORIZED", 401),
            Map.entry("FORBIDDEN", 403), Map.entry("NOT_FOUND", 404), Map.entry("CONFLICT", 409),
            Map.entry("INTERNAL_SERVER_ERROR", 500));

    private final ProjectIndex index;
    private final Set<Object> visiting = new HashSet<>();

    public ResponseAssertionExtractor(ProjectIndex index) {
        this.index = index;
    }

    public Result extract(MethodDeclaration handler, TypeSchema declaredSchema) {
        Integer status = null;
        Shape best = null;
        int bestScore = Integer.MIN_VALUE;

        for (ReturnStmt rs : handler.getBody().map(b -> b.findAll(ReturnStmt.class)).orElse(List.of())) {
            if (rs.getExpression().isEmpty()) continue;
            Unwrapped uw = unwrapResponseEntity(rs.getExpression().get());
            if (uw.status != null && (status == null || uw.status < status)) status = uw.status;
            if (uw.body == null) continue;
            Shape shape = shapeOf(uw.body, enclosingMethod(rs), 0);
            int score = score(shape);
            if (score > bestScore) {
                bestScore = score;
                best = shape;
            }
        }

        List<Condition> assertions = new ArrayList<>();
        String actualType = null;
        if (best != null) {
            actualType = best.typeQName;
            Map<String, Condition> dedup = new LinkedHashMap<>();
            emit(best, "$", declaredSchema, dedup, 0);
            assertions.addAll(dedup.values());
        }
        return new Result(status, actualType, assertions);
    }

    private static MethodDeclaration enclosingMethod(com.github.javaparser.ast.Node n) {
        return n.findAncestor(MethodDeclaration.class).orElse(null);
    }

    // ------------------------------------------------------------------ ResponseEntity unwrapping

    private record Unwrapped(Integer status, Expression body) {
    }

    private Unwrapped unwrapResponseEntity(Expression e) {
        e = unwrap(e);
        if (!(e instanceof MethodCallExpr)) return new Unwrapped(null, e);

        // flatten call chain down to the root scope
        List<MethodCallExpr> chain = new ArrayList<>();
        Expression cur = e;
        while (cur instanceof MethodCallExpr mc) {
            chain.add(0, mc);
            cur = mc.getScope().orElse(null);
        }
        boolean isResponseEntity = cur instanceof NameExpr n && n.getNameAsString().equals("ResponseEntity");
        if (!isResponseEntity) return new Unwrapped(null, e);

        Integer status = null;
        Expression body = null;
        for (MethodCallExpr mc : chain) {
            switch (mc.getNameAsString()) {
                case "ok" -> {
                    status = 200;
                    if (!mc.getArguments().isEmpty()) body = mc.getArgument(0);
                }
                case "created" -> status = 201;
                case "accepted" -> status = 202;
                case "noContent" -> status = 204;
                case "badRequest" -> status = 400;
                case "notFound" -> status = 404;
                case "status" -> status = statusValue(mc);
                case "body" -> {
                    if (!mc.getArguments().isEmpty()) body = mc.getArgument(0);
                }
                case "of" -> {
                    if (!mc.getArguments().isEmpty()) body = mc.getArgument(0);
                }
                default -> {
                }
            }
        }
        return new Unwrapped(status, body);
    }

    private static Integer statusValue(MethodCallExpr statusCall) {
        if (statusCall.getArguments().isEmpty()) return null;
        Expression arg = statusCall.getArgument(0);
        if (arg instanceof FieldAccessExpr fa) return HTTP_STATUS.get(fa.getNameAsString());
        Object v = AstUtils.literalValue(arg);
        if (v instanceof Number num) return num.intValue();
        if (v instanceof String s) return HTTP_STATUS.get(s);
        return null;
    }

    // ------------------------------------------------------------------ shape analysis

    /** What the success path constructs. */
    static final class Shape {
        String typeQName;
        Map<String, Shape> fields;
        boolean isLiteral;
        Object literal;
        String exprText;
        String jsonTypeHint;

        static Shape literal(Object v) {
            Shape s = new Shape();
            s.isLiteral = true;
            s.literal = v;
            return s;
        }

        static Shape leaf(String text, String hint) {
            Shape s = new Shape();
            s.exprText = text;
            s.jsonTypeHint = hint;
            return s;
        }
    }

    private Shape shapeOf(Expression e, MethodDeclaration enclosing, int depth) {
        if (e == null || depth > MAX_DEPTH + 6) return null;
        e = unwrap(e);

        if (AstUtils.isLiteral(e)) return Shape.literal(AstUtils.literalValue(e));

        if (e instanceof ObjectCreationExpr oce) return shapeOfCreation(oce, enclosing, depth);

        if (e instanceof MethodCallExpr mc) {
            if (mc.getNameAsString().equals("build")) {
                Shape s = shapeOfBuilder(mc, enclosing, depth);
                if (s != null) return s;
            }
            Shape viaCall = shapeOfCall(mc, enclosing, depth);
            if (viaCall != null) return viaCall;
            return Shape.leaf(e.toString(), hintOf(e));
        }
        if (e instanceof NameExpr n) {
            // local variable: follow its initializer, keep the declared-type hint
            if (enclosing != null) {
                Optional<VariableDeclarator> vd = enclosing.findAll(VariableDeclarator.class).stream()
                        .filter(v -> v.getNameAsString().equals(n.getNameAsString()))
                        .findFirst();
                if (vd.isPresent()) {
                    String hint = SchemaBuilder.jsonTypeOf(simpleName(vd.get().getType().asString()));
                    Shape s = vd.get().getInitializer().map(init -> shapeOf(init, enclosing, depth + 1)).orElse(null);
                    if (s != null && s.exprText != null && s.jsonTypeHint == null) s.jsonTypeHint = hint;
                    return s != null ? s : Shape.leaf(n.getNameAsString(), hint);
                }
                // method parameter
                for (Parameter p : enclosing.getParameters()) {
                    if (p.getNameAsString().equals(n.getNameAsString())) {
                        return Shape.leaf(n.getNameAsString(), SchemaBuilder.jsonTypeOf(simpleName(p.getType().asString())));
                    }
                }
            }
            return Shape.leaf(n.getNameAsString(), hintOf(e));
        }
        return Shape.leaf(e.toString(), hintOf(e));
    }

    private Shape shapeOfCreation(ObjectCreationExpr oce, MethodDeclaration enclosing, int depth) {
        Optional<TypeDeclaration<?>> decl = TypeLookup.find(index, oce.getType().getNameWithScope(),
                enclosing != null ? enclosing.findAncestor(TypeDeclaration.class).map(t -> (TypeDeclaration<?>) t).orElse(null) : null);
        Shape s = new Shape();
        s.fields = new LinkedHashMap<>();
        if (decl.isEmpty()) {
            s.exprText = oce.toString();
            return s;
        }
        s.typeQName = decl.get().getFullyQualifiedName().orElse(null);

        List<Shape> argShapes = new ArrayList<>();
        for (Expression a : oce.getArguments()) argShapes.add(shapeOf(a, enclosing, depth + 1));

        Optional<ConstructorDeclaration> ctor = decl.get() instanceof ClassOrInterfaceDeclaration cid
                ? cid.getConstructors().stream().filter(c -> c.getParameters().size() == oce.getArguments().size()).findFirst()
                : Optional.empty();
        if (ctor.isPresent()) {
            Map<String, Shape> byParam = new LinkedHashMap<>();
            List<Parameter> params = ctor.get().getParameters();
            for (int i = 0; i < params.size() && i < argShapes.size(); i++) {
                byParam.put(params.get(i).getNameAsString(), argShapes.get(i));
            }
            ctor.get().getBody().findAll(AssignExpr.class).forEach(ae -> {
                if (ae.getTarget() instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr
                        && ae.getValue() instanceof NameExpr val) {
                    Shape v = byParam.get(val.getNameAsString());
                    if (v != null) s.fields.put(fa.getNameAsString(), v);
                }
            });
        }
        if (s.fields.isEmpty()) {
            List<String> names = fieldNames(decl.get());
            if (names.size() == argShapes.size()) {
                for (int i = 0; i < names.size(); i++) {
                    if (argShapes.get(i) != null) s.fields.put(names.get(i), argShapes.get(i));
                }
            }
        }
        return s;
    }

    private Shape shapeOfBuilder(MethodCallExpr buildCall, MethodDeclaration enclosing, int depth) {
        Map<String, Shape> fields = new LinkedHashMap<>();
        Expression cur = buildCall.getScope().orElse(null);
        while (cur instanceof MethodCallExpr mc) {
            if (mc.getNameAsString().equals("builder")) {
                String typeText = mc.getScope().map(Object::toString).orElse(null);
                if (typeText == null) return null;
                Optional<TypeDeclaration<?>> decl = TypeLookup.find(index, typeText,
                        enclosing != null ? enclosing.findAncestor(TypeDeclaration.class).map(t -> (TypeDeclaration<?>) t).orElse(null) : null);
                Shape s = new Shape();
                s.fields = fields;
                s.typeQName = decl.flatMap(TypeDeclaration::getFullyQualifiedName).orElse(simpleName(typeText));
                return s;
            }
            if (mc.getArguments().size() == 1) {
                fields.putIfAbsent(mc.getNameAsString(), shapeOf(mc.getArgument(0), enclosing, depth + 1));
            }
            cur = mc.getScope().orElse(null);
        }
        return null;
    }

    /** Follows a project method call (service call, static factory) to its return shapes. */
    private Shape shapeOfCall(MethodCallExpr mc, MethodDeclaration enclosing, int depth) {
        if (depth > MAX_DEPTH) return null;
        MethodDeclaration target = resolveTarget(mc, enclosing);
        if (target == null || target.getBody().isEmpty() || !visiting.add(target)) return null;
        try {
            Shape best = null;
            int bestScore = Integer.MIN_VALUE;
            for (ReturnStmt rs : target.getBody().get().findAll(ReturnStmt.class)) {
                if (rs.getExpression().isEmpty()) continue;
                Shape s = shapeOf(rs.getExpression().get(), target, depth + 1);
                int sc = score(s);
                if (sc > bestScore) {
                    bestScore = sc;
                    best = s;
                }
            }
            return best;
        } finally {
            visiting.remove(target);
        }
    }

    private MethodDeclaration resolveTarget(MethodCallExpr mc, MethodDeclaration enclosing) {
        TypeDeclaration<?> context = enclosing != null
                ? enclosing.findAncestor(TypeDeclaration.class).map(t -> (TypeDeclaration<?>) t).orElse(null)
                : null;
        try {
            var resolved = mc.resolve();
            Optional<TypeDeclaration<?>> decl = index.byQualifiedName(resolved.declaringType().getQualifiedName());
            if (decl.isPresent()) {
                List<MethodDeclaration> ms = index.methods(decl.get(), mc.getNameAsString(), mc.getArguments().size());
                if (!ms.isEmpty()) return ms.get(0);
            }
            return null;
        } catch (RuntimeException ignored) {
        }
        Expression scope = mc.getScope().orElse(null);
        TypeDeclaration<?> targetType = null;
        if (scope == null || scope instanceof ThisExpr) {
            targetType = context;
        } else if (scope instanceof NameExpr n && context != null) {
            targetType = fieldType(context, n.getNameAsString()).orElse(null);
            if (targetType == null) targetType = TypeLookup.find(index, n.getNameAsString(), context).orElse(null);
        } else if (scope != null) {
            targetType = TypeLookup.find(index, scope.toString(), context).orElse(null);
        }
        if (targetType == null) return null;
        List<MethodDeclaration> ms = index.methods(targetType, mc.getNameAsString(), mc.getArguments().size());
        return ms.isEmpty() ? null : ms.get(0);
    }

    private Optional<TypeDeclaration<?>> fieldType(TypeDeclaration<?> type, String fieldName) {
        for (FieldDeclaration f : type.getFields()) {
            for (VariableDeclarator v : f.getVariables()) {
                if (v.getNameAsString().equals(fieldName)) {
                    return TypeLookup.find(index, v.getType().asString(), type);
                }
            }
        }
        return Optional.empty();
    }

    private List<String> fieldNames(TypeDeclaration<?> decl) {
        List<String> names = new ArrayList<>();
        for (FieldDeclaration f : decl.getFields()) {
            if (f.isStatic()) continue;
            f.getVariables().forEach(v -> names.add(v.getNameAsString()));
        }
        return names;
    }

    private static int score(Shape s) {
        if (s == null) return Integer.MIN_VALUE;
        int score = 0;
        if (s.typeQName != null) {
            score += 2;
            String simple = simpleName(s.typeQName);
            if (simple.contains("Error") || simple.contains("Exception") || simple.contains("Fail")) score -= 5;
        }
        if (s.fields != null) score += Math.min(s.fields.size(), 5);
        return score;
    }

    // ------------------------------------------------------------------ assertion emission

    private void emit(Shape shape, String path, TypeSchema schema, Map<String, Condition> out, int depth) {
        if (shape == null || depth > 5) return;
        if (shape.fields != null && !shape.fields.isEmpty()) {
            if (!path.equals("$")) {
                put(out, path, Operator.NOT_NULL, null, null);
            }
            for (Map.Entry<String, Shape> e : shape.fields.entrySet()) {
                String childPath = path + "." + e.getKey();
                TypeSchema childSchema = schemaField(schema, e.getKey());
                emit(e.getValue(), childPath, childSchema, out, depth + 1);
            }
            return;
        }
        if (path.equals("$")) {
            return; // nothing structural discovered
        }
        if (shape.isLiteral) {
            if (shape.literal != null) {
                put(out, path, Operator.EQ, shape.literal, null);
            }
            return;
        }
        String jsonType = shape.jsonTypeHint != null ? shape.jsonTypeHint
                : schema != null ? schema.type : null;
        Operator op = "string".equals(jsonType) ? Operator.NOT_EMPTY : Operator.NOT_NULL;
        String desc = shape.exprText != null ? "<- " + truncate(shape.exprText) : null;
        put(out, path, op, null, desc);
    }

    private TypeSchema schemaField(TypeSchema schema, String name) {
        if (schema == null || schema.fields == null) return null;
        return schema.fields.get(name);
    }

    private void put(Map<String, Condition> out, String path, Operator op, Object expected, String desc) {
        Condition c = new Condition(path, op, expected, "BODY", "response-construction", desc, null);
        out.putIfAbsent(c.key(), c);
    }

    private static String truncate(String s) {
        return s.length() > 100 ? s.substring(0, 100) + "…" : s;
    }

    private static Expression unwrap(Expression e) {
        while (true) {
            if (e instanceof EnclosedExpr en) e = en.getInner();
            else if (e instanceof CastExpr c) e = c.getExpression();
            else return e;
        }
    }

    private static String simpleName(String name) {
        int i = Math.max(name.lastIndexOf('.'), name.lastIndexOf('<'));
        String s = i >= 0 ? name.substring(i + 1) : name;
        return s.replace(">", "");
    }

    private static String hintOf(Expression e) {
        try {
            var rt = e.calculateResolvedType();
            String desc = rt.describe();
            return SchemaBuilder.jsonTypeOf(simpleName(desc.replaceAll("<.*>", "")));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
