package io.specscan.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** A header, path variable or query string parameter. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParamSpec(
        String name,
        String javaType,
        String type,
        Boolean required,
        String defaultValue,
        List<String> enumValues,
        String description,
        String example) {
}
