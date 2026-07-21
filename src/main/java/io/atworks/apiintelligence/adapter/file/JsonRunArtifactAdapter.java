package io.atworks.apiintelligence.adapter.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.atworks.apiintelligence.port.out.RunArtifactPort;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonRunArtifactAdapter implements RunArtifactPort {

    private final ObjectMapper mapper = new ObjectMapper().enable(
        SerializationFeature.INDENT_OUTPUT);

    public Path createRun(Path root, String runId) {
        try {
            Path base = root.toAbsolutePath().normalize();
            Files.createDirectories(base);
            Path dir = base.resolve(runId).normalize();
            if (!dir.startsWith(base)) {
                throw new IllegalArgumentException("run path escapes output root");
            }
            Files.createDirectory(dir);
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("ARTIFACT_WRITE_FAILED", e);
        }
    }

    public void writeJson(Path runDirectory, String relativeName, Object value) {
        try {
            Path root = runDirectory.toAbsolutePath().normalize();
            Path target = root.resolve(relativeName).normalize();
            if (!target.startsWith(root) || relativeName.contains("\\") && relativeName.contains(
                "..")) {
                throw new IllegalArgumentException("artifact path escapes run root");
            }
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("ARTIFACT_WRITE_FAILED", e);
        }
    }
}
