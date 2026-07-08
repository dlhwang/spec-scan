package io.atworks.specscan.ingestion.application;

import io.atworks.specscan.ingestion.domain.*;
import io.atworks.specscan.ingestion.port.RepositoryFetcherPort;
import io.atworks.specscan.ingestion.port.WorkspacePreparerPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RepositoryIngestionService {

    private final RepositoryFetcherPort fetcherPort;
    private final WorkspacePreparerPort preparerPort;

    public RepositoryIngestionService(RepositoryFetcherPort fetcherPort, WorkspacePreparerPort preparerPort) {
        this.fetcherPort = fetcherPort;
        this.preparerPort = preparerPort;
    }

    public RepositorySource ingest(RepositoryRequest request) throws IngestionException {
        Instant startedAt = Instant.now();
        
        // 1. Validate Input
        RepositoryIdentity identity = validateAndParseRequest(request);

        // 2. Prepare Workspace
        WorkspaceContext workspace = preparerPort.prepare();
        Path workspacePath = Paths.get(workspace.workspacePath());

        try {
            // 3. Fetch (Clone)
            fetcherPort.fetch(identity, workspacePath);

            // 4. Scan Repository
            List<SourceRootCandidate> sourceRoots = new ArrayList<>();
            List<ExcludedPathRecord> excludedPaths = new ArrayList<>();
            List<IngestionWarning> warnings = new ArrayList<>();

            scanWorkspace(workspacePath, sourceRoots, excludedPaths, warnings);

            // 5. Aggregate Summary
            int totalJavaFiles = sourceRoots.stream().mapToInt(SourceRootCandidate::javaFileCount).sum();
            String buildToolHint = detectGlobalBuildTool(sourceRoots);
            
            JavaInventorySummary javaSummary = new JavaInventorySummary(
                totalJavaFiles,
                sourceRoots.size(),
                (int) sourceRoots.stream().map(SourceRootCandidate::moduleName).distinct().count(),
                true,
                0
            );

            SafetyPolicyHint safetyHint = new SafetyPolicyHint(
                List.of("repository clone", "directory traversal", "source/config/build file reads", "inventory generation", "metadata extraction"),
                List.of("gradle build", "mvn test", "npm install", "application run", "test execution", "repository script execution", "arbitrary binary execution"),
                "1.0"
            );

            Instant completedAt = Instant.now();
            IngestionMetadata metadata = new IngestionMetadata(
                startedAt,
                completedAt,
                Duration.between(startedAt, completedAt).toMillis(),
                request.branch() != null ? "BRANCH" : request.tag() != null ? "TAG" : request.commit() != null ? "COMMIT" : "DEFAULT",
                identity.requestedRef(),
                warnings.size()
            );

            return new RepositorySource(
                identity,
                workspace,
                sourceRoots,
                buildToolHint,
                javaSummary,
                excludedPaths,
                warnings,
                safetyHint,
                metadata
            );

        } catch (IngestionException e) {
            preparerPort.clean(workspace);
            throw e;
        } catch (Exception e) {
            preparerPort.clean(workspace);
            throw new IngestionException(
                IngestionErrorCode.STATIC_ANALYSIS_POLICY_VIOLATION,
                "Unexpected ingestion failure: " + e.getMessage(),
                e
            );
        }
    }

    private RepositoryIdentity validateAndParseRequest(RepositoryRequest request) {
        String url = request.repositoryUrl();
        if (url == null || url.isBlank()) {
            throw new IngestionException(IngestionErrorCode.INVALID_REPOSITORY_URL, "Repository URL cannot be null or empty");
        }

        // SSH or local path check
        if (url.startsWith("git@") || url.startsWith("ssh://") || !url.startsWith("http")) {
            throw new IngestionException(IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE, "Only HTTP/HTTPS protocols are supported: " + url);
        }

        // GitHub host check
        if (!url.contains("github.com")) {
            throw new IngestionException(IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE, "Only GitHub host is supported: " + url);
        }

        // Archive / Release / Raw assets check
        if (url.contains("/archive/") || url.contains("/releases/") || url.contains("/raw/")) {
            throw new IngestionException(IngestionErrorCode.UNSUPPORTED_REPOSITORY_SOURCE, "Archive, raw or release asset URLs are not supported: " + url);
        }

        // Match owner and repo
        Pattern pattern = Pattern.compile("https?://github\\.com/([^/]+)/([^/\\s]+)");
        Matcher matcher = pattern.matcher(url);
        if (!matcher.find()) {
            throw new IngestionException(IngestionErrorCode.INVALID_REPOSITORY_URL, "Invalid GitHub repository URL pattern: " + url);
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2);
        if (repo.endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }

        String normalizedCloneUrl = "https://github.com/" + owner + "/" + repo + ".git";
        String requestedRef = null;
        if (request.branch() != null && !request.branch().isBlank()) {
            requestedRef = request.branch();
        } else if (request.tag() != null && !request.tag().isBlank()) {
            requestedRef = request.tag();
        } else if (request.commit() != null && !request.commit().isBlank()) {
            requestedRef = request.commit();
        }

        return new RepositoryIdentity(
            "github.com",
            owner,
            repo,
            normalizedCloneUrl,
            requestedRef
        );
    }

    private void scanWorkspace(Path workspace, List<SourceRootCandidate> sourceRoots,
                               List<ExcludedPathRecord> excludedPaths, List<IngestionWarning> warnings) {
        try (Stream<Path> stream = Files.walk(workspace)) {
            stream.forEach(path -> {
                String relativePath = workspace.relativize(path).toString().replace("\\", "/");
                if (relativePath.isEmpty()) {
                    return;
                }

                // Check directories that should be excluded
                if (isExcludedDirectory(relativePath)) {
                    excludedPaths.add(new ExcludedPathRecord(
                        relativePath,
                        "SCAN_EXCLUDE",
                        "Excluded from static scan scope"
                    ));
                    return;
                }

                // Detect src/main/java source root candidates
                if (Files.isDirectory(path) && relativePath.endsWith("src/main/java")) {
                    String moduleName = detectModuleName(workspace, path);
                    String buildToolHint = detectLocalBuildTool(path.getParent().getParent().getParent());
                    
                    int javaFileCount = countJavaFiles(path);
                    int springAnnotationCount = countSpringAnnotations(path);
                    
                    int selectionPriority = springAnnotationCount > 0 ? 1 : 2;

                    sourceRoots.add(new SourceRootCandidate(
                        moduleName,
                        relativePath,
                        buildToolHint,
                        true,
                        javaFileCount,
                        springAnnotationCount,
                        selectionPriority,
                        "DETECTED"
                    ));
                }
            });
        } catch (IOException e) {
            warnings.add(new IngestionWarning(
                "SCAN_WARNING",
                "Partial failure during repository scan: " + e.getMessage(),
                workspace.toString(),
                "LOW"
            ));
        }

        if (sourceRoots.isEmpty()) {
            warnings.add(new IngestionWarning(
                "SOURCE_ROOT_NOT_FOUND",
                "No active source roots (src/main/java) found in repository",
                workspace.toString(),
                "MEDIUM"
            ));
        }
    }

    private boolean isExcludedDirectory(String relativePath) {
        String[] parts = relativePath.split("/");
        for (String part : parts) {
            if (part.equals("build") || part.equals("target") || part.equals("out") ||
                part.equals(".git") || part.equals(".idea") || part.equals("node_modules") ||
                part.equals(".gradle") || part.equals(".settings") || part.equals("bin")) {
                return true;
            }
        }
        return false;
    }

    private String detectModuleName(Path workspaceRoot, Path sourceRoot) {
        Path parent = sourceRoot.getParent().getParent().getParent(); // module root
        if (parent.equals(workspaceRoot)) {
            return "root";
        }
        return parent.getFileName().toString();
    }

    private String detectLocalBuildTool(Path moduleRoot) {
        if (Files.exists(moduleRoot.resolve("pom.xml"))) {
            return "Maven";
        } else if (Files.exists(moduleRoot.resolve("build.gradle")) || Files.exists(moduleRoot.resolve("build.gradle.kts"))) {
            return "Gradle";
        }
        return "Unknown";
    }

    private String detectGlobalBuildTool(List<SourceRootCandidate> sourceRoots) {
        boolean hasMaven = sourceRoots.stream().anyMatch(r -> "Maven".equals(r.buildToolHint()));
        boolean hasGradle = sourceRoots.stream().anyMatch(r -> "Gradle".equals(r.buildToolHint()));
        if (hasMaven && hasGradle) {
            return "Mixed";
        } else if (hasMaven) {
            return "Maven";
        } else if (hasGradle) {
            return "Gradle";
        }
        return "Unknown";
    }

    private int countJavaFiles(Path sourceRoot) {
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            return (int) stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    private int countSpringAnnotations(Path sourceRoot) {
        int[] count = {0};
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        count[0] += countOccurrences(content, "@RestController")
                                 + countOccurrences(content, "@Controller")
                                 + countOccurrences(content, "@RequestMapping")
                                 + countOccurrences(content, "@GetMapping")
                                 + countOccurrences(content, "@PostMapping")
                                 + countOccurrences(content, "@PutMapping")
                                 + countOccurrences(content, "@DeleteMapping")
                                 + countOccurrences(content, "@PatchMapping");
                    } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
        return count[0];
    }

    private int countOccurrences(String source, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = source.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }
}
