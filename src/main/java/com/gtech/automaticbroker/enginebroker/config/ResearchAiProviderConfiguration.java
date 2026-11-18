package com.gtech.automaticbroker.enginebroker.config;

import com.gtech.automaticbroker.enginebroker.service.ResearchAiProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class ResearchAiProviderConfiguration {

    @Bean
    @Primary
    ResearchAiProvider selectedResearchAiProvider(
            @Qualifier("researchAiProviderImplementation") List<ResearchAiProvider> providers,
            AiProviderProperties properties) {
        return providers.stream()
                .filter(provider -> provider.providerName().equalsIgnoreCase(properties.provider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Configured AI provider '%s' is not registered. Available providers: %s"
                                .formatted(properties.provider(), providers.stream()
                                        .map(ResearchAiProvider::providerName)
                                        .sorted()
                                        .toList())));
    }
}
