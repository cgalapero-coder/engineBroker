package com.gtech.automaticbroker.enginebroker.service;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import java.util.List;

public interface ResearchSourceAdapter {

    List<ResearchEvidence> collect(ResearchRequest request);
}
