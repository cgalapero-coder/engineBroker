package com.gtech.automaticbroker.enginebroker.config;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.research.ai")
public record AiProviderProperties(@NotBlank @DefaultValue("ollama") String provider) {

    public AiProviderProperties {
        if (provider != null) {
            provider = provider.trim().toLowerCase(Locale.ROOT);
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("AI provider name must not be blank");
        }
    }
}
