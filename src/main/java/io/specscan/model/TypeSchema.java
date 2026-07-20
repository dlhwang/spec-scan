package io.specscan.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recursive JSON-ish schema of a request/response body type. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TypeSchema {
    public String javaType;
    /** object | array | string | integer | number | boolean */
    public String type;
    /** date, date-time, time, uuid, email ... */
    public String format;
    public List<String> enumValues;
    public Map<String, TypeSchema> fields;
    public TypeSchema items;
    public String description;
    public String example;
    /** Set instead of fields when a cycle was detected: refers to javaType already emitted. */
    public String ref;

    /** Validation/constraint annotations found on the field that has this schema. */
    @JsonIgnore
    public List<FieldAnnotation> annotations = new ArrayList<>();

    public static TypeSchema of(String javaType, String type) {
        TypeSchema s = new TypeSchema();
        s.javaType = javaType;
        s.type = type;
        return s;
    }

    public TypeSchema field(String name, TypeSchema child) {
        if (fields == null) fields = new LinkedHashMap<>();
        fields.put(name, child);
        return this;
    }

    /** A raw annotation captured from source, e.g. name=Size, members={min:10}. */
    public record FieldAnnotation(String name, Map<String, Object> members) {
    }
}
