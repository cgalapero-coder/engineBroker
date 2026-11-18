package com.gtech.automaticbroker.enginebroker.service.impl;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class ResearchPromptBuilder {

    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public ResearchPromptBuilder(
            ObjectMapper objectMapper,
            @Value("classpath:prompts/instrument-research-system.md") Resource systemPromptResource) {
        this.objectMapper = objectMapper;
        this.systemPrompt = readPrompt(systemPromptResource);
    }

    public ResearchAiRequest build(ResearchRequest request, List<ResearchEvidence> evidence) {
        try {
            String userPrompt = objectMapper.writeValueAsString(new ResearchInput(
                    request.market(),
                    request.instrument(),
                    request.objective(),
                    evidence));
            return new ResearchAiRequest(systemPrompt, userPrompt);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Research input could not be serialized", exception);
        }
    }

    private String readPrompt(Resource resource) {
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Research system prompt could not be loaded", exception);
        }
    }

    private record ResearchInput(
            String market,
            String instrument,
            String objective,
            List<ResearchEvidence> evidence) {
    }
}
