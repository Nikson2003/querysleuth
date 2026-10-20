package com.querysleuth.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for standard PostgreSQL text execution plans (from EXPLAIN or EXPLAIN ANALYZE).
 */
@Component
public class TextPlanParser {

    private static final Logger log = LoggerFactory.getLogger(TextPlanParser.class);

    private static final Pattern COST_PATTERN = Pattern.compile(
            "\\(cost=([\\d.]+)\\.\\.([\\d.]+)\\s+rows=([\\d.]+)\\s+width=(\\d+)\\)"
    );

    private static final Pattern ACTUAL_PATTERN = Pattern.compile(
            "\\(actual(?:\\s+time=([\\d.]+)\\.\\.([\\d.]+))?\\s+rows=([\\d.]+)\\s+loops=(\\d+)\\)"
    );

    private static final Pattern PLANNING_TIME_PATTERN = Pattern.compile(
            "Planning\\s+[Tt]ime:\\s+([\\d.]+)\\s*ms"
    );

    private static final Pattern EXECUTION_TIME_PATTERN = Pattern.compile(
            "Execution\\s+[Tt]ime:\\s+([\\d.]+)\\s*ms"
    );

    private static final Pattern ROWS_REMOVED_PATTERN = Pattern.compile(
            "Rows Removed by (?:Filter|Join Filter):\\s*(\\d+)"
    );

    private static final Pattern SORT_METHOD_PATTERN = Pattern.compile(
            "Sort Method:\\s+([^\\s]+(?:\\s+[^\\s]+)?)(?:\\s+(Memory|Disk):\\s*(\\d+)kB)?"
    );

    private static final Pattern BUFFERS_PATTERN = Pattern.compile(
            "Buffers:\\s*(.*)"
    );

    private static class NodeContext {
        final int indent;
        final PlanNode node;

        NodeContext(int indent, PlanNode node) {
            this.indent = indent;
            this.node = node;
        }
    }

    /**
     * Checks if the given text appears to be a PostgreSQL text execution plan.
     */
    public static boolean isTextPlan(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String lower = text.toLowerCase();
        return lower.contains("cost=") ||
                lower.contains("execution time:") ||
                lower.contains("planning time:") ||
                lower.contains("seq scan") ||
                lower.contains("index scan") ||
                lower.contains("function scan") ||
                lower.contains("nested loop");
    }

    /**
     * Parses standard PostgreSQL text execution plan into a PlanParser.ParseResult.
     */
    public PlanParser.ParseResult parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Plan text cannot be empty.");
        }

        String[] lines = text.split("\\r?\\n");
        AtomicInteger idGen = new AtomicInteger(0);

        PlanNode rootNode = null;
        Deque<NodeContext> stack = new ArrayDeque<>();
        Double planningTime = null;
        Double executionTime = null;

        String currentMultilineKey = null;
        StringBuilder multilineBuilder = new StringBuilder();
        PlanNode currentMultilineNode = null;

        for (String rawLine : lines) {
            if (rawLine.trim().isEmpty()) continue;

            // Check for top-level timing metrics
            Matcher planTimeMatcher = PLANNING_TIME_PATTERN.matcher(rawLine);
            if (planTimeMatcher.find()) {
                flushMultiline(currentMultilineKey, multilineBuilder, currentMultilineNode);
                currentMultilineKey = null;
                planningTime = Double.parseDouble(planTimeMatcher.group(1));
                continue;
            }

            Matcher execTimeMatcher = EXECUTION_TIME_PATTERN.matcher(rawLine);
            if (execTimeMatcher.find()) {
                flushMultiline(currentMultilineKey, multilineBuilder, currentMultilineNode);
                currentMultilineKey = null;
                executionTime = Double.parseDouble(execTimeMatcher.group(1));
                continue;
            }

            // Calculate leading indentation
            int indent = 0;
            while (indent < rawLine.length() && rawLine.charAt(indent) == ' ') {
                indent++;
            }

            String content = rawLine.substring(indent);
            boolean isChildNode = content.startsWith("->");
            if (isChildNode) {
                content = content.substring(2).trim();
            }

            // Check if this line is an execution plan node (contains cost=)
            Matcher costMatcher = COST_PATTERN.matcher(content);
            if (costMatcher.find()) {
                flushMultiline(currentMultilineKey, multilineBuilder, currentMultilineNode);
                currentMultilineKey = null;

                PlanNode node = parseNodeLine(content, costMatcher, idGen);

                if (rootNode == null) {
                    rootNode = node;
                    node.setDepth(0);
                    stack.push(new NodeContext(indent, node));
                } else {
                    // Adjust stack according to indentation
                    while (!stack.isEmpty() && stack.peek().indent >= indent) {
                        stack.pop();
                    }

                    if (!stack.isEmpty()) {
                        PlanNode parent = stack.peek().node;
                        node.setDepth(parent.getDepth() + 1);
                        parent.addChild(node);
                    } else {
                        node.setDepth(0);
                    }
                    stack.push(new NodeContext(indent, node));
                }
            } else if (!stack.isEmpty()) {
                PlanNode currentNode = stack.peek().node;

                // Check if this line begins a new known property
                String trimmed = content.trim();
                boolean isNewProperty = isKnownPropertyStart(trimmed);

                if (isNewProperty) {
                    flushMultiline(currentMultilineKey, multilineBuilder, currentMultilineNode);
                    currentMultilineKey = null;

                    if (trimmed.startsWith("Function Call:")) {
                        currentMultilineKey = "Function Call";
                        currentMultilineNode = currentNode;
                        multilineBuilder = new StringBuilder(trimmed.substring(14).trim());
                    } else if (trimmed.startsWith("Output:")) {
                        currentMultilineKey = "Output";
                        currentMultilineNode = currentNode;
                        multilineBuilder = new StringBuilder(trimmed.substring(7).trim());
                    } else {
                        parsePropertyLine(content, currentNode);
                    }
                } else {
                    // Continuation line of multiline property
                    if (currentMultilineKey != null) {
                        multilineBuilder.append("\n").append(trimmed);
                    } else {
                        parsePropertyLine(content, currentNode);
                    }
                }
            }
        }

        flushMultiline(currentMultilineKey, multilineBuilder, currentMultilineNode);

        if (rootNode == null) {
            throw new IllegalArgumentException("Could not detect any valid execution plan nodes in the provided text.");
        }

        // Post-process exclusive time for nodes in tree
        calculateExclusiveTimes(rootNode);

        // Build Summary
        PlanSummary summary = new PlanSummary();
        summary.setPlanningTimeMs(planningTime);
        summary.setExecutionTimeMs(executionTime != null ? executionTime : rootNode.getActualTotalTime());
        summary.setTotalCost(rootNode.getTotalCost());

        calculateSummaryMetrics(rootNode, summary);

        return new PlanParser.ParseResult(rootNode, summary);
    }

    private boolean isKnownPropertyStart(String line) {
        return line.startsWith("Filter:") ||
                line.startsWith("Index Cond:") ||
                line.startsWith("Hash Cond:") ||
                line.startsWith("Join Filter:") ||
                line.startsWith("Sort Key:") ||
                line.startsWith("Sort Method:") ||
                line.startsWith("Buffers:") ||
                line.startsWith("I/O Timings:") ||
                line.startsWith("Settings:") ||
                line.startsWith("Query Identifier:") ||
                line.startsWith("Output:") ||
                line.startsWith("Function Call:") ||
                line.startsWith("Rows Removed by");
    }

    private void flushMultiline(String key, StringBuilder builder, PlanNode node) {
        if (key != null && node != null && builder != null && builder.length() > 0) {
            String value = builder.toString().trim();
            if ("Function Call".equals(key)) {
                node.setFunctionCall(value);
            } else if ("Output".equals(key)) {
                node.setOutputColumns(value);
            }
            node.getRawProperties().put(key, value);
        }
    }

    private PlanNode parseNodeLine(String line, Matcher costMatcher, AtomicInteger idGen) {
        PlanNode node = new PlanNode();
        node.setId("node-" + idGen.getAndIncrement());

        int costStart = costMatcher.start();
        String nodeHeader = line.substring(0, costStart).trim();

        // Parse costs
        node.setStartupCost(Double.parseDouble(costMatcher.group(1)));
        node.setTotalCost(Double.parseDouble(costMatcher.group(2)));
        node.setPlanRows(Double.parseDouble(costMatcher.group(3)));
        node.setPlanWidth(Integer.parseInt(costMatcher.group(4)));

        // Parse actual timings if present
        Matcher actualMatcher = ACTUAL_PATTERN.matcher(line);
        if (actualMatcher.find()) {
            if (actualMatcher.group(1) != null) {
                node.setActualStartupTime(Double.parseDouble(actualMatcher.group(1)));
            }
            if (actualMatcher.group(2) != null) {
                node.setActualTotalTime(Double.parseDouble(actualMatcher.group(2)));
            }
            node.setActualRows(Double.parseDouble(actualMatcher.group(3)));
            node.setActualLoops(Long.parseLong(actualMatcher.group(4)));
        } else if (line.contains("never executed")) {
            node.setActualRows(0.0);
            node.setActualTotalTime(0.0);
            node.setActualLoops(0L);
        }

        // Parse Node Header: e.g.
        // "Function Scan on gettariffmainsearchsinglerow_db"
        // "Seq Scan on tariff t"
        // "Index Scan using idx_tariff_id on tariff t"
        parseNodeHeader(nodeHeader, node);

        return node;
    }

    private void parseNodeHeader(String header, PlanNode node) {
        // Check for " on "
        int onIdx = header.indexOf(" on ");
        if (onIdx != -1) {
            String beforeOn = header.substring(0, onIdx).trim();
            String afterOn = header.substring(onIdx + 4).trim();

            // Extract relation and optional alias: "tariff t" or "public.tariff"
            String[] relParts = afterOn.split("\\s+");
            String rel = relParts[0];
            if (rel.contains(".")) {
                String[] schemaRel = rel.split("\\.");
                node.setSchema(schemaRel[0]);
                node.setRelationName(schemaRel[1]);
            } else {
                node.setRelationName(rel);
            }

            if (relParts.length > 1) {
                node.setAlias(relParts[1]);
            }

            // Check if beforeOn has " using <index>"
            int usingIdx = beforeOn.indexOf(" using ");
            if (usingIdx != -1) {
                node.setNodeType(beforeOn.substring(0, usingIdx).trim());
                node.setIndexName(beforeOn.substring(usingIdx + 7).trim());
            } else {
                node.setNodeType(beforeOn);
            }
        } else {
            // No " on " (e.g. "Nested Loop", "Sort", "Hash Join", "Aggregate")
            int usingIdx = header.indexOf(" using ");
            if (usingIdx != -1) {
                node.setNodeType(header.substring(0, usingIdx).trim());
                node.setIndexName(header.substring(usingIdx + 7).trim());
            } else {
                node.setNodeType(header);
            }
        }
    }

    private void parsePropertyLine(String line, PlanNode node) {
        String trimmed = line.trim();

        if (trimmed.startsWith("Filter:")) {
            node.setFilter(trimmed.substring(7).trim());
        } else if (trimmed.startsWith("Index Cond:")) {
            node.setIndexCond(trimmed.substring(11).trim());
        } else if (trimmed.startsWith("Hash Cond:")) {
            node.setHashCond(trimmed.substring(10).trim());
        } else if (trimmed.startsWith("Join Filter:")) {
            node.setJoinFilter(trimmed.substring(12).trim());
        } else if (trimmed.startsWith("Sort Key:")) {
            String keys = trimmed.substring(9).trim();
            node.setSortKey(Arrays.stream(keys.split(",")).map(String::trim).toList());
        }

        Matcher rowsRemoved = ROWS_REMOVED_PATTERN.matcher(trimmed);
        if (rowsRemoved.find()) {
            node.setRowsRemovedByFilter(Long.parseLong(rowsRemoved.group(1)));
        }

        Matcher sortMatcher = SORT_METHOD_PATTERN.matcher(trimmed);
        if (sortMatcher.find()) {
            node.setSortMethod(sortMatcher.group(1).trim());
            if (sortMatcher.group(2) != null) {
                node.setSortSpaceType(sortMatcher.group(2).trim());
            }
            if (sortMatcher.group(3) != null) {
                node.setSortSpaceUsed(Long.parseLong(sortMatcher.group(3)));
            }
        }

        Matcher bufferMatcher = BUFFERS_PATTERN.matcher(trimmed);
        if (bufferMatcher.find()) {
            parseBufferStats(bufferMatcher.group(1), node);
        }

        if (trimmed.startsWith("Settings:")) {
            node.getRawProperties().put("Settings", trimmed.substring(9).trim());
        } else if (trimmed.startsWith("I/O Timings:")) {
            node.getRawProperties().put("I/O Timings", trimmed.substring(12).trim());
        } else if (trimmed.startsWith("Query Identifier:")) {
            node.getRawProperties().put("Query Identifier", trimmed.substring(17).trim());
        }
    }

    private void parseBufferStats(String bufferStr, PlanNode node) {
        // e.g. "shared hit=233054 read=4 written=3, local hit=20729 read=7 dirtied=138 written=135"
        for (String part : bufferStr.split("[,;]")) {
            String p = part.trim();
            if (p.startsWith("shared")) {
                extractBufferNumbers(p.substring(6).trim(), node, "shared");
            } else if (p.startsWith("local")) {
                extractBufferNumbers(p.substring(5).trim(), node, "local");
            } else if (p.startsWith("temp")) {
                extractBufferNumbers(p.substring(4).trim(), node, "temp");
            }
        }
    }

    private void extractBufferNumbers(String chunk, PlanNode node, String bufferType) {
        for (String item : chunk.split("\\s+")) {
            if (item.contains("=")) {
                String[] kv = item.split("=");
                String k = kv[0].toLowerCase();
                try {
                    long val = Long.parseLong(kv[1]);
                    if ("temp".equals(bufferType)) {
                        if (k.equals("read")) node.setTempReadBlocks(val);
                        if (k.equals("written")) node.setTempWrittenBlocks(val);
                    } else if ("local".equals(bufferType)) {
                        if (k.equals("hit")) node.setLocalHitBlocks(val);
                        if (k.equals("read")) node.setLocalReadBlocks(val);
                        if (k.equals("dirtied")) node.setLocalDirtiedBlocks(val);
                        if (k.equals("written")) node.setLocalWrittenBlocks(val);
                    } else {
                        if (k.equals("hit")) node.setSharedHitBlocks(val);
                        if (k.equals("read")) node.setSharedReadBlocks(val);
                        if (k.equals("dirtied")) node.setSharedDirtiedBlocks(val);
                        if (k.equals("written")) node.setSharedWrittenBlocks(val);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void calculateExclusiveTimes(PlanNode node) {
        if (node == null) return;

        if (node.getPlans() != null && !node.getPlans().isEmpty()) {
            double childrenTotal = 0.0;
            boolean hasChildrenTimings = false;

            for (PlanNode child : node.getPlans()) {
                calculateExclusiveTimes(child);
                if (child.getActualTotalTime() != null) {
                    hasChildrenTimings = true;
                    long loops = child.getActualLoops() != null ? child.getActualLoops() : 1L;
                    childrenTotal += (child.getActualTotalTime() * loops);
                }
            }

            if (node.getActualTotalTime() != null) {
                long loops = node.getActualLoops() != null ? node.getActualLoops() : 1L;
                double total = node.getActualTotalTime() * loops;
                if (hasChildrenTimings) {
                    double exclusive = Math.max(0.0, total - childrenTotal);
                    node.setExclusiveTimeMs(Math.round(exclusive * 1000.0) / 1000.0);
                } else {
                    node.setExclusiveTimeMs(node.getActualTotalTime());
                }
            }
        } else {
            node.setExclusiveTimeMs(node.getActualTotalTime());
        }
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
}
