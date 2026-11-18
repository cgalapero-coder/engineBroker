package com.gtech.automaticbroker.enginebroker.kafka.producer;

import com.gtech.automaticbroker.enginebroker.config.DatabaseMessagingProperties;
import com.gtech.automaticbroker.enginebroker.kafka.entity.DatabaseOperation;
import com.gtech.automaticbroker.enginebroker.kafka.entity.DatabaseRequestEnvelope;
import com.gtech.automaticbroker.enginebroker.kafka.entity.DatabaseResponseEnvelope;
import com.gtech.automaticbroker.enginebroker.util.exception.DatabaseServiceException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class ManageDatabaseClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManageDatabaseClient.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final DatabaseMessagingProperties properties;
    private final Map<String, java.util.concurrent.CompletableFuture<DatabaseResponseEnvelope>> pendingResponses =
            new ConcurrentHashMap<>();

    public ManageDatabaseClient(
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            DatabaseMessagingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode create(String collection, Map<String, Object> document) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collection", collection);
        payload.put("document", document);
        return execute(DatabaseOperation.CREATE, payload);
    }

    public JsonNode update(String collection, String id, Map<String, Object> changes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("collection", collection);
        payload.put("id", id);
        payload.put("changes", changes);
        return execute(DatabaseOperation.UPDATE, payload);
    }

    public void acceptResponse(String rawMessage) {
        DatabaseResponseEnvelope response;
        try {
            response = objectMapper.readValue(rawMessage, DatabaseResponseEnvelope.class);
        } catch (JacksonException exception) {
            throw new DatabaseServiceException("manageDatabase returned an invalid response envelope", exception);
        }
        if (response == null) {
            throw new DatabaseServiceException("manageDatabase returned an empty response envelope");
        }
        if (response.correlationId() == null || response.correlationId().isBlank()) {
            throw new DatabaseServiceException("manageDatabase response has no correlationId");
        }
        var pending = pendingResponses.get(response.correlationId());
        if (pending == null) {
            LOGGER.debug("Ignoring manageDatabase response with no waiting request");
            return;
        }
        pending.complete(response);
    }

    private JsonNode execute(DatabaseOperation operation, Map<String, Object> payload) {
        String messageId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        var responseFuture = new java.util.concurrent.CompletableFuture<DatabaseResponseEnvelope>();
        pendingResponses.put(correlationId, responseFuture);

        try {
            DatabaseRequestEnvelope request = new DatabaseRequestEnvelope(
                    messageId,
                    correlationId,
                    operation,
                    payload,
                    true);
            try {
                kafkaTemplate.send(properties.requestTopic(), correlationId, request)
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DatabaseServiceException("Interrupted while sending request to manageDatabase", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new DatabaseServiceException("Could not send request to manageDatabase", exception);
            }

            DatabaseResponseEnvelope response;
            try {
                response = responseFuture.get(properties.responseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new DatabaseServiceException("Interrupted while waiting for manageDatabase", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new DatabaseServiceException("Timed out waiting for manageDatabase response", exception);
            }
            return validateResponse(response, operation, messageId, correlationId);
        } finally {
            pendingResponses.remove(correlationId, responseFuture);
        }
    }

    private JsonNode validateResponse(
            DatabaseResponseEnvelope response,
            DatabaseOperation operation,
            String messageId,
            String correlationId) {
        if (!messageId.equals(response.requestId())
                || !correlationId.equals(response.correlationId())
                || !operation.name().equals(response.operation())) {
            throw new DatabaseServiceException("manageDatabase response does not match the request");
        }
        if (!"SUCCESS".equals(response.status())) {
            String code = response.error() == null ? "UNKNOWN" : response.error().code();
            throw new DatabaseServiceException("manageDatabase rejected the request: " + code);
        }
        if (response.result() == null || response.result().isNull()) {
            throw new DatabaseServiceException("manageDatabase returned no result for a successful request");
        }
        return response.result();
    }
}
