package com.gtech.automaticbroker.enginebroker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

class ResearchPromptBuilderTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final ResearchPromptBuilder promptBuilder = new ResearchPromptBuilder(
            objectMapper, new ClassPathResource("prompts/instrument-research-system.md"));

    @Test
    void buildsCommonAiRequestWithTheExistingPromptAndResearchInput() {
        ResearchRequest request = new ResearchRequest("EU", "SAP", "Review risks.", null, null, true);
        ResearchEvidence evidence = new ResearchEvidence(
                "source-1", "verified source", "EU", "SAP", "reference",
                "sample-value", "unit", "EUR", "2026-01-01T00:00:00Z", "Verified.", true);

        var result = promptBuilder.build(request, List.of(evidence));

        assertThat(result.systemPrompt()).contains("research");
        var input = objectMapper.readTree(result.userPrompt());
        assertThat(input.get("market").asString()).isEqualTo("EU");
        assertThat(input.get("instrument").asString()).isEqualTo("SAP");
        assertThat(input.get("objective").asString()).isEqualTo("Review risks.");
        assertThat(input.get("evidence").get(0).get("sourceId").asString()).isEqualTo("source-1");
    }
}
