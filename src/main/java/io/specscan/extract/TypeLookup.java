package io.specscan.extract;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import io.specscan.parse.ProjectIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a type reference text ("PropertyVO", "PropertyResponse.PropertyVO", fully
 * qualified name) to a project type, honoring Java scoping: enclosing types first, then
 * same-file types, imports, same package, finally a global unique-name match.
 */
public final class TypeLookup {

    private TypeLookup() {
    }

    public static Optional<TypeDeclaration<?>> find(ProjectIndex index, String text, TypeDeclaration<?> context) {
        if (text == null || text.isEmpty()) return Optional.empty();
        String cleaned = text.replaceAll("<[^>]*>", "").trim();

        Optional<TypeDeclaration<?>> exact = index.byQualifiedName(cleaned);
        if (exact.isPresent()) return exact;

        String firstSegment = cleaned.contains(".") ? cleaned.substring(0, cleaned.indexOf('.')) : cleaned;
        String lastSegment = cleaned.substring(cleaned.lastIndexOf('.') + 1);

        if (context != null) {
            // enclosing type chain (innermost first): self, outer classes
            List<TypeDeclaration<?>> enclosing = new ArrayList<>();
            TypeDeclaration<?> cur = context;
            while (cur != null) {
                enclosing.add(cur);
                cur = cur.findAncestor(TypeDeclaration.class).map(t -> (TypeDeclaration<?>) t).orElse(null);
            }
            for (TypeDeclaration<?> t : enclosing) {
                if (t.getNameAsString().equals(firstSegment)) {
                    Optional<TypeDeclaration<?>> r = descend(index, t, cleaned);
                    if (r.isPresent()) return r;
                }
                for (var member : t.getMembers()) {
                    if (member instanceof TypeDeclaration<?> nested && nested.getNameAsString().equals(firstSegment)) {
                        Optional<TypeDeclaration<?>> r = descend(index, nested, cleaned);
                        if (r.isPresent()) return r;
                    }
                }
            }
            Optional<CompilationUnit> cu = context.findCompilationUnit();
            if (cu.isPresent()) {
                // top-level types in the same file
                for (TypeDeclaration<?> t : cu.get().getTypes()) {
                    if (t.getNameAsString().equals(firstSegment)) {
                        Optional<TypeDeclaration<?>> r = descend(index, t, cleaned);
                        if (r.isPresent()) return r;
                    }
                }
                // imports
                for (var imp : cu.get().getImports()) {
                    if (imp.isAsterisk()) {
                        Optional<TypeDeclaration<?>> r = index.byQualifiedName(imp.getNameAsString() + "." + cleaned);
                        if (r.isPresent()) return r;
                    } else if (imp.getName().getIdentifier().equals(firstSegment)) {
                        String base = imp.getNameAsString();
                        String rest = cleaned.equals(firstSegment) ? "" : cleaned.substring(firstSegment.length());
                        Optional<TypeDeclaration<?>> r = index.byQualifiedName(base + rest.replace('.', '.'));
                        if (r.isPresent()) return r;
                    }
                }
                // same package
                String pkg = cu.get().getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
                if (!pkg.isEmpty()) {
                    Optional<TypeDeclaration<?>> r = index.byQualifiedName(pkg + "." + cleaned);
                    if (r.isPresent()) return r;
                }
            }
        }

        // global: dotted suffix, then unique simple name
        if (cleaned.contains(".")) {
            List<TypeDeclaration<?>> suffixMatches = new ArrayList<>();
            for (Map.Entry<String, TypeDeclaration<?>> e : index.typesByQName.entrySet()) {
                if (e.getKey().endsWith("." + cleaned)) suffixMatches.add(e.getValue());
            }
            if (suffixMatches.size() == 1) return Optional.of(suffixMatches.get(0));
        }
        return index.bySimpleName(lastSegment);
    }

    /** Follows nested segments: given type matching the first segment, walk a.b.c members. */
    private static Optional<TypeDeclaration<?>> descend(ProjectIndex index, TypeDeclaration<?> first, String dotted) {
        String[] parts = dotted.split("\\.");
        TypeDeclaration<?> cur = first;
        for (int i = 1; i < parts.length; i++) {
            TypeDeclaration<?> next = null;
            for (var member : cur.getMembers()) {
                if (member instanceof TypeDeclaration<?> nested && nested.getNameAsString().equals(parts[i])) {
                    next = nested;
                    break;
                }
            }
            if (next == null) return Optional.empty();
            cur = next;
        }
        return Optional.of(cur);
    }
}
