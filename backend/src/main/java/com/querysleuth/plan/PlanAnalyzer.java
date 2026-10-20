package com.querysleuth.plan;

import com.querysleuth.common.AnalysisResult;
import com.querysleuth.common.Finding;
import com.querysleuth.common.Severity;
import com.querysleuth.plan.rule.PlanRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Core engine for evaluating rules against a PostgreSQL execution plan.
 */
@Service
public class PlanAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PlanAnalyzer.class);
    private final List<PlanRule> rules;

    public PlanAnalyzer(List<PlanRule> rules) {
        this.rules = rules != null ? rules : Collections.emptyList();
    }

    /**
     * Analyzes a parsed plan tree and summary using all registered rules.
     *
     * @param rootNode parsed plan tree root
     * @param summary summary statistics
     * @return structured AnalysisResult containing findings, limitations, and summary
     */
    public AnalysisResult analyze(PlanNode rootNode, PlanSummary summary) {
        AnalysisResult result = new AnalysisResult("EXECUTION_PLAN_EXPLORER");

        if (rootNode == null) {
            result.addLimitation("No plan node provided for analysis.");
            return result;
        }

        // Check for limitations in provided plan data
        if (!summary.isAnalyzeIncluded()) {
            result.addLimitation("Plan was generated without ANALYZE (cost estimates only). " +
                    "Actual row counts, execution timings, and loop counts were not available for runtime analysis. " +
                    "Re-run with 'EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)' for complete diagnostics.");
        }

        if (!summary.isBuffersIncluded()) {
            result.addLimitation("Plan was generated without BUFFERS. " +
                    "Shared buffer hits, disk reads, and temp storage usage metrics were not available. " +
                    "Include 'BUFFERS' in your EXPLAIN command for I/O cache analysis.");
        }

        // Evaluate all rules
        List<Finding> allFindings = new ArrayList<>();
        for (PlanRule rule : rules) {
            try {
                List<Finding> ruleFindings = rule.evaluate(rootNode, summary);
                if (ruleFindings != null) {
                    allFindings.addAll(ruleFindings);
                }
            } catch (Exception e) {
                log.error("Error evaluating plan rule: {}", rule.getRuleId(), e);
                result.addLimitation("Rule " + rule.getRuleId() + " encountered an evaluation error: " + e.getMessage());
            }
        }

        // Sort findings by severity (HIGH -> MEDIUM -> LOW -> INFORMATIONAL)
        allFindings.sort(Comparator.comparing(Finding::getSeverity, Comparator.comparingInt(this::severityRank)));

        result.setFindings(allFindings);

        // Populate summary map
        Map<String, Object> summaryMap = new HashMap<>();
        summaryMap.put("totalNodes", summary.getTotalNodes());
        summaryMap.put("planningTimeMs", summary.getPlanningTimeMs());
        summaryMap.put("executionTimeMs", summary.getExecutionTimeMs());
        summaryMap.put("totalCost", summary.getTotalCost());
        summaryMap.put("maxActualRows", summary.getMaxActualRows());
        summaryMap.put("maxActualRowsNodeId", summary.getMaxActualRowsNodeId());
        summaryMap.put("maxExclusiveTimeMs", summary.getMaxExclusiveTimeMs());
        summaryMap.put("dominantNodeId", summary.getDominantNodeId());
        summaryMap.put("totalSharedHitBlocks", summary.getTotalSharedHitBlocks());
        summaryMap.put("totalSharedReadBlocks", summary.getTotalSharedReadBlocks());
        summaryMap.put("totalSharedDirtiedBlocks", summary.getTotalSharedDirtiedBlocks());
        summaryMap.put("totalSharedWrittenBlocks", summary.getTotalSharedWrittenBlocks());
        summaryMap.put("totalLocalHitBlocks", summary.getTotalLocalHitBlocks());
        summaryMap.put("totalLocalReadBlocks", summary.getTotalLocalReadBlocks());
        summaryMap.put("totalLocalDirtiedBlocks", summary.getTotalLocalDirtiedBlocks());
        summaryMap.put("totalLocalWrittenBlocks", summary.getTotalLocalWrittenBlocks());
        summaryMap.put("totalTempReadBlocks", summary.getTotalTempReadBlocks());
        summaryMap.put("totalTempWrittenBlocks", summary.getTotalTempWrittenBlocks());
        summaryMap.put("hasDiskSpill", summary.isHasDiskSpill());
        summaryMap.put("analyzeIncluded", summary.isAnalyzeIncluded());
        summaryMap.put("buffersIncluded", summary.isBuffersIncluded());
        summaryMap.put("totalFindings", allFindings.size());

        // Count findings by severity
        Map<String, Long> severityCounts = new LinkedHashMap<>();
        for (Severity s : Severity.values()) {
            long count = allFindings.stream().filter(f -> f.getSeverity() == s).count();
            severityCounts.put(s.name(), count);
        }
        summaryMap.put("severityCounts", severityCounts);

        result.setSummary(summaryMap);
        return result;
    }

    private int severityRank(Severity severity) {
        if (severity == null) return 99;
        return switch (severity) {
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
            case INFORMATIONAL -> 4;
        };
    }
}
