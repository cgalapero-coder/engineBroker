package com.gtech.automaticbroker.enginebroker.service.impl;

import com.gtech.automaticbroker.enginebroker.domain.entity.NormalizedResearch;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchConclusion;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidAiResponseException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchEvidenceException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ResearchResultNormalizer {

    public NormalizedResearch normalize(
            ResearchAiResponse response,
            List<ResearchEvidence> suppliedEvidence) {
        if (response == null
                || response.summary() == null || response.summary().isBlank()
                || response.conclusions() == null
                || response.uncertainties() == null
                || suppliedEvidence == null) {
            throw new InvalidAiResponseException("AI response is missing required structured fields");
        }

        List<ResearchEvidence> verifiedEvidence = filterVerifiedEvidence(suppliedEvidence);
        Set<String> validSourceIds = verifiedEvidence.stream()
                .map(ResearchEvidence::sourceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> uncertainties = new LinkedHashSet<>();
        for (String uncertainty : response.uncertainties()) {
            if (uncertainty == null || uncertainty.isBlank()) {
                throw new InvalidAiResponseException("AI response contains an empty uncertainty");
            }
            uncertainties.add(uncertainty.trim());
        }

        List<ResearchConclusion> conclusions = new ArrayList<>();
        for (ResearchConclusion conclusion : response.conclusions()) {
            if (conclusion == null
                    || conclusion.category() == null
                    || conclusion.text() == null || conclusion.text().isBlank()
                    || conclusion.sourceIds() == null
                    || conclusion.confidence() == null || conclusion.confidence().isBlank()) {
                throw new InvalidAiResponseException("AI response contains an incomplete conclusion");
            }

            List<String> validReferences = conclusion.sourceIds().stream()
                    .filter(sourceId -> sourceId != null && validSourceIds.contains(sourceId))
                    .distinct()
                    .toList();
            boolean hasUnsupportedReference = conclusion.sourceIds().stream()
                    .anyMatch(sourceId -> sourceId == null || !validSourceIds.contains(sourceId));

            if (conclusion.category() == ResearchConclusion.Category.FACT
                    && (validReferences.isEmpty() || hasUnsupportedReference)) {
                uncertainties.add("A factual claim was omitted because its source could not be verified.");
                continue;
            }
            if (hasUnsupportedReference) {
                uncertainties.add("One or more unsupported source references were removed from a conclusion.");
            }
            conclusions.add(new ResearchConclusion(
                    conclusion.category(),
                    conclusion.text().trim(),
                    validReferences,
                    conclusion.confidence().trim()));
        }

        String summary = response.summary().trim();
        if (verifiedEvidence.isEmpty()) {
            uncertainties.add("No external data source is configured; market facts could not be independently verified.");
        }
        return new NormalizedResearch(
                summary,
                List.copyOf(conclusions),
                List.copyOf(uncertainties),
                verifiedEvidence);
    }

    public List<ResearchEvidence> filterVerifiedEvidence(List<ResearchEvidence> suppliedEvidence) {
        if (suppliedEvidence == null) {
            throw new InvalidResearchEvidenceException("Research source adapter returned no evidence collection");
        }
        Set<ResearchEvidence> uniqueEvidence = new LinkedHashSet<>();
        for (ResearchEvidence evidence : suppliedEvidence) {
            if (evidence == null || !evidence.verified()) {
                continue;
            }
            if (blank(evidence.sourceId())
                    || blank(evidence.sourceName())
                    || blank(evidence.market())
                    || blank(evidence.instrument())
                    || blank(evidence.dataType())
                    || blank(evidence.value())
                    || blank(evidence.observedAt())) {
                throw new InvalidAiResponseException("Verified evidence is missing required provenance fields");
            }
            uniqueEvidence.add(evidence);
        }
        return List.copyOf(uniqueEvidence);
    }

    public List<ResearchEvidence> filterVerifiedEvidence(
            ResearchRequest request,
            List<ResearchEvidence> suppliedEvidence) {
        List<ResearchEvidence> verifiedEvidence = filterVerifiedEvidence(suppliedEvidence);
        for (ResearchEvidence evidence : verifiedEvidence) {
            if (!request.market().equalsIgnoreCase(evidence.market())
                    || !request.instrument().equalsIgnoreCase(evidence.instrument())) {
                throw new InvalidResearchEvidenceException(
                        "Research source returned evidence for a different market or instrument");
            }
        }
        return verifiedEvidence;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
