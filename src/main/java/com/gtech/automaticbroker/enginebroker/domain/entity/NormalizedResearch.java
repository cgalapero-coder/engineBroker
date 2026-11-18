package com.gtech.automaticbroker.enginebroker.domain.entity;

import java.util.List;

public record NormalizedResearch(
        String summary,
        List<ResearchConclusion> conclusions,
        List<String> uncertainties,
        List<ResearchEvidence> verifiedEvidence) {
}
