package com.gtech.automaticbroker.enginebroker.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.research")
public record ResearchProperties(@NotNull Duration aiTimeout) {

    public ResearchProperties {
        if (aiTimeout == null || aiTimeout.isZero() || aiTimeout.isNegative()) {
            throw new IllegalArgumentException("AI timeout must be greater than zero");
        }
    }
}
