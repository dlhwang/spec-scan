package io.atworks.specscan.analysis.support;

import io.atworks.specscan.analysis.domain.CandidateChunk;
import io.atworks.specscan.analysis.domain.ValidationCandidate;

public class PromptBuilder {

    /**
     * CandidateChunk 정보를 포함한 LLM 전송용 정규화 프롬프트를 조립합니다.
     */
    public String buildPrompt(CandidateChunk chunk) {
        return buildPrompt(chunk, new GraphContextSelector.GraphContext(java.util.List.of(), java.util.List.of(), 0, 0));
    }

    public String buildPrompt(CandidateChunk chunk, GraphContextSelector.GraphContext graphContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("System Instructions:\n");
        sb.append("You are an expert API contract extractor. Parse the following validation candidates and normalize them into a structured JSON array.\n");
        sb.append("Use only the candidate evidence and the supplied graph context. Ignore unrelated framework annotations or disconnected code paths.\n");
        sb.append("Each item in the array MUST follow this JSON Schema:\n");
        sb.append("{\n");
        sb.append("  \"candidateId\": \"string\",\n");
        sb.append("  \"targetPath\": \"string\",\n");
        sb.append("  \"operator\": \"NOT_NULL | SIZE | PATTERN | EMAIL | MIN_AGE | CUSTOM_RULE | VALIDATION_LOGIC | BUSINESS_CONSTRAINT\",\n");
        sb.append("  \"expected\": \"string (value or rule description)\",\n");
        sb.append("  \"confidence\": 0.0 - 1.0,\n");
        sb.append("  \"llmReason\": \"string (justification for normalization)\"\n");
        sb.append("}\n\n");

        sb.append("User Inputs:\n");
        sb.append("Chunk Metadata:\n");
        sb.append("  ChunkId: ").append(chunk.chunkId()).append("\n");
        sb.append("  EndpointPath: ").append(chunk.endpointPath()).append("\n");
        sb.append("  SourceType: ").append(chunk.sourceType()).append("\n\n");

        if (graphContext != null && graphContext.hasContext()) {
            sb.append("Relevant Code Graph Context (token-optimized subgraph):\n");
            sb.append("  SelectedNodes: ").append(graphContext.nodeSummaries().size())
                .append(" / TotalNodes: ").append(graphContext.totalNodeCount()).append("\n");
            for (String nodeSummary : graphContext.nodeSummaries()) {
                sb.append("  - Node: ").append(nodeSummary).append("\n");
            }
            sb.append("  SelectedEdges: ").append(graphContext.edgeSummaries().size())
                .append(" / TotalEdges: ").append(graphContext.totalEdgeCount()).append("\n");
            for (String edgeSummary : graphContext.edgeSummaries()) {
                sb.append("  - Edge: ").append(edgeSummary).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Candidates to parse:\n");
        for (ValidationCandidate candidate : chunk.candidates()) {
            sb.append("  - CandidateId: ").append(candidate.candidateId()).append("\n");
            sb.append("    TargetPath: ").append(candidate.targetPath()).append("\n");
            sb.append("    EvidenceSnippet: ").append(candidate.evidenceSnippet().replace("\n", "\n      ")).append("\n");
            sb.append("    ConfidenceHint: ").append(candidate.confidence()).append("\n\n");
        }

        sb.append("Output JSON ONLY. Do not include markdown code block formatting.");
        return sb.toString();
    }
}
