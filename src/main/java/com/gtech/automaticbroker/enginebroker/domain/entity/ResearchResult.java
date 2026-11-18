package com.gtech.automaticbroker.enginebroker.domain.entity;

import java.util.List;

public record ResearchResult(
        String executionId,
        String status,
        String summary,
        List<ResearchConclusion> conclusions,
        List<ResearchEvidence> evidence,
        List<String> uncertainties,
        boolean simulation,
        boolean historyPersisted,
        boolean dataPersisted,
        int persistedDataCount) {
}
