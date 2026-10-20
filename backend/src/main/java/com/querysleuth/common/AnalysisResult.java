package com.querysleuth.common;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Universal analysis result envelope returned across QuerySleuth modules.
 */
public class AnalysisResult {
    private String module;
    private Instant timestamp = Instant.now();
    private List<Finding> findings = new ArrayList<>();
    private List<String> limitations = new ArrayList<>();
    private Map<String, Object> summary = new HashMap<>();
    private Map<String, Object> metadata = new HashMap<>();

    public AnalysisResult() {
    }

    public AnalysisResult(String module) {
        this.module = module;
    }

    public void addFinding(Finding finding) {
        this.findings.add(finding);
    }

    public void addLimitation(String limitation) {
        this.limitations.add(limitation);
    }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public List<Finding> getFindings() { return findings; }
    public void setFindings(List<Finding> findings) { this.findings = findings; }

    public List<String> getLimitations() { return limitations; }
    public void setLimitations(List<String> limitations) { this.limitations = limitations; }

    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
