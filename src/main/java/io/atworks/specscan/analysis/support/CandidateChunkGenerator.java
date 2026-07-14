package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.*;

import java.util.*;
import io.atworks.specscan.analysis.domain.output.OperationKey;

public class CandidateChunkGenerator {

    /**
     * 후보 목록을 엔드포인트 및 소스타입별로 작은 청크(CandidateChunk)들로 분할합니다.
     */
    public List<CandidateChunk> generateChunks(List<ValidationCandidate> candidates, List<ApiEndpoint> endpoints) {
        List<CandidateChunk> chunks = new ArrayList<>();
        
        // endpointPath -> sourceType -> candidates
        Map<String, Map<String, List<ValidationCandidate>>> grouped = new LinkedHashMap<>();

        for (ValidationCandidate candidate : candidates) {
            String operationKey = resolveOperationKey(candidate, endpoints);
            String sourceType = candidate.sourceType();

            grouped.computeIfAbsent(operationKey, k -> new LinkedHashMap<>())
                   .computeIfAbsent(sourceType, k -> new ArrayList<>())
                   .add(candidate);
        }

        for (Map.Entry<String, Map<String, List<ValidationCandidate>>> pathEntry : grouped.entrySet()) {
            String operationKey = pathEntry.getKey();
            String path = endpointPath(operationKey);
            for (Map.Entry<String, List<ValidationCandidate>> typeEntry : pathEntry.getValue().entrySet()) {
                String type = typeEntry.getKey();
                String chunkId = "chunk-" + UUID.randomUUID().toString().substring(0, 8);
                chunks.add(new CandidateChunk(chunkId, path, type, typeEntry.getValue(), operationKey));
            }
        }

        return chunks;
    }

    private String resolveOperationKey(ValidationCandidate candidate, List<ApiEndpoint> endpoints) {
        if (candidate.operationKey() != null && !candidate.operationKey().isBlank()) return candidate.operationKey();
        if (candidate.sourceTrace() == null || candidate.sourceTrace().fileRelativePath() == null) {
            return "global";
        }
        
        String candFile = candidate.sourceTrace().fileRelativePath().replace("\\", "/");

        // 1. 후보가 추출된 파일 경로가 컨트롤러 클래스 파일 경로와 일치하는 경우 매칭
        for (ApiEndpoint endpoint : endpoints) {
            String controllerFile = endpoint.controllerClass().replace(".", "/") + ".java";
            if (candFile.endsWith(controllerFile)) {
                return OperationKey.of(endpoint).externalKey();
            }
        }

        // 2. 후보가 추출된 파일 경로가 DTO 클래스 파일 경로와 일치하는 경우 매칭
        for (ApiEndpoint endpoint : endpoints) {
            for (RequestBinding binding : endpoint.requestBindings()) {
                String dtoFile = binding.type().replace(".", "/") + ".java";
                if (candFile.endsWith(dtoFile)) {
                    return OperationKey.of(endpoint).externalKey();
                }
            }
        }

        // 3. 간접 매칭 (서비스 등)
        for (ApiEndpoint endpoint : endpoints) {
            String simpleName = candFile.substring(candFile.lastIndexOf("/") + 1).replace(".java", "");
            String baseName = simpleName.replace("Impl", "").replace("Service", "");
            if (endpoint.controllerClass().toLowerCase().contains(baseName.toLowerCase()) || 
                endpoint.controllerMethod().toLowerCase().contains(baseName.toLowerCase())) {
                return OperationKey.of(endpoint).externalKey();
            }
        }

        return "global";
    }

    private String endpointPath(String operationKey) {
        int separator = operationKey.indexOf(' ');
        return separator < 0 ? operationKey : operationKey.substring(separator + 1);
    }
}
