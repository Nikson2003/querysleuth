package com.querysleuth.api.dto;

import com.querysleuth.common.Finding;
import com.querysleuth.plan.PlanNode;
import com.querysleuth.plan.PlanSummary;

import java.util.List;
import java.util.Map;

public class PlanAnalysisResponse {
    private String status;
    private PlanSummary summary;
    private PlanNode planTree;
    private List<Finding> findings;
    private List<String> limitations;
    private Map<String, Object> metrics;

    public PlanAnalysisResponse() {
    }

    public PlanAnalysisResponse(String status, PlanSummary summary, PlanNode planTree,
                                List<Finding> findings, List<String> limitations, Map<String, Object> metrics) {
        this.status = status;
        this.summary = summary;
        this.planTree = planTree;
        this.findings = findings;
        this.limitations = limitations;
        this.metrics = metrics;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public PlanSummary getSummary() { return summary; }
    public void setSummary(PlanSummary summary) { this.summary = summary; }

    public PlanNode getPlanTree() { return planTree; }
    public void setPlanTree(PlanNode planTree) { this.planTree = planTree; }

    public List<Finding> getFindings() { return findings; }
    public void setFindings(List<Finding> findings) { this.findings = findings; }

    public List<String> getLimitations() { return limitations; }
    public void setLimitations(List<String> limitations) { this.limitations = limitations; }

    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
}
