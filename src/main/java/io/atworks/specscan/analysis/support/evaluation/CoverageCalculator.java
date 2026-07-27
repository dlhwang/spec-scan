package io.atworks.specscan.analysis.support.evaluation;

import io.atworks.specscan.analysis.domain.evaluation.BaselineObservation;
import java.util.ArrayList;
import java.util.List;

public final class CoverageCalculator {
    public BaselineObservation.CoverageSnapshot calculate(
            List<BaselineObservation.CorpusRunObservation> corpora) {
        int graphNumerator = 0, graphDenominator = 0, semanticNumerator = 0, semanticDenominator = 0;
        int unclassified = 0, unresolved = 0, partial = 0;
        List<String> graphExclusions = new ArrayList<>(), semanticExclusions = new ArrayList<>();
        for (BaselineObservation.CorpusRunObservation corpus : corpora) {
            if (corpus.availabilityStatus() == BaselineObservation.AvailabilityStatus.EXCLUDED_OPTIONAL) {
                graphExclusions.add(corpus.corpusId()); semanticExclusions.add(corpus.corpusId()); continue;
            }
            for (BaselineObservation.EndpointObservation endpoint : corpus.endpoints()) {
                graphDenominator++;
                if (endpoint.graphStatus() == BaselineObservation.GraphStatus.COMPLETE) graphNumerator++;
                if (endpoint.graphStatus() == BaselineObservation.GraphStatus.PARTIAL) partial++;
                semanticDenominator += endpoint.eligibleFailureCandidateCount();
                for (BaselineObservation.ObservedCandidate candidate : endpoint.candidates()) {
                    String status = candidate.observation().semanticStatus();
                    if ("RESOLVED".equals(status)) semanticNumerator++;
                    else if ("UNRESOLVED".equals(status)) unresolved++;
                    else unclassified++;
                }
            }
        }
        return new BaselineObservation.CoverageSnapshot(
            new BaselineObservation.CoverageDimension(graphNumerator, graphDenominator, graphExclusions),
            new BaselineObservation.CoverageDimension(semanticNumerator, semanticDenominator, semanticExclusions),
            unclassified, unresolved, partial);
    }
}
