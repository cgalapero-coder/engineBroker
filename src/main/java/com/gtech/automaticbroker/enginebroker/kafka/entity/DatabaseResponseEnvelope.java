package com.gtech.automaticbroker.enginebroker.kafka.entity;

import tools.jackson.databind.JsonNode;

public record DatabaseResponseEnvelope(
        String messageId,
        String requestId,
        String correlationId,
        String operation,
        String status,
        JsonNode result,
        ResponseError error,
        String occurredAt) {

    public record ResponseError(String category, String code, String message) {
    }
}
