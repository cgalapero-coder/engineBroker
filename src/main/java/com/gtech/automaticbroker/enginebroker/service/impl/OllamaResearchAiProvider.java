package com.gtech.automaticbroker.enginebroker.service.impl;

import com.gtech.automaticbroker.enginebroker.config.ResearchProperties;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;
import com.gtech.automaticbroker.enginebroker.service.ResearchAiProvider;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderCommunicationException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderTimeoutException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderUnavailableException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidAiResponseException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@Qualifier("researchAiProviderImplementation")
public class OllamaResearchAiProvider implements ResearchAiProvider {

    private static final String RESEARCH_RESPONSE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "summary": {"type": "string"},
                "conclusions": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "category": {
                        "type": "string",
                        "enum": ["FACT", "INFERENCE", "HYPOTHESIS", "PREDICTION"]
                      },
                      "text": {"type": "string"},
                      "sourceIds": {"type": "array", "items": {"type": "string"}},
                      "confidence": {"type": "string"}
                    },
                    "required": ["category", "text", "sourceIds", "confidence"],
                    "additionalProperties": false
                  }
                },
                "uncertainties": {"type": "array", "items": {"type": "string"}}
              },
              "required": ["summary", "conclusions", "uncertainties"],
              "additionalProperties": false
            }
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService aiExecutor;
    private final ResearchProperties properties;
    private final String modelName;

    public OllamaResearchAiProvider(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            ExecutorService aiExecutor,
            ResearchProperties properties,
            @Value("${spring.ai.ollama.chat.options.model}") String modelName) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.aiExecutor = aiExecutor;
        this.properties = properties;
        this.modelName = modelName;
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public ResearchAiResponse research(ResearchAiRequest request) {
        Future<String> call = aiExecutor.submit(() -> chatClient.prompt()
                .options(OllamaChatOptions.builder().outputSchema(RESEARCH_RESPONSE_SCHEMA))
                .system(request.systemPrompt())
                .user(request.userPrompt())
                .call()
                .content());
        String content;
        try {
            content = call.get(properties.aiTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            call.cancel(true);
            throw new AiProviderTimeoutException("AI provider timed out", exception);
        } catch (InterruptedException exception) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiProviderCommunicationException("AI provider call was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (isConnectionFailure(cause)) {
                throw new AiProviderUnavailableException("Ollama is unavailable", cause);
            }
            throw new AiProviderCommunicationException("AI provider call failed", cause);
        }

        if (content == null || content.isBlank()) {
            throw new InvalidAiResponseException("AI provider returned an empty response");
        }
        try {
            return objectMapper.readValue(content, ResearchAiResponse.class);
        } catch (JacksonException exception) {
            throw new InvalidAiResponseException("AI provider response was not valid research JSON", exception);
        }
    }

    private boolean isConnectionFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException
                    || cause instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }
}
