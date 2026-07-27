package io.atworks.specscan.analysis.evaluation.baseline;

import io.atworks.specscan.analysis.domain.evaluation.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.Comparator;

final class BaselineTestWorkspace {
    private BaselineTestWorkspace() {}

    static Materialized materialize(Path temporaryRoot) throws IOException, URISyntaxException {
        Path source = Path.of(BaselineTestWorkspace.class.getClassLoader()
            .getResource("rule-based-static-analysis/baseline/v1").toURI());
        Path target = temporaryRoot.resolve("baseline-v1");
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path); Path output = target.resolve(relative.toString());
                if (Files.isDirectory(path)) Files.createDirectories(output);
                else Files.copy(path, output, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        return new Materialized(target, target.resolve("corpus-manifest.json"),
            temporaryRoot.resolve("evaluation-workspaces"), temporaryRoot.resolve("reports"));
    }

    static BaselineRunRequest proposal(Materialized materialized) {
        return new BaselineRunRequest(BaselineRunRequest.RunPurpose.SNAPSHOT_PROPOSAL,
            materialized.manifest(), null, EvaluationProfile.g01(), "java-baseline-u01",
            materialized.root(), materialized.evaluationRoot(), materialized.reportRoot());
    }

    record Materialized(Path root, Path manifest, Path evaluationRoot, Path reportRoot) {}
}
