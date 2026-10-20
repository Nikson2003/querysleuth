package com.querysleuth.plan;

/**
 * High-level summary metrics calculated across the entire execution plan.
 */
public class PlanSummary {
    private Double planningTimeMs;
    private Double executionTimeMs;
    private Double totalCost;
    private int totalNodes;
    private Double maxActualRows;
    private String maxActualRowsNodeId;
    private Double maxExclusiveTimeMs;
    private String dominantNodeId;
    private long totalSharedHitBlocks;
    private long totalSharedReadBlocks;
    private long totalSharedDirtiedBlocks;
    private long totalSharedWrittenBlocks;
    private long totalLocalHitBlocks;
    private long totalLocalReadBlocks;
    private long totalLocalDirtiedBlocks;
    private long totalLocalWrittenBlocks;
    private long totalTempReadBlocks;
    private long totalTempWrittenBlocks;
    private boolean hasDiskSpill;
    private boolean analyzeIncluded;
    private boolean buffersIncluded;

    public PlanSummary() {
    }

    public Double getPlanningTimeMs() { return planningTimeMs; }
    public void setPlanningTimeMs(Double planningTimeMs) { this.planningTimeMs = planningTimeMs; }

    public Double getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(Double executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }

    public int getTotalNodes() { return totalNodes; }
    public void setTotalNodes(int totalNodes) { this.totalNodes = totalNodes; }

    public Double getMaxActualRows() { return maxActualRows; }
    public void setMaxActualRows(Double maxActualRows) { this.maxActualRows = maxActualRows; }

    public String getMaxActualRowsNodeId() { return maxActualRowsNodeId; }
    public void setMaxActualRowsNodeId(String maxActualRowsNodeId) { this.maxActualRowsNodeId = maxActualRowsNodeId; }

    public Double getMaxExclusiveTimeMs() { return maxExclusiveTimeMs; }
    public void setMaxExclusiveTimeMs(Double maxExclusiveTimeMs) { this.maxExclusiveTimeMs = maxExclusiveTimeMs; }

    public String getDominantNodeId() { return dominantNodeId; }
    public void setDominantNodeId(String dominantNodeId) { this.dominantNodeId = dominantNodeId; }

    public long getTotalSharedHitBlocks() { return totalSharedHitBlocks; }
    public void setTotalSharedHitBlocks(long totalSharedHitBlocks) { this.totalSharedHitBlocks = totalSharedHitBlocks; }

    public long getTotalSharedReadBlocks() { return totalSharedReadBlocks; }
    public void setTotalSharedReadBlocks(long totalSharedReadBlocks) { this.totalSharedReadBlocks = totalSharedReadBlocks; }

    public long getTotalSharedDirtiedBlocks() { return totalSharedDirtiedBlocks; }
    public void setTotalSharedDirtiedBlocks(long totalSharedDirtiedBlocks) { this.totalSharedDirtiedBlocks = totalSharedDirtiedBlocks; }

    public long getTotalSharedWrittenBlocks() { return totalSharedWrittenBlocks; }
    public void setTotalSharedWrittenBlocks(long totalSharedWrittenBlocks) { this.totalSharedWrittenBlocks = totalSharedWrittenBlocks; }

    public long getTotalLocalHitBlocks() { return totalLocalHitBlocks; }
    public void setTotalLocalHitBlocks(long totalLocalHitBlocks) { this.totalLocalHitBlocks = totalLocalHitBlocks; }

    public long getTotalLocalReadBlocks() { return totalLocalReadBlocks; }
    public void setTotalLocalReadBlocks(long totalLocalReadBlocks) { this.totalLocalReadBlocks = totalLocalReadBlocks; }

    public long getTotalLocalDirtiedBlocks() { return totalLocalDirtiedBlocks; }
    public void setTotalLocalDirtiedBlocks(long totalLocalDirtiedBlocks) { this.totalLocalDirtiedBlocks = totalLocalDirtiedBlocks; }

    public long getTotalLocalWrittenBlocks() { return totalLocalWrittenBlocks; }
    public void setTotalLocalWrittenBlocks(long totalLocalWrittenBlocks) { this.totalLocalWrittenBlocks = totalLocalWrittenBlocks; }

    public long getTotalTempReadBlocks() { return totalTempReadBlocks; }
    public void setTotalTempReadBlocks(long totalTempReadBlocks) { this.totalTempReadBlocks = totalTempReadBlocks; }

    public long getTotalTempWrittenBlocks() { return totalTempWrittenBlocks; }
    public void setTotalTempWrittenBlocks(long totalTempWrittenBlocks) { this.totalTempWrittenBlocks = totalTempWrittenBlocks; }

    public boolean isHasDiskSpill() { return hasDiskSpill; }
    public void setHasDiskSpill(boolean hasDiskSpill) { this.hasDiskSpill = hasDiskSpill; }

    public boolean isAnalyzeIncluded() { return analyzeIncluded; }
    public void setAnalyzeIncluded(boolean analyzeIncluded) { this.analyzeIncluded = analyzeIncluded; }

    public boolean isBuffersIncluded() { return buffersIncluded; }
    public void setBuffersIncluded(boolean buffersIncluded) { this.buffersIncluded = buffersIncluded; }
}
