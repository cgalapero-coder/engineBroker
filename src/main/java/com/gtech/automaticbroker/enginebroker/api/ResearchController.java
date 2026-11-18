package com.gtech.automaticbroker.enginebroker.api;

import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchRequest;
import com.gtech.automaticbroker.enginebroker.domain.entity.ResearchResult;
import com.gtech.automaticbroker.enginebroker.service.InvestmentResearchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {

    private final InvestmentResearchService researchService;

    public ResearchController(InvestmentResearchService researchService) {
        this.researchService = researchService;
    }

    @PostMapping
    public ResponseEntity<ResearchResult> research(@Valid @RequestBody ResearchRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(researchService.investigate(request));
    }
}
