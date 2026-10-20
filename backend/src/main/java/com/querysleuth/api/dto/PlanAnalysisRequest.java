package com.querysleuth.api.dto;

import jakarta.validation.constraints.NotBlank;

public class PlanAnalysisRequest {

    @NotBlank(message = "Plan JSON must not be blank")
    private String planJson;

    public PlanAnalysisRequest() {
    }

    public PlanAnalysisRequest(String planJson) {
        this.planJson = planJson;
    }

    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
}
