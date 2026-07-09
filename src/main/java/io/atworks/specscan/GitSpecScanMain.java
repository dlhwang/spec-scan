package io.atworks.specscan;

import java.util.LinkedHashMap;
import java.util.Map;

public class GitSpecScanMain {

    public static void main(String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);

        require(options, "projectName");
        String repositoryUrl = require(options, "repositoryUrl");
        require(options, "baseUrl");
        String revisionType = options.get("revisionType");
        String revision = options.get("revision");

        String payload = new GitExecutionSpecScanService().scan(repositoryUrl, revisionType, revision);
        System.out.println(payload);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int separatorIndex = arg.indexOf('=');
            if (separatorIndex < 0) {
                continue;
            }
            options.put(arg.substring(2, separatorIndex), arg.substring(separatorIndex + 1));
        }
        return options;
    }

    private static String require(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required option: " + name);
        }
        return value;
    }
}
