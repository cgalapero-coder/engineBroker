package com.gtech.automaticbroker.enginebroker.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;
import com.gtech.automaticbroker.enginebroker.service.ResearchAiProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ResearchAiProviderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ResearchAiProviderConfiguration.class, TestProviderConfiguration.class);

    @Test
    void selectsOllamaByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ResearchAiProvider.class).providerName()).isEqualTo("ollama");
            assertThat(context.getBean(ResearchAiProvider.class).modelName()).isEqualTo("test-model");
        });
    }

    @Test
    void failsClearlyWhenConfiguredProviderIsUnknown() {
        contextRunner.withPropertyValues("app.research.ai.provider=unknown").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("Configured AI provider 'unknown' is not registered. "
                            + "Available providers: [ollama]");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestProviderConfiguration {

        @Bean
        @Qualifier("researchAiProviderImplementation")
        ResearchAiProvider ollamaProvider() {
            return new ResearchAiProvider() {
                @Override
                public ResearchAiResponse research(ResearchAiRequest request) {
                    return null;
                }

                @Override
                public String providerName() {
                    return "ollama";
                }

                @Override
                public String modelName() {
                    return "test-model";
                }
            };
        }
    }
}
