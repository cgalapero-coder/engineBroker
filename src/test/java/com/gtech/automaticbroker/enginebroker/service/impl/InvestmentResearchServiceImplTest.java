package com.gtech.automaticbroker.enginebroker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchConclusion;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.kafka.producer.ManageDatabaseClient;
import com.gtech.automaticbroker.enginebroker.service.ResearchAiProvider;
import com.gtech.automaticbroker.enginebroker.service.ResearchSourceAdapter;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchRequestException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class InvestmentResearchServiceImplTest {

    private final ResearchSourceAdapter sourceAdapter = org.mockito.Mockito.mock(ResearchSourceAdapter.class);
    private final ResearchAiProvider aiProvider = org.mockito.Mockito.mock(ResearchAiProvider.class);
    private final ManageDatabaseClient databaseClient = org.mockito.Mockito.mock(ManageDatabaseClient.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final ResearchPromptBuilder promptBuilder =
            new ResearchPromptBuilder(objectMapper, new ClassPathResource("prompts/instrument-research-system.md"));
    private final InvestmentResearchServiceImpl service = new InvestmentResearchServiceImpl(
            sourceAdapter,
            aiProvider,
            promptBuilder,
            new ResearchResultNormalizer(),
            databaseClient,
            objectMapper);

    @BeforeEach
    void createHistoryRecord() {
        when(databaseClient.create(eq("histData"), anyMap()))
                .thenReturn(objectMapper.readTree("{\"id\":\"history-1\"}"));
        when(aiProvider.providerName()).thenReturn("test-provider");
        when(aiProvider.modelName()).thenReturn("test-model");
    }

    @Test
    void completesHypothesisOnlyResearchWithoutClaimingMarketDataWasPersisted() {
        ResearchRequest request = request(true);
        when(sourceAdapter.collect(request)).thenReturn(List.of());
        when(aiProvider.research(any(ResearchAiRequest.class))).thenReturn(new ResearchAiResponse(
                "No independently verified market data was available.",
                List.of(new ResearchConclusion(
                        ResearchConclusion.Category.HYPOTHESIS,
                        "The instrument may be affected by market conditions.",
                        List.of(),
                        "low")),
                List.of("External market data was unavailable.")));

        var result = service.investigate(request);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.simulation()).isTrue();
        assertThat(result.historyPersisted()).isTrue();
        assertThat(result.dataPersisted()).isFalse();
        assertThat(result.persistedDataCount()).isZero();
        verify(databaseClient, never()).create(eq("biData"), anyMap());
        verify(databaseClient).update(eq("histData"), eq("history-1"), anyMap());
    }

    @Test
    void persistsOnlyVerifiedAdapterEvidenceUsingTheBiDataContract() {
        ResearchRequest request = request(true);
        ResearchEvidence evidence = new ResearchEvidence(
                "source-1", "configured source", "EU", "instrument-1", "reference",
                "sample-value", "unit", "EUR", "2026-01-01T00:00:00Z", "Verified value.", true);
        when(sourceAdapter.collect(request)).thenReturn(List.of(evidence));
        when(aiProvider.research(any(ResearchAiRequest.class))).thenReturn(new ResearchAiResponse(
                "A sourced result.",
                List.of(new ResearchConclusion(
                        ResearchConclusion.Category.FACT,
                        "The configured source supplied a value.",
                        List.of("source-1"),
                        "high")),
                List.of()));
        when(databaseClient.create(eq("biData"), anyMap()))
                .thenReturn(objectMapper.readTree("{\"id\":\"bi-1\"}"));

        var result = service.investigate(request);

        assertThat(result.dataPersisted()).isTrue();
        assertThat(result.persistedDataCount()).isEqualTo(1);
        ArgumentCaptor<Map<String, Object>> document = ArgumentCaptor.forClass(Map.class);
        verify(databaseClient).create(eq("biData"), document.capture());
        assertThat(document.getValue())
                .containsEntry("sourceId", "source-1")
                .containsEntry("instrument", "instrument-1")
                .containsEntry("value", "sample-value")
                .containsKey("collectedAt")
                .doesNotContainKey("id");
    }

    @Test
    void recordsAiFailureInHistoryAndPropagatesTheError() {
        ResearchRequest request = request(true);
        when(sourceAdapter.collect(request)).thenReturn(List.of());
        when(aiProvider.research(any(ResearchAiRequest.class)))
                .thenThrow(new AiProviderException("provider unavailable", new IllegalStateException()));

        assertThatThrownBy(() -> service.investigate(request))
                .isInstanceOf(AiProviderException.class);

        ArgumentCaptor<Map<String, Object>> changes = ArgumentCaptor.forClass(Map.class);
        verify(databaseClient).update(eq("histData"), eq("history-1"), changes.capture());
        assertThat(changes.getValue())
                .containsEntry("eventType", "INVESTIGATION_FAILED")
                .containsEntry("result", "{\"status\":\"FAILED\","
                        + "\"errorCode\":\"AI_PROVIDER_COMMUNICATION_FAILED\"}");
    }

    @Test
    void rejectsNonSimulationRequestsBeforePersistence() {
        assertThatThrownBy(() -> service.investigate(request(false)))
                .isInstanceOf(InvalidResearchRequestException.class);
        verify(databaseClient, never()).create(eq("histData"), anyMap());
    }

    private ResearchRequest request(boolean simulation) {
        return new ResearchRequest("EU", "instrument-1", "Investigate the instrument.", null, null, simulation);
    }
}
