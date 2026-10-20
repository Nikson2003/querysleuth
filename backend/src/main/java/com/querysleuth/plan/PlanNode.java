package com.querysleuth.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single node in a PostgreSQL execution plan tree.
 */
public class PlanNode {
    private String id;
    private int depth;
    private String nodeType;
    private String relationName;
    private String schema;
    private String alias;
    private String indexName;

    // Cost estimates
    private Double startupCost;
    private Double totalCost;
    private Double planRows;
    private Integer planWidth;

    // Actual runtime measurements (when ANALYZE was run)
    private Double actualStartupTime;
    private Double actualTotalTime;
    private Double actualRows;
    private Long actualLoops;

    // Filters and conditions
    private String filter;
    private Long rowsRemovedByFilter;
    private String indexCond;
    private String hashCond;
    private String joinType;
    private String joinFilter;
    private Long rowsRemovedByJoinFilter;

    // Sort metrics
    private List<String> sortKey = new ArrayList<>();
    private String sortMethod;
    private Long sortSpaceUsed;
    private String sortSpaceType;

    // Buffer metrics (when BUFFERS was run)
    private Long sharedHitBlocks;
    private Long sharedReadBlocks;
    private Long sharedDirtiedBlocks;
    private Long sharedWrittenBlocks;
    private Long localHitBlocks;
    private Long localReadBlocks;
    private Long localDirtiedBlocks;
    private Long localWrittenBlocks;
    private Long tempReadBlocks;
    private Long tempWrittenBlocks;

    private String functionCall;
    private String outputColumns;

    // Computed / derived metrics
    private Double exclusiveTimeMs;

    // Child nodes in the execution tree
    private List<PlanNode> plans = new ArrayList<>();

    // Unmapped properties for advanced inspection
    private Map<String, Object> rawProperties = new HashMap<>();

    public PlanNode() {
    }

    public PlanNode(String id, String nodeType) {
        this.id = id;
        this.nodeType = nodeType;
    }

    public void addChild(PlanNode child) {
        if (child != null) {
            this.plans.add(child);
        }
    }

    public boolean hasRuntimeMetrics() {
        return actualTotalTime != null && actualRows != null;
    }

    public boolean hasBufferMetrics() {
        return (sharedHitBlocks != null && sharedHitBlocks > 0) ||
               (sharedReadBlocks != null && sharedReadBlocks > 0) ||
               (localHitBlocks != null && localHitBlocks > 0) ||
               (localReadBlocks != null && localReadBlocks > 0) ||
               (tempReadBlocks != null && tempReadBlocks > 0) ||
               (tempWrittenBlocks != null && tempWrittenBlocks > 0);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getRelationName() { return relationName; }
    public void setRelationName(String relationName) { this.relationName = relationName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public Double getStartupCost() { return startupCost; }
    public void setStartupCost(Double startupCost) { this.startupCost = startupCost; }

    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }

    public Double getPlanRows() { return planRows; }
    public void setPlanRows(Double planRows) { this.planRows = planRows; }

    public Integer getPlanWidth() { return planWidth; }
    public void setPlanWidth(Integer planWidth) { this.planWidth = planWidth; }

    public Double getActualStartupTime() { return actualStartupTime; }
    public void setActualStartupTime(Double actualStartupTime) { this.actualStartupTime = actualStartupTime; }

    public Double getActualTotalTime() { return actualTotalTime; }
    public void setActualTotalTime(Double actualTotalTime) { this.actualTotalTime = actualTotalTime; }

    public Double getActualRows() { return actualRows; }
    public void setActualRows(Double actualRows) { this.actualRows = actualRows; }

    public Long getActualLoops() { return actualLoops; }
    public void setActualLoops(Long actualLoops) { this.actualLoops = actualLoops; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    public Long getRowsRemovedByFilter() { return rowsRemovedByFilter; }
    public void setRowsRemovedByFilter(Long rowsRemovedByFilter) { this.rowsRemovedByFilter = rowsRemovedByFilter; }

    public String getIndexCond() { return indexCond; }
    public void setIndexCond(String indexCond) { this.indexCond = indexCond; }

    public String getHashCond() { return hashCond; }
    public void setHashCond(String hashCond) { this.hashCond = hashCond; }

    public String getJoinType() { return joinType; }
    public void setJoinType(String joinType) { this.joinType = joinType; }

    public String getJoinFilter() { return joinFilter; }
    public void setJoinFilter(String joinFilter) { this.joinFilter = joinFilter; }

    public Long getRowsRemovedByJoinFilter() { return rowsRemovedByJoinFilter; }
    public void setRowsRemovedByJoinFilter(Long rowsRemovedByJoinFilter) { this.rowsRemovedByJoinFilter = rowsRemovedByJoinFilter; }

    public List<String> getSortKey() { return sortKey; }
    public void setSortKey(List<String> sortKey) { this.sortKey = sortKey; }

    public String getSortMethod() { return sortMethod; }
    public void setSortMethod(String sortMethod) { this.sortMethod = sortMethod; }

    public Long getSortSpaceUsed() { return sortSpaceUsed; }
    public void setSortSpaceUsed(Long sortSpaceUsed) { this.sortSpaceUsed = sortSpaceUsed; }

    public String getSortSpaceType() { return sortSpaceType; }
    public void setSortSpaceType(String sortSpaceType) { this.sortSpaceType = sortSpaceType; }

    public Long getSharedHitBlocks() { return sharedHitBlocks; }
    public void setSharedHitBlocks(Long sharedHitBlocks) { this.sharedHitBlocks = sharedHitBlocks; }

    public Long getSharedReadBlocks() { return sharedReadBlocks; }
    public void setSharedReadBlocks(Long sharedReadBlocks) { this.sharedReadBlocks = sharedReadBlocks; }

    public Long getSharedDirtiedBlocks() { return sharedDirtiedBlocks; }
    public void setSharedDirtiedBlocks(Long sharedDirtiedBlocks) { this.sharedDirtiedBlocks = sharedDirtiedBlocks; }

    public Long getSharedWrittenBlocks() { return sharedWrittenBlocks; }
    public void setSharedWrittenBlocks(Long sharedWrittenBlocks) { this.sharedWrittenBlocks = sharedWrittenBlocks; }

    public Long getTempReadBlocks() { return tempReadBlocks; }
    public void setTempReadBlocks(Long tempReadBlocks) { this.tempReadBlocks = tempReadBlocks; }

    public Long getTempWrittenBlocks() { return tempWrittenBlocks; }
    public void setTempWrittenBlocks(Long tempWrittenBlocks) { this.tempWrittenBlocks = tempWrittenBlocks; }

    public Double getExclusiveTimeMs() { return exclusiveTimeMs; }
    public void setExclusiveTimeMs(Double exclusiveTimeMs) { this.exclusiveTimeMs = exclusiveTimeMs; }

    public List<PlanNode> getPlans() { return plans; }
    public void setPlans(List<PlanNode> plans) { this.plans = plans; }

    public Long getLocalHitBlocks() { return localHitBlocks; }
    public void setLocalHitBlocks(Long localHitBlocks) { this.localHitBlocks = localHitBlocks; }

    public Long getLocalReadBlocks() { return localReadBlocks; }
    public void setLocalReadBlocks(Long localReadBlocks) { this.localReadBlocks = localReadBlocks; }

    public Long getLocalDirtiedBlocks() { return localDirtiedBlocks; }
    public void setLocalDirtiedBlocks(Long localDirtiedBlocks) { this.localDirtiedBlocks = localDirtiedBlocks; }

    public Long getLocalWrittenBlocks() { return localWrittenBlocks; }
    public void setLocalWrittenBlocks(Long localWrittenBlocks) { this.localWrittenBlocks = localWrittenBlocks; }

    public String getFunctionCall() { return functionCall; }
    public void setFunctionCall(String functionCall) { this.functionCall = functionCall; }

    public String getOutputColumns() { return outputColumns; }
    public void setOutputColumns(String outputColumns) { this.outputColumns = outputColumns; }

    public Map<String, Object> getRawProperties() { return rawProperties; }
    public void setRawProperties(Map<String, Object> rawProperties) { this.rawProperties = rawProperties; }

    @Override
    public String toString() {
        return "PlanNode{" +
                "id='" + id + '\'' +
                ", nodeType='" + nodeType + '\'' +
                ", relationName='" + relationName + '\'' +
                ", actualTotalTime=" + actualTotalTime +
                ", actualRows=" + actualRows +
                '}';
    }
}
