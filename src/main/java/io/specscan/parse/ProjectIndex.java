package io.specscan.parse;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Parsed compilation units plus lookup indexes over all project types. */
public class ProjectIndex {
    public final Path root;
    public final List<CompilationUnit> units = new ArrayList<>();
    /** Fully-qualified (dot-separated, including nested) type name -> AST declaration. */
    public final Map<String, TypeDeclaration<?>> typesByQName = new HashMap<>();
    public final Map<String, List<TypeDeclaration<?>>> typesBySimpleName = new HashMap<>();

    public ProjectIndex(Path root) {
        this.root = root;
    }

    void register(TypeDeclaration<?> type) {
        type.getFullyQualifiedName().ifPresent(fqn -> typesByQName.put(fqn, type));
        typesBySimpleName.computeIfAbsent(type.getNameAsString(), k -> new ArrayList<>()).add(type);
    }

    public Optional<TypeDeclaration<?>> byQualifiedName(String qname) {
        return Optional.ofNullable(typesByQName.get(qname));
    }

    /** Resolve a simple name to a project type when unambiguous. */
    public Optional<TypeDeclaration<?>> bySimpleName(String simple) {
        List<TypeDeclaration<?>> list = typesBySimpleName.get(simple);
        if (list == null || list.size() != 1) return Optional.empty();
        return Optional.of(list.get(0));
    }

    /** Find methods by name within a type (including static). */
    public List<MethodDeclaration> methods(TypeDeclaration<?> type, String name, int arity) {
        List<MethodDeclaration> out = new ArrayList<>();
        for (MethodDeclaration m : type.getMethodsByName(name)) {
            if (m.getParameters().size() == arity) out.add(m);
        }
        return out;
    }

    /** Superclass declaration of a class, when it is a project type. */
    public Optional<TypeDeclaration<?>> superclassOf(TypeDeclaration<?> type) {
        if (!(type instanceof ClassOrInterfaceDeclaration cid) || cid.getExtendedTypes().isEmpty()) {
            return Optional.empty();
        }
        String simple = cid.getExtendedTypes().get(0).getName().getIdentifier();
        try {
            var resolved = cid.getExtendedTypes().get(0).resolve();
            var byQ = byQualifiedName(resolved.asReferenceType().getQualifiedName());
            if (byQ.isPresent()) return byQ;
        } catch (RuntimeException ignored) {
            // fall through to simple-name lookup
        }
        return bySimpleName(simple);
    }
}
