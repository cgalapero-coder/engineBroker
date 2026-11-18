package com.gtech.automaticbroker.enginebroker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gtech.automaticbroker.enginebroker.config.ResearchProperties;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderCommunicationException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderTimeoutException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderUnavailableException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidAiResponseException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.json.JsonMapper;

class OllamaResearchAiProviderTest {

    @Test
    void sendsCommonPromptThroughSpringAiAndParsesTheStructuredResponse() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system instructions")).thenReturn(requestSpec);
        when(requestSpec.user("user prompt")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("""
                {"summary":"Cautious result","conclusions":[],"uncertainties":[]}
                """);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var provider = provider(builder, executor, Duration.ofSeconds(1));

            var result = provider.research(new ResearchAiRequest("system instructions", "user prompt"));

            assertThat(provider.providerName()).isEqualTo("ollama");
            assertThat(provider.modelName()).isEqualTo("test-model");
            assertThat(result.summary()).isEqualTo("Cautious result");
            verify(requestSpec).system("system instructions");
            verify(requestSpec).user("user prompt");
        }
    }

    @Test
    void distinguishesUnavailableCommunicationTimeoutAndInvalidResponse() {
        assertThatThrownBy(() -> executeWithFailure(
                new RuntimeException(new ConnectException("refused")), Duration.ofSeconds(1)))
                .isInstanceOf(AiProviderUnavailableException.class);
        assertThatThrownBy(() -> executeWithFailure(new IllegalStateException("failed"), Duration.ofSeconds(1)))
                .isInstanceOf(AiProviderCommunicationException.class);
        assertThatThrownBy(() -> executeWithContent("not-json", Duration.ofSeconds(1)))
                .isInstanceOf(InvalidAiResponseException.class);
        assertThatThrownBy(() -> executeWithDelay(Duration.ofMillis(100)))
                .isInstanceOf(AiProviderTimeoutException.class);
    }

    private void executeWithFailure(Throwable failure, Duration timeout) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.user("user")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenThrow((RuntimeException) failure);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            provider(builder, executor, timeout).research(new ResearchAiRequest("system", "user"));
        }
    }

    private void executeWithContent(String content, Duration timeout) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.user("user")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(content);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            provider(builder, executor, timeout).research(new ResearchAiRequest("system", "user"));
        }
    }

    private void executeWithDelay(Duration timeout) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.user("user")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenAnswer(invocation -> {
            Thread.sleep(1_000);
            return "{}";
        });
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            provider(builder, executor, timeout).research(new ResearchAiRequest("system", "user"));
        }
    }

    private OllamaResearchAiProvider provider(
            ChatClient.Builder builder,
            ExecutorService executor,
            Duration timeout) {
        return new OllamaResearchAiProvider(
                builder,
                JsonMapper.builder().build(),
                executor,
                new ResearchProperties(timeout),
                "test-model");
    }
}
