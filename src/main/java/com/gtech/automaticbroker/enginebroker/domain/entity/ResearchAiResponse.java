package com.gtech.automaticbroker.enginebroker.domain.entity;

import java.util.List;

public record ResearchAiResponse(
        String summary,
        List<ResearchConclusion> conclusions,
        List<String> uncertainties) {
}
