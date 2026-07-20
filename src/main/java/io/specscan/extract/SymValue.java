package io.specscan.extract;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Symbolic value tracked through the call graph: either a JSONPath rooted at a request
 * input ($.password of the body, $.propertyId of the path, ...) or a synthetic object
 * whose fields map to other symbolic values (result of a DTO -> domain mapping).
 */
public final class SymValue {

    /** JSONPath when this value is a projection of a request input; null for object values. */
    public final String path;
    /** BODY | QUERY | PATH | HEADER */
    public final String location;
    /** Field map for object values built by constructors / builders; null for path values. */
    public final Map<String, SymValue> fields;
    /** Qualified type name when known — used to find methods invoked on this value. */
    public final String typeQName;

    private SymValue(String path, String location, Map<String, SymValue> fields, String typeQName) {
        this.path = path;
        this.location = location;
        this.fields = fields;
        this.typeQName = typeQName;
    }

    public static SymValue path(String path, String location, String typeQName) {
        return new SymValue(path, location, null, typeQName);
    }

    public static SymValue object(Map<String, SymValue> fields, String typeQName) {
        return new SymValue(null, null, fields, typeQName);
    }

    public boolean isTracked() {
        return path != null || (fields != null && fields.values().stream().anyMatch(v -> v != null && v.isTracked()));
    }

    /** Projection through a property access (getter, record accessor, field access). */
    public SymValue member(String name) {
        if (fields != null) return fields.get(name);
        if (path != null) return new SymValue(path.equals("$") ? "$." + name : path + "." + name, location, null, null);
        return null;
    }

    /** First request-rooted path value reachable in this value (self or nested field). */
    public SymValue firstPathValue() {
        if (path != null) return this;
        if (fields != null) {
            for (SymValue v : fields.values()) {
                if (v == null) continue;
                SymValue r = v.firstPathValue();
                if (r != null) return r;
            }
        }
        return null;
    }

    public SymValue withType(String typeQName) {
        return new SymValue(path, location, fields, typeQName);
    }

    /** Stable fingerprint for recursion guards. */
    public String fingerprint() {
        if (path != null) return location + ":" + path;
        if (fields == null) return "?";
        StringBuilder sb = new StringBuilder("{");
        fields.forEach((k, v) -> sb.append(k).append('=').append(v == null ? "null" : v.fingerprint()).append(','));
        return sb.append('}').toString();
    }

    public static Map<String, SymValue> orderedMap() {
        return new LinkedHashMap<>();
    }
}
