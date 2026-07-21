package io.atworks.apiintelligence.adapter.javaparser;

import io.atworks.apiintelligence.domain.source.SourceLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SourceSnippetReader {

    private SourceSnippetReader() {
    }

    public static String read(Path file, SourceLocation l) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (l.startLine() > lines.size() || l.endLine() > lines.size()) {
            throw new IllegalArgumentException("source range outside file");
        }
        return String.join(System.lineSeparator(), lines.subList(l.startLine() - 1, l.endLine()));
    }
}
