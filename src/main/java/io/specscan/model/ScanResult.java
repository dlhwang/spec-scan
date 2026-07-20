package io.specscan.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Top-level output document. */
public class ScanResult {
    public String source;
    public String scannedAt;
    public Map<String, Object> stats = new LinkedHashMap<>();
    public List<ApiEndpoint> apis = new ArrayList<>();
}
