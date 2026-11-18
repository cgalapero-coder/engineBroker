package com.gtech.automaticbroker.enginebroker.service;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchResult;

public interface InvestmentResearchService {

    ResearchResult investigate(ResearchRequest request);
}
