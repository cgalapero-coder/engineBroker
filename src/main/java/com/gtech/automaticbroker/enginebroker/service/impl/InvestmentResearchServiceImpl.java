package com.gtech.automaticbroker.enginebroker.service.impl;

import com.gtech.automaticbroker.enginebroker.domain.entity.NormalizedResearch;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchResult;
import com.gtech.automaticbroker.enginebroker.kafka.producer.ManageDatabaseClient;
import com.gtech.automaticbroker.enginebroker.service.InvestmentResearchService;
import com.gtech.automaticbroker.enginebroker.service.ResearchAiProvider;
import com.gtech.automaticbroker.enginebroker.service.ResearchSourceAdapter;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderCommunicationException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderTimeoutException;
import com.gtech.automaticbroker.enginebroker.util.exception.AiProviderUnavailableException;
import com.gtech.automaticbroker.enginebroker.util.exception.DatabaseServiceException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidAiResponseException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchEvidenceException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class InvestmentResearchServiceImpl implements InvestmentResearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvestmentResearchServiceImpl.class);
    private static final String HISTORY_COLLECTION = "histData";
    private static final String BI_DATA_COLLECTION = "biData";
    private static final String OPERATION_NAME = "INSTRUMENT_RESEARCH";

    private final ResearchSourceAdapter sourceAdapter;
    private final ResearchAiProvider aiProvider;
    private final ResearchPromptBuilder promptBuilder;
    private final ResearchResultNormalizer normalizer;
    private final ManageDatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public InvestmentResearchServiceImpl(
            ResearchSourceAdapter sourceAdapter,
            ResearchAiProvider aiProvider,
            ResearchPromptBuilder promptBuilder,
            ResearchResultNormalizer normalizer,
            ManageDatabaseClient databaseClient,
            ObjectMapper objectMapper) {
        this.sourceAdapter = sourceAdapter;
        this.aiProvider = aiProvider;
        this.promptBuilder = promptBuilder;
        this.normalizer = normalizer;
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResearchResult investigate(ResearchRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.simulation())) {
            throw new InvalidResearchRequestException("Research is available only in simulation mode");
        }

        String executionId = UUID.randomUUID().toString();
        String historyId = requireId(databaseClient.create(HISTORY_COLLECTION, historyDocument(
                request,
                executionId,
                "INVESTIGATION_STARTED",
                "RUNNING",
                requestDescription(request, "Research started"))));

        try {
            List<ResearchEvidence> suppliedEvidence = sourceAdapter.collect(request);
            List<ResearchEvidence> verifiedEvidence = normalizer.filterVerifiedEvidence(request, suppliedEvidence);
            ResearchAiRequest aiRequest = promptBuilder.build(request, verifiedEvidence);
            LOGGER.info("Running research executionId={} aiProvider={} aiModel={}",
                    executionId, aiProvider.providerName(), aiProvider.modelName());
            ResearchAiResponse aiResponse = aiProvider.research(aiRequest);
            NormalizedResearch normalized = normalizer.normalize(aiResponse, verifiedEvidence);

            List<String> uncertainties = new ArrayList<>(normalized.uncertainties());
            int persistedDataCount = persistEvidence(normalized.verifiedEvidence(), uncertainties);
            ResearchResult result = new ResearchResult(
                    executionId,
                    "COMPLETED",
                    normalized.summary(),
                    normalized.conclusions(),
                    normalized.verifiedEvidence(),
                    List.copyOf(uncertainties),
                    true,
                    true,
                    persistedDataCount > 0,
                    persistedDataCount);

            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("eventType", "INVESTIGATION_COMPLETED");
            changes.put("occurredAt", Instant.now().toString());
            changes.put("result", serializeResult(result));
            changes.put("description", "Instrument research completed in simulation mode.");
            databaseClient.update(HISTORY_COLLECTION, historyId, changes);
            LOGGER.info("Research completed executionId={} persistedDataCount={}", executionId, persistedDataCount);
            return result;
        } catch (RuntimeException failure) {
            recordFailure(executionId, historyId, failure);
            throw failure;
        }
    }

    private int persistEvidence(List<ResearchEvidence> evidence, List<String> uncertainties) {
        int persistedCount = 0;
        for (ResearchEvidence item : evidence) {
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("sourceId", item.sourceId());
            document.put("sourceName", item.sourceName());
            document.put("market", item.market());
            document.put("instrument", item.instrument());
            document.put("dataType", item.dataType());
            document.put("value", item.value());
            addIfPresent(document, "unit", item.unit());
            addIfPresent(document, "currency", item.currency());
            document.put("observedAt", item.observedAt());
            document.put("collectedAt", Instant.now().toString());
            addIfPresent(document, "description", item.description());
            requireId(databaseClient.create(BI_DATA_COLLECTION, document));
            persistedCount++;
        }
        if (evidence.isEmpty()) {
            uncertainties.add("No verified data was available to persist to biData.");
        }
        return persistedCount;
    }

    private Map<String, Object> historyDocument(
            ResearchRequest request,
            String executionId,
            String eventType,
            String result,
            String description) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("actorType", request.userId() == null || request.userId().isBlank() ? "SYSTEM" : "USER");
        addIfPresent(document, "userId", request.userId());
        addIfPresent(document, "userRole", request.userRole());
        document.put("eventType", eventType);
        document.put("operationId", executionId);
        document.put("operation", OPERATION_NAME);
        document.put("occurredAt", Instant.now().toString());
        document.put("simulationData", Map.of(
                "amounts", List.of(),
                "services", List.of("ollama", "manageDatabase"),
                "gains", List.of(),
                "losses", List.of(),
                "performance", List.of()));
        document.put("result", result);
        document.put("description", description);
        return document;
    }

    private void recordFailure(
            String executionId,
            String historyId,
            RuntimeException failure) {
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("eventType", "INVESTIGATION_FAILED");
        changes.put("occurredAt", Instant.now().toString());
        changes.put("result", "{\"status\":\"FAILED\",\"errorCode\":\"" + errorCode(failure) + "\"}");
        changes.put("description", "Research failed in simulation mode.");
        try {
            databaseClient.update(HISTORY_COLLECTION, historyId, changes);
            LOGGER.warn("Research failed executionId={} errorType={}",
                    executionId, failure.getClass().getSimpleName());
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
            LOGGER.error("Research failure could not be recorded executionId={} errorType={}",
                    executionId, auditFailure.getClass().getSimpleName());
        }
    }

    private String serializeResult(ResearchResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Research result could not be serialized for history", exception);
        }
    }

    private String requireId(JsonNode document) {
        JsonNode id = document == null ? null : document.get("id");
        if (id == null || !id.isString() || id.asString().isBlank()) {
            throw new DatabaseServiceException("manageDatabase did not return a persisted document id");
        }
        return id.asString();
    }

    private String errorCode(RuntimeException failure) {
        if (failure instanceof AiProviderTimeoutException) {
            return "AI_PROVIDER_TIMEOUT";
        }
        if (failure instanceof AiProviderUnavailableException) {
            return "AI_PROVIDER_UNAVAILABLE";
        }
        if (failure instanceof AiProviderCommunicationException || failure instanceof AiProviderException) {
            return "AI_PROVIDER_COMMUNICATION_FAILED";
        }
        if (failure instanceof InvalidAiResponseException) {
            return "INVALID_AI_RESPONSE";
        }
        if (failure instanceof InvalidResearchEvidenceException) {
            return "INVALID_RESEARCH_EVIDENCE";
        }
        if (failure instanceof DatabaseServiceException) {
            return "PERSISTENCE_FAILED";
        }
        return "RESEARCH_FAILED";
    }

    private void addIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private String requestDescription(ResearchRequest request, String event) {
        return "%s for market=%s, instrument=%s. Objective: %s"
                .formatted(event, request.market(), request.instrument(), request.objective());
    }
}
