package com.gtech.automaticbroker.enginebroker.kafka.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gtech.automaticbroker.enginebroker.config.DatabaseMessagingProperties;
import com.gtech.automaticbroker.enginebroker.kafka.entity.DatabaseRequestEnvelope;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ManageDatabaseClientTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void sendsCreateUsingTheManageDatabaseEnvelopeAndCorrelatedResponse() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        DatabaseMessagingProperties properties = new DatabaseMessagingProperties(
                "manageDatabase.requests",
                "manageDatabase.responses",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                ".DLT",
                0,
                0);
        ManageDatabaseClient client = new ManageDatabaseClient(kafkaTemplate, objectMapper, properties);

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> {
            String topic = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            DatabaseRequestEnvelope request = invocation.getArgument(2);
            assertThat(topic).isEqualTo("manageDatabase.requests");

            assertThat(request.messageId()).isNotBlank();
            assertThat(request.correlationId()).isEqualTo(key);
            assertThat(request.operation().name()).isEqualTo("CREATE");
            assertThat(request.responseRequired()).isTrue();
            assertThat(request.payload()).containsEntry("collection", "histData");

            client.acceptResponse("""
                    {"messageId":"response-1","requestId":"%s","correlationId":"%s",
                     "operation":"CREATE","status":"SUCCESS","result":{"id":"history-1"},
                     "error":null,"occurredAt":"2026-01-01T00:00:00Z"}
                    """.formatted(request.messageId(), request.correlationId()));
            return CompletableFuture.<SendResult<String, String>>completedFuture(null);
        });

        var result = client.create("histData", Map.of("eventType", "INVESTIGATION_STARTED"));

        assertThat(result.path("id").asString()).isEqualTo("history-1");
    }

    @Test
    @SuppressWarnings("removal")
    void springJsonSerializerWritesTheEnvelopeAsRawJsonNotAsAnEscapedString() throws Exception {
        DatabaseRequestEnvelope request = new DatabaseRequestEnvelope(
                "message-1",
                "correlation-1",
                com.gtech.automaticbroker.enginebroker.kafka.entity.DatabaseOperation.CREATE,
                Map.of("collection", "histData", "document", Map.of("eventType", "STARTED")),
                true);
        var headers = new RecordHeaders();
        JsonSerializer<Object> serializer = new JsonSerializer<>();
        try {
            byte[] bytes = serializer.serialize("manageDatabase.requests", headers, request);
            String decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(decoded).as("serialized envelope: %s", decoded)
                    .startsWith("{")
                    .doesNotStartWith("\"");
            var envelope = objectMapper.readTree(decoded);
            assertThat(envelope.path("operation").asString()).isEqualTo("CREATE");
            assertThat(envelope.path("payload").path("collection").asString()).isEqualTo("histData");
            assertThat(envelope.path("responseRequired").asBoolean()).isTrue();
        } finally {
            serializer.close();
        }
    }

    @Test
    void timesOutWhenManageDatabaseDoesNotPublishACorrelatedResponse() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        DatabaseMessagingProperties properties = new DatabaseMessagingProperties(
                "manageDatabase.requests",
                "manageDatabase.responses",
                Duration.ofSeconds(1),
                Duration.ofMillis(30),
                ".DLT",
                0,
                0);
        ManageDatabaseClient client = new ManageDatabaseClient(kafkaTemplate, objectMapper, properties);

        assertThatThrownBy(() -> client.create("histData", Map.of("eventType", "STARTED")))
                .isInstanceOf(com.gtech.automaticbroker.enginebroker.util.exception.DatabaseServiceException.class)
                .hasMessageContaining("Timed out waiting");
    }

    @Test
    void reportsKafkaSendFailureInsteadOfReturningAResponse() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        DatabaseMessagingProperties properties = new DatabaseMessagingProperties(
                "manageDatabase.requests",
                "manageDatabase.responses",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                ".DLT",
                0,
                0);
        ManageDatabaseClient client = new ManageDatabaseClient(kafkaTemplate, objectMapper, properties);

        assertThatThrownBy(() -> client.create("histData", Map.of("eventType", "STARTED")))
                .isInstanceOf(com.gtech.automaticbroker.enginebroker.util.exception.DatabaseServiceException.class)
                .hasMessageContaining("Could not send request");
    }
}
