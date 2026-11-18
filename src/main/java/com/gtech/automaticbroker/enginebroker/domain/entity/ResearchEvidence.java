package com.gtech.automaticbroker.enginebroker.domain.entity;

public record ResearchEvidence(
        String sourceId,
        String sourceName,
        String market,
        String instrument,
        String dataType,
        String value,
        String unit,
        String currency,
        String observedAt,
        String description,
        boolean verified) {
}
