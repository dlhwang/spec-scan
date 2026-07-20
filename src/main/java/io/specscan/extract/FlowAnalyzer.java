package io.specscan.extract;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import io.specscan.model.Condition;
import io.specscan.model.Operator;
import io.specscan.parse.AstUtils;
import io.specscan.parse.ProjectIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Walks the call graph from an endpoint handler, tracking request inputs symbolically, and
 * turns guard clauses (if-throw, orElseThrow, Assert/requireNonNull, success-guard returns)
 * into conditions. Conditions on tracked request paths become preconditions; everything
 * else lands in "others" as domain/business rules.
 */
public class FlowAnalyzer {

    public record Result(List<Condition> preConditions, List<Condition> others) {
    }

    private static final int MAX_DEPTH = 8;
    private static final int MAX_CONDITIONS = 200;
    private static final Set<String> PASS_THROUGH = Set.of(
            "stream", "parallelStream", "map", "filter", "collect", "toList", "toSet", "distinct",
            "sorted", "trim", "strip", "toLowerCase", "toUpperCase", "get", "orElse", "orElseGet");

    /** No-arg methods that are measurements, not property accessors. */
    private static final Set<String> NOT_ACCESSORS = Set.of(
            "size", "length", "isEmpty", "isBlank", "toString", "hashCode", "iterator",
            "keySet", "entrySet", "values", "ordinal", "build");

    private final ProjectIndex index;
    private final Map<String, Condition> pre = new LinkedHashMap<>();
    private final Map<String, Condition> others = new LinkedHashMap<>();
    private final Set<String> visited = new HashSet<>();

    public FlowAnalyzer(ProjectIndex index) {
        this.index = index;
    }

    public static Result analyzeEndpoint(ProjectIndex index, MethodDeclaration handler,
                                         Map<String, SymValue> paramBindings) {
        FlowAnalyzer fa = new FlowAnalyzer(index);
        fa.analyzeCallable(handler, paramBindings, null, 0);
        return new Result(new ArrayList<>(fa.pre.values()), new ArrayList<>(fa.others.values()));
    }

    // ------------------------------------------------------------------ traversal

    private void analyzeCallable(CallableDeclaration<?> callable, Map<String, SymValue> params,
                                 SymValue thisVal, int depth) {
        if (depth > MAX_DEPTH || pre.size() + others.size() > MAX_CONDITIONS) return;
        String key = System.identityHashCode(callable) + "|" + fingerprint(params, thisVal);
        if (!visited.add(key)) return;

        Optional<BlockStmt> body = callable instanceof MethodDeclaration m ? m.getBody()
                : Optional.of(((ConstructorDeclaration) callable).getBody());
        if (body.isEmpty()) return;

        Ctx ctx = new Ctx(new HashMap<>(params), thisVal, enclosingType(callable), depth);
        walk(body.get(), ctx);
    }

    private static String fingerprint(Map<String, SymValue> params, SymValue thisVal) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> sb.append(k).append('=').append(v == null ? "-" : v.fingerprint()).append(';'));
        sb.append("this=").append(thisVal == null ? "-" : thisVal.fingerprint());
        return sb.toString();
    }

    private static TypeDeclaration<?> enclosingType(Node n) {
        return n.findAncestor(TypeDeclaration.class).map(t -> (TypeDeclaration<?>) t).orElse(null);
    }

    private final class Ctx {
        final Map<String, SymValue> env;
        final SymValue thisVal;
        final TypeDeclaration<?> type;
        final int depth;
        /** Declared types of local variables — used to resolve calls on untracked locals. */
        final Map<String, TypeDeclaration<?>> varTypes = new HashMap<>();
        /** Variables not directly trackable but derived from a tracked input (taint). */
        final Map<String, SymValue> derived = new HashMap<>();

        Ctx(Map<String, SymValue> env, SymValue thisVal, TypeDeclaration<?> type, int depth) {
            this.env = env;
            this.thisVal = thisVal;
            this.type = type;
            this.depth = depth;
        }
    }

    private void walk(Statement st, Ctx ctx) {
        if (st == null) return;
        switch (st) {
            case BlockStmt b -> b.getStatements().forEach(s -> walk(s, ctx));
            case ExpressionStmt es -> handleExpressionStmt(es.getExpression(), ctx);
            case IfStmt is -> handleIf(is, ctx);
            case ReturnStmt rs -> rs.getExpression().ifPresent(e -> scanExpr(e, ctx));
            case TryStmt ts -> walk(ts.getTryBlock(), ctx);
            case ForEachStmt fe -> {
                scanExpr(fe.getIterable(), ctx);
                SymValue iterable = eval(fe.getIterable(), ctx);
                if (iterable != null && iterable.path != null && !fe.getVariable().getVariables().isEmpty()) {
                    ctx.env.put(fe.getVariable().getVariables().get(0).getNameAsString(),
                            SymValue.path(iterable.path + "[*]", iterable.location, null));
                }
                walk(fe.getBody(), ctx);
            }
            case ForStmt f -> walk(f.getBody(), ctx);
            case WhileStmt w -> walk(w.getBody(), ctx);
            case DoStmt d -> walk(d.getBody(), ctx);
            case SwitchStmt sw -> sw.getEntries().forEach(e -> e.getStatements().forEach(s -> walk(s, ctx)));
            case ExplicitConstructorInvocationStmt eci -> handleExplicitCtor(eci, ctx);
            default -> {
            }
        }
    }

    private void handleExpressionStmt(Expression expr, Ctx ctx) {
        if (expr instanceof VariableDeclarationExpr vde) {
            for (VariableDeclarator v : vde.getVariables()) {
                TypeLookup.find(index, v.getType().asString(), ctx.type)
                        .ifPresent(t -> ctx.varTypes.put(v.getNameAsString(), t));
                v.getInitializer().ifPresent(init -> {
                    scanExpr(init, ctx);
                    SymValue val = eval(init, ctx);
                    if (val != null) {
                        ctx.env.put(v.getNameAsString(), val);
                    } else {
                        SymValue taint = firstTracked(init, ctx);
                        if (taint != null) ctx.derived.put(v.getNameAsString(), taint);
                    }
                });
            }
            return;
        }
        if (expr instanceof AssignExpr ae) {
            scanExpr(ae.getValue(), ctx);
            if (ae.getTarget() instanceof NameExpr n) {
                SymValue val = eval(ae.getValue(), ctx);
                if (val != null) ctx.env.put(n.getNameAsString(), val);
            }
            return;
        }
        scanExpr(expr, ctx);
    }

    /** Scans an expression tree for guard patterns and project-call recursion. */
    private void scanExpr(Expression expr, Ctx ctx) {
        expr.walk(MethodCallExpr.class, call -> {
            if (insideLambdaOf(call, expr)) return; // lambda bodies handled by orElseThrow logic only
            String name = call.getNameAsString();
            switch (name) {
                case "orElseThrow" -> handleOrElseThrow(call, ctx);
                case "requireNonNull" -> handleRequireNonNull(call, ctx);
                default -> {
                    if (isScopeName(call, "Assert")) handleAssert(call, ctx);
                    else if (isScopeName(call, "Preconditions")) handlePreconditions(call, ctx);
                    else maybeRecurseCall(call, ctx);
                }
            }
        });
        expr.walk(ObjectCreationExpr.class, creation -> {
            if (!insideLambdaOf(creation, expr)) maybeRecurseCtor(creation, ctx);
        });
    }

    private static boolean insideLambdaOf(Node node, Expression root) {
        Node cur = node.getParentNode().orElse(null);
        while (cur != null && cur != root) {
            if (cur instanceof LambdaExpr) return true;
            cur = cur.getParentNode().orElse(null);
        }
        return false;
    }

    private static boolean isScopeName(MethodCallExpr call, String name) {
        return call.getScope().map(s -> s instanceof NameExpr n && n.getNameAsString().equals(name)).orElse(false);
    }

    // ------------------------------------------------------------------ guard patterns

    private void handleIf(IfStmt is, Ctx ctx) {
        Statement thenSt = is.getThenStmt();
        Statement elseSt = is.getElseStmt().orElse(null);
        boolean thenThrows = endsWithThrow(thenSt);
        boolean elseThrows = elseSt != null && endsWithThrow(elseSt);

        scanExpr(is.getCondition(), ctx);

        // error-accumulating validator: if (cond) errors.add(ValidationError.of("field","code"))
        if (!thenThrows && isErrorAccumulation(thenSt)) {
            emitBool(is.getCondition(), true, ctx, "validation-error",
                    errorDescription(thenSt), AstUtils.at(is));
            if (elseSt != null) walk(elseSt, ctx);
            return;
        }

        if (thenThrows) {
            String desc = throwDescription(thenSt);
            emitBool(is.getCondition(), true, ctx, "if-throw", desc, AstUtils.at(is));
            if (elseSt != null) walk(elseSt, ctx);
            return;
        }
        if (elseThrows) {
            String desc = throwDescription(elseSt);
            emitBool(is.getCondition(), false, ctx, "if-throw", desc, AstUtils.at(is));
            walk(thenSt, ctx);
            return;
        }
        // success-guard: if (cond) { ... return ok; } ... return fallback;
        if (endsWithReturn(thenSt)) {
            List<RawCond> conds = boolCond(is.getCondition(), false, ctx);
            for (RawCond rc : conds) {
                emit(rc, ctx, "if-return", "정상 응답 경로로 진입하기 위한 조건", AstUtils.at(is), true);
            }
        }
        walk(thenSt, ctx);
        if (elseSt != null) walk(elseSt, ctx);
    }

    /** Does the branch only record a validation error (errors.add / bindingResult.reject*)? */
    private static boolean isErrorAccumulation(Statement st) {
        List<Statement> stmts = st instanceof BlockStmt b ? b.getStatements() : List.of(st);
        if (stmts.isEmpty()) return false;
        for (Statement s : stmts) {
            if (!(s instanceof ExpressionStmt es) || !(es.getExpression() instanceof MethodCallExpr mc)) {
                return false;
            }
            String name = mc.getNameAsString();
            boolean rejecting = name.equals("reject") || name.equals("rejectValue") || name.equals("addError");
            boolean addingToErrors = name.equals("add") && mc.getScope()
                    .map(sc -> sc.toString().toLowerCase().contains("error")).orElse(false);
            if (!rejecting && !addingToErrors) return false;
        }
        return true;
    }

    private static String errorDescription(Statement st) {
        List<String> literals = new ArrayList<>();
        st.walk(StringLiteralExpr.class, s -> literals.add(s.asString()));
        return literals.isEmpty() ? "validation error" : String.join(": ", literals);
    }

    private static boolean endsWithThrow(Statement st) {
        if (st instanceof ThrowStmt) return true;
        if (st instanceof BlockStmt b && !b.getStatements().isEmpty()) {
            return b.getStatements().get(b.getStatements().size() - 1) instanceof ThrowStmt;
        }
        return false;
    }

    private static boolean endsWithReturn(Statement st) {
        if (st instanceof ReturnStmt) return true;
        if (st instanceof BlockStmt b && !b.getStatements().isEmpty()) {
            return b.getStatements().get(b.getStatements().size() - 1) instanceof ReturnStmt;
        }
        return false;
    }

    private static String throwDescription(Statement st) {
        ThrowStmt ts = null;
        if (st instanceof ThrowStmt t) ts = t;
        else if (st instanceof BlockStmt b) {
            for (Statement s : b.getStatements()) {
                if (s instanceof ThrowStmt t) ts = t;
            }
        }
        if (ts == null) return null;
        if (ts.getExpression() instanceof ObjectCreationExpr oce) {
            String ex = oce.getType().getName().getIdentifier();
            String msg = oce.getArguments().stream()
                    .filter(a -> a instanceof StringLiteralExpr)
                    .map(a -> ((StringLiteralExpr) a).asString())
                    .findFirst().orElse(null);
            return msg != null ? ex + ": " + msg : ex;
        }
        return ts.getExpression().toString();
    }

    private void handleOrElseThrow(MethodCallExpr call, Ctx ctx) {
        // find a tracked argument anywhere in the lookup chain, following local variables
        // and unwrapping constructor arguments: repo.findById(new OrderNo($.orderNo)).orElseThrow(...)
        SymValue tracked = null;
        String chainText = null;
        Expression scope = call.getScope().orElse(null);
        int hops = 0;
        while (scope != null && hops++ < 10) {
            if (scope instanceof MethodCallExpr mc) {
                for (Expression arg : mc.getArguments()) {
                    SymValue v = trackedIn(arg, ctx);
                    if (v != null) {
                        tracked = v;
                        chainText = mc.getNameAsString();
                        break;
                    }
                }
                if (tracked != null) break;
                scope = mc.getScope().orElse(null);
            } else if (scope instanceof NameExpr n) {
                SymValue v = ctx.env.get(n.getNameAsString());
                if (v == null) v = ctx.derived.get(n.getNameAsString());
                if (v != null && v.firstPathValue() != null) {
                    tracked = v.firstPathValue();
                    chainText = n.getNameAsString();
                    break;
                }
                // follow the variable's initializer expression
                scope = initializerOf(call, n.getNameAsString());
            } else {
                break;
            }
        }
        String exMsg = orElseThrowMessage(call);
        String desc = (chainText != null ? chainText + " 결과가 존재해야 함" : "조회 결과가 존재해야 함")
                + (exMsg != null ? " — " + exMsg : "");
        if (tracked != null) {
            emit(new RawCond(tracked, null, Operator.EXISTS, null), ctx, "or-else-throw", desc, AstUtils.at(call), false);
        } else {
            String text = call.getScope().map(Object::toString).orElse(call.toString());
            emit(new RawCond(null, text, Operator.EXISTS, null), ctx, "or-else-throw", desc, AstUtils.at(call), true);
        }
    }

    /** Tracked path in an argument: direct, nested in a wrapper object, or by taint. */
    private SymValue trackedIn(Expression e, Ctx ctx) {
        SymValue v = eval(e, ctx);
        if (v != null) {
            SymValue p = v.firstPathValue();
            if (p != null) return p;
        }
        return firstTracked(e, ctx);
    }

    /** Initializer expression of a local variable declared in the enclosing method. */
    private static Expression initializerOf(Node at, String varName) {
        return at.findAncestor(MethodDeclaration.class)
                .flatMap(md -> md.findAll(VariableDeclarator.class).stream()
                        .filter(vd -> vd.getNameAsString().equals(varName))
                        .findFirst())
                .flatMap(VariableDeclarator::getInitializer)
                .orElse(null);
    }

    private static String orElseThrowMessage(MethodCallExpr call) {
        if (call.getArguments().isEmpty()) return null;
        if (call.getArgument(0) instanceof LambdaExpr le) {
            var oce = le.getBody().findFirst(ObjectCreationExpr.class).orElse(null);
            if (oce != null) {
                String ex = oce.getType().getName().getIdentifier();
                String msg = oce.getArguments().stream()
                        .filter(a -> a instanceof StringLiteralExpr)
                        .map(a -> ((StringLiteralExpr) a).asString())
                        .findFirst().orElse(null);
                return msg != null ? ex + ": " + msg : ex;
            }
        }
        return null;
    }

    private void handleRequireNonNull(MethodCallExpr call, Ctx ctx) {
        if (call.getArguments().isEmpty()) return;
        SymValue v = eval(call.getArgument(0), ctx);
        String msg = call.getArguments().size() > 1 && call.getArgument(1) instanceof StringLiteralExpr s
                ? s.asString() : null;
        if (v != null && v.path != null) {
            emit(new RawCond(v, null, Operator.NOT_NULL, null), ctx, "assert", msg, AstUtils.at(call), false);
        }
    }

    private void handleAssert(MethodCallExpr call, Ctx ctx) {
        if (call.getArguments().isEmpty()) return;
        String msg = call.getArguments().stream().filter(a -> a instanceof StringLiteralExpr)
                .map(a -> ((StringLiteralExpr) a).asString()).findFirst().orElse(null);
        String at = AstUtils.at(call);
        Expression arg0 = call.getArgument(0);
        switch (call.getNameAsString()) {
            case "notNull" -> emitOn(arg0, Operator.NOT_NULL, ctx, msg, at);
            case "hasText" -> emitOn(arg0, Operator.NOT_BLANK, ctx, msg, at);
            case "hasLength", "notEmpty" -> emitOn(arg0, Operator.NOT_EMPTY, ctx, msg, at);
            case "isTrue", "state" -> emitBool(arg0, false, ctx, "assert", msg, at);
            default -> {
            }
        }
    }

    private void handlePreconditions(MethodCallExpr call, Ctx ctx) {
        if (call.getArguments().isEmpty()) return;
        String msg = call.getArguments().stream().skip(1).filter(a -> a instanceof StringLiteralExpr)
                .map(a -> ((StringLiteralExpr) a).asString()).findFirst().orElse(null);
        String at = AstUtils.at(call);
        switch (call.getNameAsString()) {
            case "checkNotNull" -> emitOn(call.getArgument(0), Operator.NOT_NULL, ctx, msg, at);
            case "checkArgument", "checkState" -> emitBool(call.getArgument(0), false, ctx, "assert", msg, at);
            default -> {
            }
        }
    }

    private void emitOn(Expression target, Operator op, Ctx ctx, String msg, String at) {
        SymValue v = eval(target, ctx);
        if (v != null && v.path != null) {
            emit(new RawCond(v, null, op, null), ctx, "assert", msg, at, false);
        } else {
            emit(new RawCond(null, target.toString(), op, null), ctx, "assert", msg, at, true);
        }
    }

    // ------------------------------------------------------------------ boolean conditions

    /** Intermediate condition before classification. */
    private record RawCond(SymValue target, String exprText, Operator op, Object expected) {
    }

    private void emitBool(Expression cond, boolean negate, Ctx ctx, String source, String desc, String at) {
        for (RawCond rc : boolCond(cond, negate, ctx)) {
            emit(rc, ctx, source, desc, at, false);
        }
    }

    /**
     * Translates a boolean expression into conditions. When {@code negate} is true the
     * requirement is the negation of the expression (the expression guards a throw).
     */
    private List<RawCond> boolCond(Expression expr, boolean negate, Ctx ctx) {
        List<RawCond> out = new ArrayList<>();
        expr = unwrap(expr);

        if (expr instanceof UnaryExpr u && u.getOperator() == UnaryExpr.Operator.LOGICAL_COMPLEMENT) {
            return boolCond(u.getExpression(), !negate, ctx);
        }
        if (expr instanceof BinaryExpr be) {
            BinaryExpr.Operator op = be.getOperator();
            if (op == BinaryExpr.Operator.OR && negate) {
                out.addAll(boolCond(be.getLeft(), true, ctx));
                out.addAll(boolCond(be.getRight(), true, ctx));
                return out;
            }
            if (op == BinaryExpr.Operator.AND && !negate) {
                out.addAll(boolCond(be.getLeft(), false, ctx));
                out.addAll(boolCond(be.getRight(), false, ctx));
                return out;
            }
            if (op == BinaryExpr.Operator.AND && negate) {
                // `if (x != null && cond) throw` — the null check is an applicability guard,
                // the real requirement is the negation of cond (when x is present)
                if (isPresenceGuard(be.getLeft())) return boolCond(be.getRight(), true, ctx);
                if (isPresenceGuard(be.getRight())) return boolCond(be.getLeft(), true, ctx);
            }
            if (op == BinaryExpr.Operator.AND || op == BinaryExpr.Operator.OR) {
                out.add(rawExpr(expr, negate, ctx));
                return out;
            }
            RawCond cmp = comparison(be, negate, ctx);
            if (cmp != null) {
                out.add(cmp);
                return out;
            }
            out.add(rawExpr(expr, negate, ctx));
            return out;
        }
        if (expr instanceof MethodCallExpr mc) {
            RawCond rc = methodCondition(mc, negate, ctx);
            out.add(rc != null ? rc : rawExpr(expr, negate, ctx));
            return out;
        }
        if (expr instanceof NameExpr) {
            SymValue v = eval(expr, ctx);
            if (v != null && v.path != null) {
                out.add(new RawCond(v, null, negate ? Operator.FALSE : Operator.TRUE, null));
                return out;
            }
        }
        out.add(rawExpr(expr, negate, ctx));
        return out;
    }

    private RawCond rawExpr(Expression expr, boolean negate, Ctx ctx) {
        String text = (negate ? "!(" : "(") + expr.toString() + ")";
        SymValue tracked = firstTracked(expr, ctx);
        return new RawCond(tracked, text, Operator.EXPR, text);
    }

    private SymValue firstTracked(Expression expr, Ctx ctx) {
        final SymValue[] found = {null};
        expr.walk(e -> {
            if (found[0] != null || !(e instanceof Expression sub)) return;
            if (sub instanceof NameExpr || sub instanceof MethodCallExpr || sub instanceof FieldAccessExpr) {
                SymValue v = eval(sub, ctx);
                if (v == null && sub instanceof NameExpr n) v = ctx.derived.get(n.getNameAsString());
                if (v != null && v.path != null) found[0] = v;
            }
        });
        return found[0];
    }

    private static boolean isPresenceGuard(Expression e) {
        e = unwrap(e);
        if (e instanceof BinaryExpr be && be.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
            return be.getLeft() instanceof com.github.javaparser.ast.expr.NullLiteralExpr
                    || be.getRight() instanceof com.github.javaparser.ast.expr.NullLiteralExpr;
        }
        if (e instanceof MethodCallExpr mc) {
            return mc.getNameAsString().equals("nonNull") || mc.getNameAsString().equals("isPresent");
        }
        return false;
    }

    private RawCond comparison(BinaryExpr be, boolean negate, Ctx ctx) {
        Expression left = unwrap(be.getLeft());
        Expression right = unwrap(be.getRight());
        BinaryExpr.Operator op = be.getOperator();

        // a.compareTo(b) OP 0  — cross-field comparison
        RawCond viaCompareTo = compareToCond(left, right, op, negate, ctx);
        if (viaCompareTo != null) return viaCompareTo;

        // normalize: tracked/measured side on the left
        Side ls = side(left, ctx);
        Side rs = side(right, ctx);
        if (ls.value == null && rs.value != null) {
            Side tmp = ls;
            ls = rs;
            rs = tmp;
            op = flip(op);
        }
        if (ls.value == null) return null;

        // x == null / x != null
        if (rs.isNull) {
            boolean eq = op == BinaryExpr.Operator.EQUALS;
            boolean requireNull = eq != negate;
            return new RawCond(ls.value, null, requireNull ? Operator.NULL : Operator.NOT_NULL, null);
        }
        if (!rs.isLiteral) return null;

        Operator cond = switch (op) {
            case LESS -> negate ? Operator.GOE : Operator.LT;
            case LESS_EQUALS -> negate ? Operator.GT : Operator.LOE;
            case GREATER -> negate ? Operator.LOE : Operator.GT;
            case GREATER_EQUALS -> negate ? Operator.LT : Operator.GOE;
            case EQUALS -> negate ? Operator.NE : Operator.EQ;
            case NOT_EQUALS -> negate ? Operator.EQ : Operator.NE;
            default -> null;
        };
        if (cond == null) return null;
        return new RawCond(ls.value, null, cond, rs.literal);
    }

    private RawCond compareToCond(Expression left, Expression right, BinaryExpr.Operator op,
                                  boolean negate, Ctx ctx) {
        if (!(left instanceof MethodCallExpr mc) || !mc.getNameAsString().equals("compareTo")
                || mc.getArguments().size() != 1 || mc.getScope().isEmpty()) {
            return null;
        }
        Object zero = AstUtils.isLiteral(right) ? AstUtils.literalValue(right) : null;
        if (!(zero instanceof Number n) || n.intValue() != 0) return null;
        SymValue base = eval(mc.getScope().get(), ctx);
        if (base == null || base.path == null) return null;
        SymValue other = eval(mc.getArgument(0), ctx);
        Object expected = other != null && other.path != null ? other.path : mc.getArgument(0).toString();
        Operator cond = switch (op) {
            case LESS -> negate ? Operator.GOE : Operator.LT;
            case LESS_EQUALS -> negate ? Operator.GT : Operator.LOE;
            case GREATER -> negate ? Operator.LOE : Operator.GT;
            case GREATER_EQUALS -> negate ? Operator.LT : Operator.GOE;
            case EQUALS -> negate ? Operator.NE : Operator.EQ;
            case NOT_EQUALS -> negate ? Operator.EQ : Operator.NE;
            default -> null;
        };
        return cond == null ? null : new RawCond(base, null, cond, expected);
    }

    private record Side(SymValue value, boolean isLiteral, Object literal, boolean isNull) {
    }

    /** Classifies one side of a comparison: tracked value (unwrapping length()/size()) or literal. */
    private Side side(Expression e, Ctx ctx) {
        e = unwrap(e);
        if (e instanceof com.github.javaparser.ast.expr.NullLiteralExpr) {
            return new Side(null, true, null, true);
        }
        if (AstUtils.isLiteral(e)) {
            return new Side(null, true, AstUtils.literalValue(e), false);
        }
        if (e instanceof MethodCallExpr mc
                && (mc.getNameAsString().equals("length") || mc.getNameAsString().equals("size"))
                && mc.getArguments().isEmpty() && mc.getScope().isPresent()) {
            SymValue v = eval(mc.getScope().get(), ctx);
            if (v != null && v.path != null) return new Side(v, false, null, false);
        }
        SymValue v = eval(e, ctx);
        if (v != null && v.path != null) return new Side(v, false, null, false);
        return new Side(null, false, null, false);
    }

    private static BinaryExpr.Operator flip(BinaryExpr.Operator op) {
        return switch (op) {
            case LESS -> BinaryExpr.Operator.GREATER;
            case LESS_EQUALS -> BinaryExpr.Operator.GREATER_EQUALS;
            case GREATER -> BinaryExpr.Operator.LESS;
            case GREATER_EQUALS -> BinaryExpr.Operator.LESS_EQUALS;
            default -> op;
        };
    }

    /** isEmpty/isBlank/equals/matches/ObjectUtils.isEmpty/StringUtils.hasText patterns. */
    private RawCond methodCondition(MethodCallExpr mc, boolean negate, Ctx ctx) {
        String name = mc.getNameAsString();
        Expression scope = mc.getScope().orElse(null);

        // static utility forms
        if (scope instanceof NameExpr n) {
            String cls = n.getNameAsString();
            boolean utils = cls.equals("ObjectUtils") || cls.equals("CollectionUtils") || cls.equals("StringUtils")
                    || cls.equals("Objects");
            if (utils && !mc.getArguments().isEmpty()) {
                SymValue v = eval(mc.getArgument(0), ctx);
                if (v == null || v.path == null) return null;
                return switch (name) {
                    case "isEmpty" -> new RawCond(v, null, negate ? Operator.NOT_EMPTY : Operator.EMPTY, null);
                    case "isBlank" -> new RawCond(v, null, negate ? Operator.NOT_BLANK : Operator.BLANK, null);
                    case "isNotBlank", "hasText" -> new RawCond(v, null, negate ? Operator.BLANK : Operator.NOT_BLANK, null);
                    case "isNotEmpty", "hasLength" -> new RawCond(v, null, negate ? Operator.EMPTY : Operator.NOT_EMPTY, null);
                    case "isNull" -> new RawCond(v, null, negate ? Operator.NOT_NULL : Operator.NULL, null);
                    case "nonNull" -> new RawCond(v, null, negate ? Operator.NULL : Operator.NOT_NULL, null);
                    case "equals" -> equalsCond(v, mc.getArguments().size() > 1 ? mc.getArgument(1) : null, negate);
                    default -> null;
                };
            }
        }
        if (scope == null) return null;
        SymValue scopeVal = eval(scope, ctx);

        switch (name) {
            case "isEmpty" -> {
                if (scopeVal != null && scopeVal.path != null) {
                    return new RawCond(scopeVal, null, negate ? Operator.NOT_EMPTY : Operator.EMPTY, null);
                }
            }
            case "isBlank" -> {
                if (scopeVal != null && scopeVal.path != null) {
                    return new RawCond(scopeVal, null, negate ? Operator.NOT_BLANK : Operator.BLANK, null);
                }
            }
            case "equals", "equalsIgnoreCase" -> {
                if (mc.getArguments().size() != 1) return null;
                Expression arg = mc.getArgument(0);
                if (scopeVal != null && scopeVal.path != null && AstUtils.isLiteral(arg)) {
                    return new RawCond(scopeVal, null, negate ? Operator.NE : Operator.EQ, AstUtils.literalValue(arg));
                }
                SymValue argVal = eval(arg, ctx);
                if (argVal != null && argVal.path != null && AstUtils.isLiteral(scope)) {
                    return new RawCond(argVal, null, negate ? Operator.NE : Operator.EQ, AstUtils.literalValue(scope));
                }
            }
            case "matches" -> {
                if (mc.getArguments().size() == 1 && scopeVal != null && scopeVal.path != null
                        && mc.getArgument(0) instanceof StringLiteralExpr regex && !negate) {
                    return new RawCond(scopeVal, null, Operator.MATCHES, regex.asString());
                }
                if (mc.getArguments().size() == 2 && !negate) {
                    SymValue a0 = eval(mc.getArgument(0), ctx);
                    if (a0 != null && a0.path != null) {
                        return new RawCond(a0, null, Operator.MATCHES, mc.getArgument(1).toString());
                    }
                }
            }
            case "contains" -> {
                if (mc.getArguments().size() == 1 && scopeVal != null && scopeVal.path != null
                        && AstUtils.isLiteral(mc.getArgument(0)) && !negate) {
                    return new RawCond(scopeVal, null, Operator.IN, AstUtils.literalValue(mc.getArgument(0)));
                }
            }
            default -> {
            }
        }
        return null;
    }

    private static RawCond equalsCond(SymValue v, Expression other, boolean negate) {
        if (other == null || !AstUtils.isLiteral(other)) return null;
        return new RawCond(v, null, negate ? Operator.NE : Operator.EQ, AstUtils.literalValue(other));
    }

    // ------------------------------------------------------------------ emission

    private void emit(RawCond rc, Ctx ctx, String source, String desc, String at, boolean forceOthers) {
        if (rc == null) return;
        boolean tracked = rc.target != null && rc.target.path != null;
        String path = tracked ? rc.target.path : (rc.exprText != null ? rc.exprText : "<unknown>");
        String location = tracked ? rc.target.location : "DOMAIN";
        Condition c = new Condition(path, rc.op, rc.expected, location, source, desc, at);
        boolean isPre = tracked && !forceOthers && !"if-return".equals(source) && rc.op != Operator.EXPR;
        Map<String, Condition> sink = isPre ? pre : others;
        sink.putIfAbsent(c.key(), c);
    }

    // ------------------------------------------------------------------ symbolic evaluation

    SymValue eval(Expression e, Ctx ctx) {
        return eval(e, ctx, 0);
    }

    private SymValue eval(Expression e, Ctx ctx, int guard) {
        if (e == null || guard > 12) return null;
        e = unwrap(e);

        if (e instanceof NameExpr n) {
            SymValue v = ctx.env.get(n.getNameAsString());
            if (v != null) return v;
            if (ctx.thisVal != null && hasField(ctx.type, n.getNameAsString())) {
                return ctx.thisVal.member(n.getNameAsString());
            }
            return null;
        }
        if (e instanceof ThisExpr) return ctx.thisVal;
        if (e instanceof FieldAccessExpr fa) {
            SymValue scope = eval(fa.getScope(), ctx, guard + 1);
            return scope != null ? scope.member(fa.getNameAsString()) : null;
        }
        if (e instanceof ConditionalExpr c) {
            SymValue v = eval(c.getThenExpr(), ctx, guard + 1);
            return v != null ? v : eval(c.getElseExpr(), ctx, guard + 1);
        }
        if (e instanceof ObjectCreationExpr oce) {
            return evalCreation(oce, ctx, guard);
        }
        if (e instanceof MethodCallExpr mc) {
            return evalCall(mc, ctx, guard);
        }
        return null;
    }

    private SymValue evalCall(MethodCallExpr mc, Ctx ctx, int guard) {
        String name = mc.getNameAsString();
        Expression scopeExpr = mc.getScope().orElse(null);
        SymValue scopeVal = scopeExpr != null ? eval(scopeExpr, ctx, guard + 1) : null;

        // indexed element access: list.get(i) -> $.list[*]
        if (name.equals("get") && mc.getArguments().size() == 1 && scopeVal != null && scopeVal.path != null) {
            return SymValue.path(scopeVal.path + "[*]", scopeVal.location, null);
        }
        if (scopeVal != null && PASS_THROUGH.contains(name)) {
            return scopeVal;
        }
        // project method whose return value we can evaluate (DTO -> domain mappers etc.)
        // — must run before the accessor heuristic so save.to() is not read as $.to
        if (ctx.depth < MAX_DEPTH) {
            Optional<CallTarget> target = resolveProjectMethod(mc, ctx);
            if (target.isPresent()) {
                Map<String, SymValue> bindings = bindArgs(target.get().method.getParameters(), mc.getArguments(), ctx);
                boolean anyTracked = bindings.values().stream().anyMatch(v -> v != null && v.isTracked())
                        || (scopeVal != null && scopeVal.isTracked());
                if (anyTracked) {
                    SymValue ret = evalReturnOf(target.get().method, bindings, scopeVal, ctx.depth + 1);
                    if (ret != null) return ret;
                }
                return null;
            }
        }
        // accessor on a tracked value: loginDTO.getUsername(), record.username()
        if (scopeVal != null && mc.getArguments().isEmpty() && !NOT_ACCESSORS.contains(name)) {
            Optional<String> prop = AstUtils.propertyOfAccessor(name, 0);
            if (prop.isPresent()) {
                SymValue member = scopeVal.member(prop.get());
                if (member != null) return member;
            }
        }
        return null;
    }

    private SymValue evalCreation(ObjectCreationExpr oce, Ctx ctx, int guard) {
        Optional<TypeDeclaration<?>> decl = findType(typeText(oce), ctx);
        if (decl.isEmpty()) return null;
        String qname = decl.get().getFullyQualifiedName().orElse(null);

        List<SymValue> args = new ArrayList<>();
        for (Expression a : oce.getArguments()) args.add(eval(a, ctx, guard + 1));

        // explicit constructor: map via `this.f = param` assignments
        Optional<ConstructorDeclaration> ctor = findCtor(decl.get(), oce.getArguments().size());
        Map<String, SymValue> fields = SymValue.orderedMap();
        if (ctor.isPresent()) {
            List<Parameter> params = ctor.get().getParameters();
            Map<String, SymValue> paramVals = new HashMap<>();
            for (int i = 0; i < params.size() && i < args.size(); i++) {
                paramVals.put(params.get(i).getNameAsString(), args.get(i));
            }
            ctor.get().getBody().findAll(AssignExpr.class).forEach(ae -> {
                if (ae.getTarget() instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr
                        && ae.getValue() instanceof NameExpr val) {
                    SymValue v = paramVals.get(val.getNameAsString());
                    if (v != null) fields.put(fa.getNameAsString(), v);
                }
            });
            if (fields.isEmpty()) {
                positionalFields(decl.get(), args, fields);
            }
        } else {
            // Lombok @AllArgsConstructor et al: positional against declared fields
            positionalFields(decl.get(), args, fields);
        }
        return SymValue.object(fields, qname);
    }

    private void positionalFields(TypeDeclaration<?> decl, List<SymValue> args, Map<String, SymValue> fields) {
        List<String> names = new ArrayList<>();
        for (FieldDeclaration f : decl.getFields()) {
            if (f.isStatic()) continue;
            f.getVariables().forEach(v -> names.add(v.getNameAsString()));
        }
        if (names.size() == args.size()) {
            for (int i = 0; i < names.size(); i++) {
                if (args.get(i) != null) fields.put(names.get(i), args.get(i));
            }
        }
    }

    /** Evaluates the return value of a project method under the given bindings. */
    private SymValue evalReturnOf(MethodDeclaration m, Map<String, SymValue> bindings, SymValue thisVal, int depth) {
        if (depth > MAX_DEPTH || m.getBody().isEmpty()) return null;
        Ctx ctx = new Ctx(new HashMap<>(bindings), thisVal, enclosingType(m), depth);
        for (ReturnStmt rs : m.getBody().get().findAll(ReturnStmt.class)) {
            if (rs.getExpression().isEmpty()) continue;
            SymValue v = eval(rs.getExpression().get(), ctx, 0);
            if (v != null) return v;
        }
        return null;
    }

    // ------------------------------------------------------------------ call resolution & recursion

    private record CallTarget(TypeDeclaration<?> type, MethodDeclaration method) {
    }

    private void maybeRecurseCall(MethodCallExpr call, Ctx ctx) {
        if (ctx.depth >= MAX_DEPTH) return;
        Optional<CallTarget> target = resolveProjectMethod(call, ctx);
        if (target.isEmpty()) return;

        Map<String, SymValue> bindings = bindArgs(target.get().method.getParameters(), call.getArguments(), ctx);
        SymValue thisVal = call.getScope().map(s -> eval(s, ctx)).orElse(ctx.thisVal);
        boolean anyTracked = bindings.values().stream().anyMatch(v -> v != null && v.isTracked())
                || (thisVal != null && thisVal.isTracked());
        // shallow layers (controller -> service -> domain) are followed even without tracked
        // inputs so that pure domain-state rules still surface in "others"
        if (anyTracked || ctx.depth <= 2) {
            analyzeCallable(target.get().method, bindings, thisVal, ctx.depth + 1);
        }
    }

    /** Follows super(...) / this(...) constructor delegation. */
    private void handleExplicitCtor(ExplicitConstructorInvocationStmt eci, Ctx ctx) {
        if (ctx.depth >= MAX_DEPTH || ctx.type == null) return;
        eci.getArguments().forEach(a -> scanExpr(a, ctx));
        TypeDeclaration<?> targetType = eci.isThis() ? ctx.type : index.superclassOf(ctx.type).orElse(null);
        if (targetType == null) return;
        Optional<ConstructorDeclaration> ctor = findCtor(targetType, eci.getArguments().size());
        if (ctor.isEmpty()) return;
        Map<String, SymValue> bindings = bindArgs(ctor.get().getParameters(), eci.getArguments(), ctx);
        if (bindings.values().stream().anyMatch(v -> v != null && v.isTracked())) {
            analyzeCallable(ctor.get(), bindings, ctx.thisVal, ctx.depth + 1);
        }
    }

    private void maybeRecurseCtor(ObjectCreationExpr creation, Ctx ctx) {
        if (ctx.depth >= MAX_DEPTH) return;
        Optional<TypeDeclaration<?>> decl = findType(typeText(creation), ctx);
        if (decl.isEmpty()) return;
        Optional<ConstructorDeclaration> ctor = findCtor(decl.get(), creation.getArguments().size());
        if (ctor.isEmpty()) return;
        Map<String, SymValue> bindings = bindArgs(ctor.get().getParameters(), creation.getArguments(), ctx);
        if (bindings.values().stream().anyMatch(v -> v != null && v.isTracked())) {
            analyzeCallable(ctor.get(), bindings, null, ctx.depth + 1);
        }
    }

    private Map<String, SymValue> bindArgs(List<Parameter> params, List<Expression> args, Ctx ctx) {
        Map<String, SymValue> bindings = new HashMap<>();
        for (int i = 0; i < params.size() && i < args.size(); i++) {
            SymValue v = eval(args.get(i), ctx);
            if (v != null) bindings.put(params.get(i).getNameAsString(), v);
        }
        return bindings;
    }

    private Optional<CallTarget> resolveProjectMethod(MethodCallExpr call, Ctx ctx) {
        // 1. symbol solver
        try {
            var resolved = call.resolve();
            String qname = resolved.declaringType().getQualifiedName();
            Optional<TypeDeclaration<?>> decl = index.byQualifiedName(qname);
            if (decl.isPresent()) {
                List<MethodDeclaration> ms = index.methods(decl.get(), call.getNameAsString(), call.getArguments().size());
                if (!ms.isEmpty()) return Optional.of(new CallTarget(decl.get(), ms.get(0)));
            }
            return Optional.empty();
        } catch (RuntimeException ignored) {
            // fall through to heuristics
        }

        Expression scope = call.getScope().orElse(null);
        TypeDeclaration<?> targetType = null;
        if (scope == null || scope instanceof ThisExpr) {
            targetType = ctx.type;
        } else {
            SymValue scopeVal = eval(scope, ctx);
            if (scopeVal != null && scopeVal.typeQName != null) {
                targetType = index.byQualifiedName(scopeVal.typeQName).orElse(null);
            }
            if (targetType == null && scope instanceof NameExpr n) {
                targetType = ctx.varTypes.get(n.getNameAsString());
                if (targetType == null) {
                    targetType = fieldTypeOf(ctx.type, n.getNameAsString())
                            .or(() -> findType(n.getNameAsString(), ctx))
                            .orElse(null);
                }
            }
            if (targetType == null) {
                // dotted type reference: PropertyResponse.PropertyVO.to(...) or FQN
                targetType = findType(scope.toString(), ctx).orElse(null);
            }
        }
        if (targetType == null) return Optional.empty();
        TypeDeclaration<?> cur = targetType;
        while (cur != null) {
            List<MethodDeclaration> ms = index.methods(cur, call.getNameAsString(), call.getArguments().size());
            if (!ms.isEmpty()) return Optional.of(new CallTarget(cur, ms.get(0)));
            cur = index.superclassOf(cur).orElse(null);
        }
        return Optional.empty();
    }

    /** Declared type of an instance field, resolved to a project type when possible. */
    private Optional<TypeDeclaration<?>> fieldTypeOf(TypeDeclaration<?> type, String fieldName) {
        TypeDeclaration<?> cur = type;
        while (cur != null) {
            for (FieldDeclaration f : cur.getFields()) {
                for (VariableDeclarator v : f.getVariables()) {
                    if (v.getNameAsString().equals(fieldName)) {
                        try {
                            var rt = v.getType().resolve();
                            if (rt.isReferenceType()) {
                                var found = index.byQualifiedName(rt.asReferenceType().getQualifiedName());
                                if (found.isPresent()) return found;
                            }
                        } catch (RuntimeException ignored) {
                        }
                        return TypeLookup.find(index, v.getType().asString(), type);
                    }
                }
            }
            cur = index.superclassOf(cur).orElse(null);
        }
        return Optional.empty();
    }

    private boolean hasField(TypeDeclaration<?> type, String name) {
        TypeDeclaration<?> cur = type;
        while (cur != null) {
            for (FieldDeclaration f : cur.getFields()) {
                for (VariableDeclarator v : f.getVariables()) {
                    if (v.getNameAsString().equals(name)) return true;
                }
            }
            cur = index.superclassOf(cur).orElse(null);
        }
        return false;
    }

    /** Finds a project type by reference text, without scoping context. */
    Optional<TypeDeclaration<?>> findType(String text) {
        return TypeLookup.find(index, text, null);
    }

    private Optional<TypeDeclaration<?>> findType(String text, Ctx ctx) {
        return TypeLookup.find(index, text, ctx != null ? ctx.type : null);
    }

    private Optional<ConstructorDeclaration> findCtor(TypeDeclaration<?> decl, int arity) {
        if (!(decl instanceof ClassOrInterfaceDeclaration cid)) return Optional.empty();
        return cid.getConstructors().stream().filter(c -> c.getParameters().size() == arity).findFirst();
    }

    private static String typeText(ObjectCreationExpr oce) {
        return oce.getType().getNameWithScope();
    }

    private static Expression unwrap(Expression e) {
        while (true) {
            if (e instanceof EnclosedExpr en) e = en.getInner();
            else if (e instanceof CastExpr c) e = c.getExpression();
            else return e;
        }
    }
}
