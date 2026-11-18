package com.gtech.automaticbroker.enginebroker.domain.entity;

import java.util.List;

public record ResearchConclusion(
        Category category,
        String text,
        List<String> sourceIds,
        String confidence) {

    public enum Category {
        FACT,
        INFERENCE,
        HYPOTHESIS,
        PREDICTION
    }
}
