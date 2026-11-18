package com.gtech.automaticbroker.enginebroker.service;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchAiResponse;

public interface ResearchAiProvider {

    ResearchAiResponse research(ResearchAiRequest request);

    String providerName();

    String modelName();
}
