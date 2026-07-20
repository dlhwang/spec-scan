package io.specscan.extract;

import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import io.specscan.model.TypeSchema;
import io.specscan.parse.AstUtils;
import io.specscan.parse.ProjectIndex;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts Java types into recursive {@link TypeSchema}, resolving generics best-effort. */
public class SchemaBuilder {

    private static final Map<String, String[]> SIMPLE = new HashMap<>();

    static {
        put("String", "string", null);
        put("CharSequence", "string", null);
        put("char", "string", null);
        put("Character", "string", null);
        put("int", "integer", null);
        put("Integer", "integer", null);
        put("long", "integer", null);
        put("Long", "integer", null);
        put("short", "integer", null);
        put("Short", "integer", null);
        put("byte", "integer", null);
        put("Byte", "integer", null);
        put("BigInteger", "integer", null);
        put("double", "number", null);
        put("Double", "number", null);
        put("float", "number", null);
        put("Float", "number", null);
        put("BigDecimal", "number", null);
        put("boolean", "boolean", null);
        put("Boolean", "boolean", null);
        put("LocalDate", "string", "date");
        put("LocalDateTime", "string", "date-time");
        put("Instant", "string", "date-time");
        put("ZonedDateTime", "string", "date-time");
        put("OffsetDateTime", "string", "date-time");
        put("Date", "string", "date-time");
        put("LocalTime", "string", "time");
        put("UUID", "string", "uuid");
        put("URI", "string", "uri");
        put("URL", "string", "uri");
        put("Object", "object", null);
    }

    private static void put(String name, String type, String format) {
        SIMPLE.put(name, new String[]{type, format});
    }

    private static final Set<String> COLLECTIONS = Set.of("List", "Set", "Collection", "Iterable", "ArrayList", "HashSet", "LinkedList", "SortedSet");

    private final ProjectIndex index;

    public SchemaBuilder(ProjectIndex index) {
        this.index = index;
    }

    /** Entry point from an AST type in its original context. */
    public TypeSchema fromAstType(Type type) {
        try {
            return fromResolved(type.resolve(), new ArrayDeque<>());
        } catch (RuntimeException e) {
            return fromSyntactic(type, new ArrayDeque<>());
        }
    }

    public TypeSchema fromResolved(ResolvedType rt, Deque<String> stack) {
        if (rt == null) return TypeSchema.of("java.lang.Object", "object");
        if (rt.isPrimitive()) {
            String name = rt.asPrimitive().describe();
            String[] tf = SIMPLE.getOrDefault(name, new String[]{"string", null});
            return withFormat(TypeSchema.of(name, tf[0]), tf[1]);
        }
        if (rt.isArray()) {
            TypeSchema s = TypeSchema.of(rt.describe(), "array");
            s.items = fromResolved(rt.asArrayType().getComponentType(), stack);
            return s;
        }
        if (rt.isVoid()) return null;
        if (!rt.isReferenceType()) return TypeSchema.of(rt.describe(), "object");

        ResolvedReferenceType ref = rt.asReferenceType();
        String qname = ref.getQualifiedName();
        if (qname.equals("java.lang.Void")) return null;
        String simple = qname.substring(qname.lastIndexOf('.') + 1);

        if (SIMPLE.containsKey(simple)) {
            String[] tf = SIMPLE.get(simple);
            return withFormat(TypeSchema.of(qname, tf[0]), tf[1]);
        }
        List<ResolvedType> args = typeArguments(ref);
        if (COLLECTIONS.contains(simple)) {
            TypeSchema s = TypeSchema.of(ref.describe(), "array");
            s.items = args.isEmpty() ? TypeSchema.of("java.lang.Object", "object") : fromResolved(args.get(0), stack);
            return s;
        }
        if (simple.equals("Map")) {
            TypeSchema s = TypeSchema.of(ref.describe(), "object");
            s.description = "map";
            return s;
        }
        if (simple.equals("Optional")) {
            return args.isEmpty() ? TypeSchema.of("java.lang.Object", "object") : fromResolved(args.get(0), stack);
        }
        if (qname.equals("org.springframework.data.domain.Page")
                || qname.equals("org.springframework.data.domain.Slice")) {
            return pageSchema(ref, args, stack);
        }
        if (qname.startsWith("org.springframework.http.ResponseEntity")) {
            return args.isEmpty() ? null : fromResolved(args.get(0), stack);
        }

        Optional<TypeDeclaration<?>> ast = index.byQualifiedName(qname);
        if (ast.isPresent()) {
            if (ast.get() instanceof EnumDeclaration en) return enumSchema(qname, en);
            return objectSchema(qname, ast.get(), substitution(ref), stack);
        }
        // External, unknown type
        return TypeSchema.of(qname, "object");
    }

    /** Object schema from a project class AST, walking superclasses too. */
    public TypeSchema objectSchema(String qname, TypeDeclaration<?> decl,
                                   Map<String, ResolvedType> subst, Deque<String> stack) {
        if (stack.contains(qname)) {
            TypeSchema cyclic = TypeSchema.of(qname, "object");
            cyclic.ref = qname;
            return cyclic;
        }
        stack.push(qname);
        try {
            TypeSchema s = TypeSchema.of(qname, "object");
            AstUtils.annotation(decl, "Schema").ifPresent(a -> {
                Object d = AstUtils.annotationMember(a, "description");
                if (d != null) s.description = String.valueOf(d);
            });
            List<TypeDeclaration<?>> chain = new ArrayList<>();
            TypeDeclaration<?> cur = decl;
            while (cur != null) {
                chain.add(0, cur);
                cur = index.superclassOf(cur).orElse(null);
            }
            for (TypeDeclaration<?> t : chain) {
                for (FieldDeclaration f : t.getFields()) {
                    if (f.isStatic() || f.isTransient() || AstUtils.hasAnnotation(f, "JsonIgnore")) continue;
                    for (VariableDeclarator v : f.getVariables()) {
                        String name = fieldJsonName(f, v.getNameAsString());
                        TypeSchema child = fieldSchema(v, subst, stack);
                        decorate(child, f);
                        s.field(name, child);
                    }
                }
            }
            return s;
        } finally {
            stack.pop();
        }
    }

    private TypeSchema fieldSchema(VariableDeclarator v, Map<String, ResolvedType> subst, Deque<String> stack) {
        try {
            ResolvedType rt = v.getType().resolve();
            rt = substitute(rt, subst);
            return fromResolved(rt, stack);
        } catch (RuntimeException e) {
            return fromSyntactic(v.getType(), stack);
        }
    }

    /** Attach swagger metadata + raw annotations from the declaring field. */
    private void decorate(TypeSchema child, FieldDeclaration f) {
        if (child == null) return;
        for (AnnotationExpr a : f.getAnnotations()) {
            String id = a.getName().getIdentifier();
            if (id.equals("Schema")) {
                Object d = AstUtils.annotationMember(a, "description");
                Object ex = AstUtils.annotationMember(a, "example");
                if (d != null) child.description = String.valueOf(d);
                if (ex != null) child.example = String.valueOf(ex);
            } else {
                child.annotations.add(new TypeSchema.FieldAnnotation(id, AstUtils.annotationMembers(a)));
            }
        }
    }

    private static String fieldJsonName(FieldDeclaration f, String defaultName) {
        return AstUtils.annotation(f, "JsonProperty")
                .map(a -> AstUtils.annotationMember(a, "value"))
                .map(String::valueOf)
                .orElse(defaultName);
    }

    private TypeSchema enumSchema(String qname, EnumDeclaration en) {
        TypeSchema s = TypeSchema.of(qname, "string");
        s.enumValues = new ArrayList<>();
        en.getEntries().forEach(e -> s.enumValues.add(e.getNameAsString()));
        return s;
    }

    /** Syntactic fallback when symbol resolution fails. */
    public TypeSchema fromSyntactic(Type type, Deque<String> stack) {
        if (type instanceof PrimitiveType p) {
            String[] tf = SIMPLE.getOrDefault(p.asString(), new String[]{"string", null});
            return withFormat(TypeSchema.of(p.asString(), tf[0]), tf[1]);
        }
        if (type instanceof ClassOrInterfaceType c) {
            String simple = c.getName().getIdentifier();
            if (simple.equals("Void")) return null;
            if (SIMPLE.containsKey(simple)) {
                String[] tf = SIMPLE.get(simple);
                return withFormat(TypeSchema.of(simple, tf[0]), tf[1]);
            }
            List<Type> args = c.getTypeArguments().map(list -> (List<Type>) new ArrayList<Type>(list)).orElse(List.of());
            if (simple.equals("ResponseEntity") || simple.equals("HttpEntity")) {
                if (args.isEmpty()) return null;
                if (args.get(0).asString().equals("Void")) return null;
                return fromSyntactic(args.get(0), stack);
            }
            if (simple.equals("Page") || simple.equals("Slice")) {
                TypeSchema s = TypeSchema.of(c.asString(), "object");
                TypeSchema content = TypeSchema.of("java.util.List", "array");
                content.items = args.isEmpty() ? TypeSchema.of("java.lang.Object", "object") : fromSyntactic(args.get(0), stack);
                s.field("content", content);
                s.field("totalElements", TypeSchema.of("long", "integer"));
                s.field("totalPages", TypeSchema.of("int", "integer"));
                s.field("size", TypeSchema.of("int", "integer"));
                s.field("number", TypeSchema.of("int", "integer"));
                s.field("first", TypeSchema.of("boolean", "boolean"));
                s.field("last", TypeSchema.of("boolean", "boolean"));
                s.field("empty", TypeSchema.of("boolean", "boolean"));
                return s;
            }
            if (COLLECTIONS.contains(simple)) {
                TypeSchema s = TypeSchema.of(c.asString(), "array");
                s.items = args.isEmpty() ? TypeSchema.of("java.lang.Object", "object") : fromSyntactic(args.get(0), stack);
                return s;
            }
            if (simple.equals("Optional") && !args.isEmpty()) return fromSyntactic(args.get(0), stack);
            TypeDeclaration<?> context = type.findAncestor(TypeDeclaration.class)
                    .map(t -> (TypeDeclaration<?>) t).orElse(null);
            Optional<TypeDeclaration<?>> ast = TypeLookup.find(index, c.getNameWithScope(), context);
            if (ast.isPresent()) {
                String qname = ast.get().getFullyQualifiedName().orElse(simple);
                if (ast.get() instanceof EnumDeclaration en) return enumSchema(qname, en);
                return objectSchema(qname, ast.get(), Map.of(), stack);
            }
            return TypeSchema.of(c.asString(), "object");
        }
        return TypeSchema.of(type.asString(), "object");
    }

    private TypeSchema pageSchema(ResolvedReferenceType ref, List<ResolvedType> args, Deque<String> stack) {
        TypeSchema s = TypeSchema.of(ref.describe(), "object");
        TypeSchema content = TypeSchema.of("java.util.List", "array");
        content.items = args.isEmpty() ? TypeSchema.of("java.lang.Object", "object") : fromResolved(args.get(0), stack);
        s.field("content", content);
        s.field("totalElements", TypeSchema.of("long", "integer"));
        s.field("totalPages", TypeSchema.of("int", "integer"));
        s.field("size", TypeSchema.of("int", "integer"));
        s.field("number", TypeSchema.of("int", "integer"));
        s.field("numberOfElements", TypeSchema.of("int", "integer"));
        s.field("first", TypeSchema.of("boolean", "boolean"));
        s.field("last", TypeSchema.of("boolean", "boolean"));
        s.field("empty", TypeSchema.of("boolean", "boolean"));
        return s;
    }

    private static List<ResolvedType> typeArguments(ResolvedReferenceType ref) {
        try {
            return ref.typeParametersValues();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static Map<String, ResolvedType> substitution(ResolvedReferenceType ref) {
        Map<String, ResolvedType> map = new HashMap<>();
        try {
            ref.getTypeParametersMap().forEach(pair -> map.put(pair.a.getName(), pair.b));
        } catch (RuntimeException ignored) {
        }
        return map;
    }

    private static ResolvedType substitute(ResolvedType rt, Map<String, ResolvedType> subst) {
        if (subst.isEmpty()) return rt;
        if (rt.isTypeVariable()) {
            ResolvedType replacement = subst.get(rt.asTypeVariable().describe());
            return replacement != null ? replacement : rt;
        }
        return rt;
    }

    private static TypeSchema withFormat(TypeSchema s, String format) {
        s.format = format;
        return s;
    }

    /** json type name for a syntactic type — used for query/path params. */
    public static String jsonTypeOf(String simpleName) {
        String[] tf = SIMPLE.get(simpleName);
        return tf != null ? tf[0] : "string";
    }
}
