package com.gtech.automaticbroker.enginebroker.domain.entity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResearchRequest(
        @NotBlank @Size(max = 100) String market,
        @NotBlank @Size(max = 200) String instrument,
        @NotBlank @Size(max = 2000) String objective,
        @Size(max = 200) String userId,
        @Size(max = 100) String userRole,
        @NotNull @AssertTrue Boolean simulation) {
}
