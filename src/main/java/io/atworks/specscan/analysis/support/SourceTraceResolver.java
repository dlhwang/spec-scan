package io.atworks.specscan.analysis.support;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import io.atworks.specscan.ingestion.domain.SourceTrace;

import java.nio.file.Path;

public class SourceTraceResolver {

    public static SourceTrace resolve(Node node, Path workspaceRoot, Path currentFile) {
        String relativePath = workspaceRoot.relativize(currentFile).toString().replace("\\", "/");
        int startLine = 0;
        int endLine = 0;

        if (node != null && node.getRange().isPresent()) {
            Range range = node.getRange().get();
            startLine = range.begin.line;
            endLine = range.end.line;
        }

        return new SourceTrace(relativePath, startLine, endLine);
    }
}
