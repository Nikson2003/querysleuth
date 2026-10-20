package com.querysleuth.common;

import java.util.Objects;

/**
 * Standard structured finding produced by QuerySleuth analysis engines.
 */
public class Finding {
    private String id;
    private FindingCategory category;
    private Severity severity;
    private Confidence confidence;
    private String title;
    private String evidence;
    private String explanation;
    private String suggestedNextStep;
    private String targetNodeId;
    private String targetLocation;
    private String ruleId;

    public Finding() {
    }

    public Finding(String id, FindingCategory category, Severity severity, Confidence confidence,
                   String title, String evidence, String explanation, String suggestedNextStep,
                   String targetNodeId, String targetLocation, String ruleId) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.confidence = confidence;
        this.title = title;
        this.evidence = evidence;
        this.explanation = explanation;
        this.suggestedNextStep = suggestedNextStep;
        this.targetNodeId = targetNodeId;
        this.targetLocation = targetLocation;
        this.ruleId = ruleId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private FindingCategory category = FindingCategory.PERFORMANCE;
        private Severity severity = Severity.MEDIUM;
        private Confidence confidence = Confidence.MEDIUM;
        private String title;
        private String evidence;
        private String explanation;
        private String suggestedNextStep;
        private String targetNodeId;
        private String targetLocation;
        private String ruleId;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder category(FindingCategory category) {
            this.category = category;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder confidence(Confidence confidence) {
            this.confidence = confidence;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder evidence(String evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder explanation(String explanation) {
            this.explanation = explanation;
            return this;
        }

        public Builder suggestedNextStep(String suggestedNextStep) {
            this.suggestedNextStep = suggestedNextStep;
            return this;
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public Builder targetLocation(String targetLocation) {
            this.targetLocation = targetLocation;
            return this;
        }

        public Builder ruleId(String ruleId) {
            this.ruleId = ruleId;
            return this;
        }

        public Finding build() {
            return new Finding(id, category, severity, confidence, title, evidence,
                    explanation, suggestedNextStep, targetNodeId, targetLocation, ruleId);
        }
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public FindingCategory getCategory() { return category; }
    public void setCategory(FindingCategory category) { this.category = category; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Confidence getConfidence() { return confidence; }
    public void setConfidence(Confidence confidence) { this.confidence = confidence; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getSuggestedNextStep() { return suggestedNextStep; }
    public void setSuggestedNextStep(String suggestedNextStep) { this.suggestedNextStep = suggestedNextStep; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getTargetLocation() { return targetLocation; }
    public void setTargetLocation(String targetLocation) { this.targetLocation = targetLocation; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Finding finding)) return false;
        return Objects.equals(id, finding.id) && Objects.equals(ruleId, finding.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ruleId);
    }

    @Override
    public String toString() {
        return "Finding{" +
                "id='" + id + '\'' +
                ", severity=" + severity +
                ", title='" + title + '\'' +
                ", targetNodeId='" + targetNodeId + '\'' +
                '}';
    }
}
