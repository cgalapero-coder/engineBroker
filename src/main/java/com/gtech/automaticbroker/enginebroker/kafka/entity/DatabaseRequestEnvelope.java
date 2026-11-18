package com.gtech.automaticbroker.enginebroker.kafka.entity;

import java.util.Map;

public record DatabaseRequestEnvelope(
        String messageId,
        String correlationId,
        DatabaseOperation operation,
        Map<String, Object> payload,
        boolean responseRequired) {
}
