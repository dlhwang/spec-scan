package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.BaselineDiagnostic;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public final class CorpusAccessGuard {
    public ConfinedCorpusView confine(Path allowedRoot, String locator) {
        try {
            Path absoluteRoot = allowedRoot.toAbsolutePath().normalize();
            if (!Files.isDirectory(absoluteRoot))
                throw new CorpusAccessException("CORPUS_PATH_UNRESOLVED", allowedRoot.toString());
            Path realRoot = canonicalOrAbsolute(absoluteRoot);
            Path relative = Path.of(locator);
            if (relative.isAbsolute() || relative.normalize().startsWith(".."))
                throw new CorpusAccessException("CORPUS_PATH_OUTSIDE_ALLOWED_ROOT", locator);
            Path corpus = absoluteRoot.resolve(relative).normalize();
            if (!Files.isDirectory(corpus))
                throw new CorpusAccessException("CORPUS_PATH_UNRESOLVED", locator);
            Path realCorpus = canonicalOrAbsolute(corpus);
            if (!isWithin(realRoot, realCorpus))
                throw new CorpusAccessException("CORPUS_PATH_OUTSIDE_ALLOWED_ROOT", locator);
            try (var paths = Files.walk(corpus)) {
                paths.forEach(path -> validatePath(realRoot, path));
            }
            return new ConfinedCorpusView(realRoot, corpus);
        } catch (CorpusAccessException exception) { throw exception; }
        catch (IOException | InvalidPathException exception) {
            throw new CorpusAccessException("CORPUS_PATH_UNRESOLVED", locator + ": "
                + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private void validatePath(Path root, Path path) {
        try {
            if (Files.isSymbolicLink(path)) throw new CorpusAccessException("CORPUS_PATH_ESCAPE", path.toString());
            if (!isWithin(root, canonicalOrAbsolute(path)))
                throw new CorpusAccessException("CORPUS_PATH_ESCAPE", path.toString());
        } catch (IOException exception) {
            throw new CorpusAccessException("CORPUS_PATH_UNRESOLVED", path.toString());
        }
    }

    private boolean isWithin(Path root, Path target) throws IOException {
        Path canonicalRoot = root.toAbsolutePath().normalize();
        Path canonicalTarget = target.toAbsolutePath().normalize();
        if (canonicalTarget.startsWith(canonicalRoot)) return true;
        String rootText = canonicalRoot.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        String targetText = canonicalTarget.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        return targetText.equals(rootText) || targetText.startsWith(rootText + "/");
    }

    private static Path canonicalOrAbsolute(Path path) throws IOException {
        try { return path.toRealPath(); }
        catch (AccessDeniedException exception) {
            return path.toAbsolutePath().normalize();
        }
    }

    public static final class ConfinedCorpusView {
        private final Path allowedRoot;
        private final Path corpusRoot;
        private ConfinedCorpusView(Path allowedRoot, Path corpusRoot) {
            this.allowedRoot = allowedRoot; this.corpusRoot = corpusRoot;
        }
        public List<String> listSourcePaths() {
            try (var paths = Files.walk(corpusRoot)) {
                return paths.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                    .map(corpusRoot::relativize).map(path -> path.toString().replace('\\', '/')).sorted().toList();
            } catch (IOException exception) { throw new CorpusAccessException("CORPUS_PATH_UNRESOLVED", exception.getMessage()); }
        }
        public byte[] readSource(String relativePath) {
            try {
                Path relative = Path.of(relativePath);
                if (relative.isAbsolute() || relative.normalize().startsWith(".."))
                    throw new CorpusAccessException("CORPUS_PATH_ESCAPE", relativePath);
                Path target = corpusRoot.resolve(relative).normalize();
                if (!target.startsWith(corpusRoot) || Files.isSymbolicLink(target)
                        || !within(allowedRoot, CorpusAccessGuard.canonicalOrAbsolute(target)))
                    throw new CorpusAccessException("CORPUS_PATH_ESCAPE", relativePath);
                return Files.readAllBytes(target);
            } catch (CorpusAccessException exception) { throw exception; }
            catch (IOException exception) { throw new CorpusAccessException("CORPUS_PATH_UNRESOLVED", relativePath); }
        }
        private boolean within(Path root, Path target) throws IOException {
            Path canonicalRoot = root.toAbsolutePath().normalize();
            Path canonicalTarget = target.toAbsolutePath().normalize();
            if (canonicalTarget.startsWith(canonicalRoot)) return true;
            String rootText = canonicalRoot.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            String targetText = canonicalTarget.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            return targetText.equals(rootText) || targetText.startsWith(rootText + "/");
        }
    }

    public static final class CorpusAccessException extends RuntimeException {
        private final String code;
        public CorpusAccessException(String code, String details) { super(details); this.code = code; }
        public String code() { return code; }
        public BaselineDiagnostic diagnostic(String corpusId) {
            return new BaselineDiagnostic(code, BaselineDiagnostic.Severity.ERROR, "preflight", corpusId,
                null, null, getMessage());
        }
    }
}
