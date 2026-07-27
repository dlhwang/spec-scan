package io.atworks.specscan.analysis.support.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.specscan.analysis.domain.evaluation.BaselineManifest;
import io.atworks.specscan.analysis.domain.evaluation.CompletionManifest;
import io.atworks.specscan.analysis.domain.evaluation.CorpusManifest;
import java.io.IOException;
import java.nio.file.Path;

public final class BaselineJsonCodec {
    private final ObjectMapper mapper;

    public BaselineJsonCodec() {
        mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }

    public CorpusManifest readCorpusManifest(Path path) { return read(path, CorpusManifest.class); }
    public BaselineManifest readBaselineManifest(Path path) { return read(path, BaselineManifest.class); }
    public CompletionManifest readCompletionManifest(Path path) { return read(path, CompletionManifest.class); }

    public int readSchemaVersion(Path path) {
        try {
            var node = mapper.readTree(path.toFile()).get("schemaVersion");
            if (node == null || !node.canConvertToInt())
                throw new IllegalArgumentException("schemaVersion is required at " + path);
            return node.intValue();
        } catch (IOException exception) {
            throw new IllegalArgumentException("cannot read schemaVersion at " + path, exception);
        }
    }

    public <T> T read(Path path, Class<T> type) {
        try { return mapper.readValue(path.toFile(), type); }
        catch (IOException exception) { throw new IllegalArgumentException("invalid " + type.getSimpleName()
            + " at " + path + ": " + exception.getMessage(), exception); }
    }

    public byte[] toCanonicalJson(Object value) {
        try { return mapper.writeValueAsBytes(value); }
        catch (JsonProcessingException exception) {
            throw new IllegalStateException("baseline JSON serialization failed", exception);
        }
    }
}
