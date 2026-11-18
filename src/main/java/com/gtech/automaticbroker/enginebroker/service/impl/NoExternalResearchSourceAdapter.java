package com.gtech.automaticbroker.enginebroker.service.impl;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchEvidence;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.service.ResearchSourceAdapter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NoExternalResearchSourceAdapter implements ResearchSourceAdapter {

    @Override
    public List<ResearchEvidence> collect(ResearchRequest request) {
        return List.of();
    }
}
