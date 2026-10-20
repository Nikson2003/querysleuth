package com.querysleuth.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Jackson-based parser for PostgreSQL EXPLAIN JSON execution plans.
 * Supports standard arrays, wrapper objects, and bare plan root nodes.
 */
@Component
public class PlanParser {

    private static final Logger log = LoggerFactory.getLogger(PlanParser.class);
    private final ObjectMapper objectMapper;
    private final TextPlanParser textPlanParser;

    public PlanParser(ObjectMapper objectMapper, TextPlanParser textPlanParser) {
        this.objectMapper = objectMapper;
        this.textPlanParser = textPlanParser;
    }

    public static class ParseResult {
        private final PlanNode rootNode;
        private final PlanSummary summary;

        public ParseResult(PlanNode rootNode, PlanSummary summary) {
            this.rootNode = rootNode;
            this.summary = summary;
        }

        public PlanNode getRootNode() { return rootNode; }
        public PlanSummary getSummary() { return summary; }
    }

    /**
     * Parses an execution plan (in PostgreSQL JSON format or standard text format)
     * into a PlanNode tree and PlanSummary.
     *
     * @param planText raw plan text or JSON string
     * @return ParseResult containing the root PlanNode and summary metrics
     */
    public ParseResult parse(String planText) {
        if (planText == null || planText.trim().isEmpty()) {
            throw new IllegalArgumentException("Execution plan input cannot be null or empty.");
        }

        String trimmed = planText.trim();

        // 1. If it looks like JSON, attempt JSON parsing first
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return parseJson(trimmed);
            } catch (Exception e) {
                // If it failed JSON parsing, but looks like text plan, fall back to text parser
                if (TextPlanParser.isTextPlan(trimmed)) {
                    log.info("JSON parsing failed, falling back to text plan parser.");
                    return textPlanParser.parse(trimmed);
                }
                throw new IllegalArgumentException("Failed to parse JSON execution plan: " + e.getMessage(), e);
            }
        }

        // 2. Otherwise check if it is a text-based EXPLAIN plan
        if (TextPlanParser.isTextPlan(trimmed)) {
            return textPlanParser.parse(trimmed);
        }

        throw new IllegalArgumentException(
                "Unrecognized execution plan format. Please provide output from either:\n" +
                "1. 'EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) <query>;' (JSON format)\n" +
                "2. 'EXPLAIN (ANALYZE) <query>;' (Standard text format)"
        );
    }

    private ParseResult parseJson(String jsonText) throws JsonProcessingException {
        JsonNode rootJson = objectMapper.readTree(jsonText);

        JsonNode targetPlanWrapper = rootJson;
        if (targetPlanWrapper.isArray() && targetPlanWrapper.size() > 0) {
            targetPlanWrapper = targetPlanWrapper.get(0);
        }

        Double planningTime = null;
        Double executionTime = null;

        if (targetPlanWrapper.has("Planning Time")) {
            planningTime = targetPlanWrapper.get("Planning Time").asDouble();
        } else if (targetPlanWrapper.has("Planning time")) {
            planningTime = targetPlanWrapper.get("Planning time").asDouble();
        }

        if (targetPlanWrapper.has("Execution Time")) {
            executionTime = targetPlanWrapper.get("Execution Time").asDouble();
        } else if (targetPlanWrapper.has("Execution time")) {
            executionTime = targetPlanWrapper.get("Execution time").asDouble();
        }

        JsonNode planNodeJson;
        if (targetPlanWrapper.has("Plan")) {
            planNodeJson = targetPlanWrapper.get("Plan");
        } else if (targetPlanWrapper.has("Node Type")) {
            planNodeJson = targetPlanWrapper;
        } else {
            throw new IllegalArgumentException("Invalid PostgreSQL plan format: Could not find 'Plan' or 'Node Type' element.");
        }

        AtomicInteger idGenerator = new AtomicInteger(0);
        PlanNode rootNode = parseNodeRecursively(planNodeJson, 0, idGenerator);

        // Build PlanSummary
        PlanSummary summary = new PlanSummary();
        summary.setPlanningTimeMs(planningTime);
        summary.setExecutionTimeMs(executionTime != null ? executionTime : (rootNode.getActualTotalTime()));
        summary.setTotalCost(rootNode.getTotalCost());

        calculateSummaryMetrics(rootNode, summary);

        return new ParseResult(rootNode, summary);
    }

    private PlanNode parseNodeRecursively(JsonNode json, int depth, AtomicInteger idGen) {
        PlanNode node = new PlanNode();
        node.setId("node-" + idGen.getAndIncrement());
        node.setDepth(depth);

        // Standard identifiers
        node.setNodeType(getText(json, "Node Type", "Unknown"));
        node.setRelationName(getText(json, "Relation Name", null));
        node.setSchema(getText(json, "Schema", null));
        node.setAlias(getText(json, "Alias", null));
        node.setIndexName(getText(json, "Index Name", null));

        // Cost estimates
        node.setStartupCost(getDouble(json, "Startup Cost"));
        node.setTotalCost(getDouble(json, "Total Cost"));
        node.setPlanRows(getDouble(json, "Plan Rows"));
        node.setPlanWidth(getInt(json, "Plan Width"));

        // Runtime measurements
        node.setActualStartupTime(getDouble(json, "Actual Startup Time"));
        node.setActualTotalTime(getDouble(json, "Actual Total Time"));
        node.setActualRows(getDouble(json, "Actual Rows"));
        node.setActualLoops(getLong(json, "Actual Loops"));

        // Filters and conditions
        node.setFilter(getText(json, "Filter", null));
        node.setRowsRemovedByFilter(getLong(json, "Rows Removed by Filter"));
        node.setIndexCond(getText(json, "Index Cond", null));
        node.setHashCond(getText(json, "Hash Cond", null));
        node.setJoinType(getText(json, "Join Type", null));
        node.setJoinFilter(getText(json, "Join Filter", null));
        node.setRowsRemovedByJoinFilter(getLong(json, "Rows Removed by Join Filter"));

        // Sort properties
        if (json.has("Sort Key") && json.get("Sort Key").isArray()) {
            List<String> sortKeys = new ArrayList<>();
            for (JsonNode key : json.get("Sort Key")) {
                sortKeys.add(key.asText());
            }
            node.setSortKey(sortKeys);
        }
        node.setSortMethod(getText(json, "Sort Method", null));
        node.setSortSpaceUsed(getLong(json, "Sort Space Used"));
        node.setSortSpaceType(getText(json, "Sort Space Type", null));

        // Buffers
        node.setSharedHitBlocks(getLong(json, "Shared Hit Blocks"));
        node.setSharedReadBlocks(getLong(json, "Shared Read Blocks"));
        node.setSharedDirtiedBlocks(getLong(json, "Shared Dirtied Blocks"));
        node.setSharedWrittenBlocks(getLong(json, "Shared Written Blocks"));
        node.setTempReadBlocks(getLong(json, "Temp Read Blocks"));
        node.setTempWrittenBlocks(getLong(json, "Temp Written Blocks"));

        // Track unmapped/extra properties for deep developer inspection
        Set<String> mappedKeys = Set.of(
                "Node Type", "Relation Name", "Schema", "Alias", "Index Name",
                "Startup Cost", "Total Cost", "Plan Rows", "Plan Width",
                "Actual Startup Time", "Actual Total Time", "Actual Rows", "Actual Loops",
                "Filter", "Rows Removed by Filter", "Index Cond", "Hash Cond", "Join Type",
                "Join Filter", "Rows Removed by Join Filter", "Sort Key", "Sort Method",
                "Sort Space Used", "Sort Space Type", "Shared Hit Blocks", "Shared Read Blocks",
                "Shared Dirtied Blocks", "Shared Written Blocks", "Temp Read Blocks", "Temp Written Blocks",
                "Plans"
        );

        Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!mappedKeys.contains(entry.getKey())) {
                node.getRawProperties().put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
            }
        }

        // Child Plans
        if (json.has("Plans") && json.get("Plans").isArray()) {
            double childrenTotalTime = 0.0;
            boolean hasChildTimings = false;

            for (JsonNode childJson : json.get("Plans")) {
                PlanNode child = parseNodeRecursively(childJson, depth + 1, idGen);
                node.addChild(child);

                if (child.getActualTotalTime() != null) {
                    hasChildTimings = true;
                    // Note: child actual total time is per-loop, so we multiply by actual loops if present
                    long loops = child.getActualLoops() != null ? child.getActualLoops() : 1L;
                    childrenTotalTime += (child.getActualTotalTime() * loops);
                }
            }

            // Calculate Exclusive Time for this node
            if (node.getActualTotalTime() != null) {
                long currentLoops = node.getActualLoops() != null ? node.getActualLoops() : 1L;
                double totalObservedTime = node.getActualTotalTime() * currentLoops;
                if (hasChildTimings) {
                    double exclusive = totalObservedTime - childrenTotalTime;
                    node.setExclusiveTimeMs(Math.max(0.0, Math.round(exclusive * 1000.0) / 1000.0));
                } else {
                    node.setExclusiveTimeMs(node.getActualTotalTime());
                }
            }
        } else {
            // Leaf node: exclusive time is its own actual total time
            node.setExclusiveTimeMs(node.getActualTotalTime());
        }

        return node;
    }

    private void calculateSummaryMetrics(PlanNode rootNode, PlanSummary summary) {
        int[] nodeCount = new int[]{0};
        double[] maxRows = new double[]{0.0};
        String[] maxRowsNodeId = new String[]{null};
        double[] maxExclusiveTime = new double[]{0.0};
        String[] dominantNodeId = new String[]{null};
        long[] totalHit = new long[]{0};
        long[] totalRead = new long[]{0};
        long[] totalSharedDirtied = new long[]{0};
        long[] totalSharedWritten = new long[]{0};
        long[] totalLocalHit = new long[]{0};
        long[] totalLocalRead = new long[]{0};
        long[] totalLocalDirtied = new long[]{0};
        long[] totalLocalWritten = new long[]{0};
        long[] totalTempRead = new long[]{0};
        long[] totalTempWritten = new long[]{0};
        boolean[] hasSpill = new boolean[]{false};
        boolean[] hasAnalyze = new boolean[]{false};
        boolean[] hasBuffers = new boolean[]{false};

        traverse(rootNode, node -> {
            nodeCount[0]++;

            if (node.getActualRows() != null) {
                hasAnalyze[0] = true;
                if (node.getActualRows() > maxRows[0]) {
                    maxRows[0] = node.getActualRows();
                    maxRowsNodeId[0] = node.getId();
                }
            }

            if (node.getExclusiveTimeMs() != null) {
                if (node.getExclusiveTimeMs() > maxExclusiveTime[0]) {
                    maxExclusiveTime[0] = node.getExclusiveTimeMs();
                    dominantNodeId[0] = node.getId();
                }
            }

            if (node.getSharedHitBlocks() != null) {
                hasBuffers[0] = true;
                totalHit[0] += node.getSharedHitBlocks();
            }
            if (node.getSharedReadBlocks() != null) {
                hasBuffers[0] = true;
                totalRead[0] += node.getSharedReadBlocks();
            }
            if (node.getSharedDirtiedBlocks() != null) {
                hasBuffers[0] = true;
                totalSharedDirtied[0] += node.getSharedDirtiedBlocks();
            }
            if (node.getSharedWrittenBlocks() != null) {
                hasBuffers[0] = true;
                totalSharedWritten[0] += node.getSharedWrittenBlocks();
            }
            if (node.getLocalHitBlocks() != null) {
                hasBuffers[0] = true;
                totalLocalHit[0] += node.getLocalHitBlocks();
            }
            if (node.getLocalReadBlocks() != null) {
                hasBuffers[0] = true;
                totalLocalRead[0] += node.getLocalReadBlocks();
            }
            if (node.getLocalDirtiedBlocks() != null) {
                hasBuffers[0] = true;
                totalLocalDirtied[0] += node.getLocalDirtiedBlocks();
            }
            if (node.getLocalWrittenBlocks() != null) {
                hasBuffers[0] = true;
                totalLocalWritten[0] += node.getLocalWrittenBlocks();
            }
            if (node.getTempReadBlocks() != null) {
                totalTempRead[0] += node.getTempReadBlocks();
            }
            if (node.getTempWrittenBlocks() != null) {
                totalTempWritten[0] += node.getTempWrittenBlocks();
            }

            if ("Disk".equalsIgnoreCase(node.getSortSpaceType()) ||
                    (node.getTempWrittenBlocks() != null && node.getTempWrittenBlocks() > 0) ||
                    (node.getLocalWrittenBlocks() != null && node.getLocalWrittenBlocks() > 0)) {
                hasSpill[0] = true;
            }
        });

        summary.setTotalNodes(nodeCount[0]);
        summary.setMaxActualRows(maxRows[0]);
        summary.setMaxActualRowsNodeId(maxRowsNodeId[0]);
        summary.setMaxExclusiveTimeMs(maxExclusiveTime[0]);
        summary.setDominantNodeId(dominantNodeId[0]);
        summary.setTotalSharedHitBlocks(totalHit[0]);
        summary.setTotalSharedReadBlocks(totalRead[0]);
        summary.setTotalSharedDirtiedBlocks(totalSharedDirtied[0]);
        summary.setTotalSharedWrittenBlocks(totalSharedWritten[0]);
        summary.setTotalLocalHitBlocks(totalLocalHit[0]);
        summary.setTotalLocalReadBlocks(totalLocalRead[0]);
        summary.setTotalLocalDirtiedBlocks(totalLocalDirtied[0]);
        summary.setTotalLocalWrittenBlocks(totalLocalWritten[0]);
        summary.setTotalTempReadBlocks(totalTempRead[0]);
        summary.setTotalTempWrittenBlocks(totalTempWritten[0]);
        summary.setHasDiskSpill(hasSpill[0]);
        summary.setAnalyzeIncluded(hasAnalyze[0]);
        summary.setBuffersIncluded(hasBuffers[0]);
    }

    private void traverse(PlanNode node, java.util.function.Consumer<PlanNode> consumer) {
        if (node == null) return;
        consumer.accept(node);
        for (PlanNode child : node.getPlans()) {
            traverse(child, consumer);
        }
    }

    private String getText(JsonNode json, String field, String defaultValue) {
        return json.has(field) && !json.get(field).isNull() ? json.get(field).asText() : defaultValue;
    }

    private Double getDouble(JsonNode json, String field) {
        return json.has(field) && !json.get(field).isNull() ? json.get(field).asDouble() : null;
    }

    private Long getLong(JsonNode json, String field) {
        return json.has(field) && !json.get(field).isNull() ? json.get(field).asLong() : null;
    }

    private Integer getInt(JsonNode json, String field) {
        return json.has(field) && !json.get(field).isNull() ? json.get(field).asInt() : null;
    }
}
