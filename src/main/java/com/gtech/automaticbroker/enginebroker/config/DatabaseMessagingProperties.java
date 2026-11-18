package com.gtech.automaticbroker.enginebroker.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.manage-database")
public record DatabaseMessagingProperties(
        @NotBlank String requestTopic,
        @NotBlank String responseTopic,
        @NotNull Duration sendTimeout,
        @NotNull Duration responseTimeout,
        @NotBlank String deadLetterSuffix,
        long retryIntervalMs,
        long maxRetries) {

    public DatabaseMessagingProperties {
        if (sendTimeout == null || sendTimeout.isZero() || sendTimeout.isNegative()
                || responseTimeout == null || responseTimeout.isZero() || responseTimeout.isNegative()
                || retryIntervalMs < 0 || maxRetries < 0) {
            throw new IllegalArgumentException(
                    "Kafka timeouts must be greater than zero and retry settings must be non-negative");
        }
    }
}
