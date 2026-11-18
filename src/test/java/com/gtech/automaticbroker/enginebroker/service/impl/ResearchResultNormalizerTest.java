package com.gtech.automaticbroker.enginebroker.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchConclusion;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidAiResponseException;
import com.gtech.automaticbroker.enginebroker.util.exception.InvalidResearchEvidenceException;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ResearchResultNormalizerTest {

    private final ResearchResultNormalizer normalizer = new ResearchResultNormalizer();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void parsesTheStructuredAiResponseContract() {
        ResearchAiResponse response = jsonMapper.readValue("""
                {
                  "summary": "A cautious summary.",
                  "conclusions": [{
                    "category": "HYPOTHESIS",
                    "text": "A hypothesis.",
                    "sourceIds": [],
                    "confidence": "low"
                  }],
                  "uncertainties": ["No market source was available."]
                }
                """, ResearchAiResponse.class);

        assertThat(response.summary()).isEqualTo("A cautious summary.");
        assertThat(response.conclusions().getFirst().category())
                .isEqualTo(ResearchConclusion.Category.HYPOTHESIS);
        assertThat(response.conclusions().getFirst().sourceIds()).isEmpty();
        assertThat(response.uncertainties()).containsExactly("No market source was available.");
    }

    @Test
    void removesUnverifiedFactsAndKeepsClearlyLabelledHypotheses() {
        ResearchAiResponse response = new ResearchAiResponse(
                "The request could not be verified.",
                List.of(
                        new ResearchConclusion(
                                ResearchConclusion.Category.FACT,
                                "The price increased.",
                                List.of("unknown-source"),
                                "high"),
                        new ResearchConclusion(
                                ResearchConclusion.Category.HYPOTHESIS,
                                "The instrument may react to future market changes.",
                                List.of(),
                                "low")),
                List.of("No source was available."));

        var result = normalizer.normalize(response, List.of());

        assertThat(result.conclusions()).hasSize(1);
        assertThat(result.conclusions().getFirst().category())
                .isEqualTo(ResearchConclusion.Category.HYPOTHESIS);
        assertThat(result.uncertainties())
                .anyMatch(value -> value.contains("factual claim was omitted"))
                .anyMatch(value -> value.contains("No external data source"));
        assertThat(result.verifiedEvidence()).isEmpty();
    }

    @Test
    void preservesClaimsAndOnlyVerifiedEvidenceWithMatchingProvenance() {
        ResearchEvidence evidence = new ResearchEvidence(
                "source-1",
                "Configured source",
                "EU",
                "instrument-1",
                "reference",
                "sample-value",
                "unit",
                "EUR",
                "2026-01-01T00:00:00Z",
                "Verified test evidence.",
                true);
        ResearchAiResponse response = new ResearchAiResponse(
                "A sourced research summary.",
                List.of(new ResearchConclusion(
                        ResearchConclusion.Category.FACT,
                        "A supplied measurement is available.",
                        List.of("source-1"),
                        "high")),
                List.of("The measurement is limited to the supplied source."));

        var result = normalizer.normalize(response, List.of(evidence));

        assertThat(result.conclusions()).hasSize(1);
        assertThat(result.conclusions().getFirst().sourceIds()).containsExactly("source-1");
        assertThat(result.verifiedEvidence()).containsExactly(evidence);
        assertThat(result.uncertainties()).doesNotContain(
                "No external data source is configured; market facts could not be independently verified.");
    }

    @Test
    void rejectsResponsesWithMissingStructuredFields() {
        assertThatThrownBy(() -> normalizer.normalize(
                        new ResearchAiResponse("Summary", null, List.of()),
                        List.of()))
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("required structured fields");
    }

    @Test
    void doesNotAcceptVerifiedEvidenceWithoutRequiredProvenance() {
        ResearchEvidence incomplete = new ResearchEvidence(
                "source-1", "source", "market", "instrument", "price", "", null, null, null, null, true);

        assertThatThrownBy(() -> normalizer.filterVerifiedEvidence(List.of(incomplete)))
                .isInstanceOf(InvalidAiResponseException.class)
                .hasMessageContaining("provenance");
    }

    @Test
    void rejectsEvidenceForAnotherInstrument() {
        ResearchRequest request = new ResearchRequest("EU", "instrument-1", "objective", null, null, true);
        ResearchEvidence otherInstrument = new ResearchEvidence(
                "source-1", "source", "EU", "instrument-2", "reference",
                "value", null, null, "2026-01-01T00:00:00Z", null, true);

        assertThatThrownBy(() -> normalizer.filterVerifiedEvidence(request, List.of(otherInstrument)))
                .isInstanceOf(InvalidResearchEvidenceException.class)
                .hasMessageContaining("different market or instrument");
    }
}
