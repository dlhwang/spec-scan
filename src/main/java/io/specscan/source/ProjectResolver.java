package io.specscan.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Resolves the CLI input (git URL or local path) to a local directory. */
public final class ProjectResolver {

    private ProjectResolver() {
    }

    public static boolean isGitUrl(String input) {
        return input.startsWith("http://") || input.startsWith("https://")
                || input.startsWith("git@") || input.startsWith("ssh://");
    }

    /** Returns a local checkout of the project, cloning shallowly when given a git URL. */
    public static Path resolve(String input) throws IOException, InterruptedException {
        if (!isGitUrl(input)) {
            Path p = Path.of(input).toAbsolutePath().normalize();
            if (!Files.isDirectory(p)) {
                throw new IOException("Not a directory: " + p);
            }
            return p;
        }
        Path target = Files.createTempDirectory("spec-scan-" + UUID.randomUUID().toString().substring(0, 8));
        System.err.println("[spec-scan] cloning " + input + " -> " + target);
        Process proc = new ProcessBuilder("git", "clone", "--depth", "1", input, target.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(proc.getInputStream().readAllBytes());
        int code = proc.waitFor();
        if (code != 0) {
            throw new IOException("git clone failed (exit " + code + "):\n" + output);
        }
        return target;
    }
}
