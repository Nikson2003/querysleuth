package com.querysleuth.plan.rule;

import com.querysleuth.common.Confidence;
import com.querysleuth.common.Finding;
import com.querysleuth.common.FindingCategory;
import com.querysleuth.common.Severity;
import com.querysleuth.plan.PlanNode;
import com.querysleuth.plan.PlanSummary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects operations that perform massive in-memory buffer page hits (e.g. scanning hundreds of thousands
 * of 8KB buffer blocks), causing high CPU cache contention and memory bus churn.
 */
@Component
public class HeavyBufferAccessRule implements PlanRule {

    private static final String RULE_ID = "PLAN-RULE-006";
    private static final String RULE_NAME = "Excessive Buffer Cache Churn";

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public String getRuleName() {
        return RULE_NAME;
    }

    @Override
    public List<Finding> evaluate(PlanNode rootNode, PlanSummary summary) {
        List<Finding> findings = new ArrayList<>();
        checkNode(rootNode, findings);
        return findings;
    }

    private void checkNode(PlanNode node, List<Finding> findings) {
        if (node == null) return;

        long sharedHit = node.getSharedHitBlocks() != null ? node.getSharedHitBlocks() : 0L;
        long localHit = node.getLocalHitBlocks() != null ? node.getLocalHitBlocks() : 0L;
        long totalHit = sharedHit + localHit;

        // Flag if 20,000+ 8KB blocks (approx. 160 MB+) were accessed in memory
        if (totalHit >= 20000) {
            double totalMb = (totalHit * 8.0) / 1024.0;
            double sharedMb = (sharedHit * 8.0) / 1024.0;
            double localMb = (localHit * 8.0) / 1024.0;

            Severity severity = totalHit >= 100000 ? Severity.HIGH : Severity.MEDIUM;
            Confidence confidence = Confidence.HIGH;

            String relation = node.getRelationName() != null ? node.getRelationName() : "operation";
            boolean isFunction = "Function Scan".equalsIgnoreCase(node.getNodeType());

            String title = String.format("Massive buffer memory churn on %s on '%s' (%,d blocks / ~%.1f MB)",
                    node.getNodeType(), relation, totalHit, totalMb);

            String evidence = String.format(
                    "Shared Hit Blocks: %,d (~%.1f MB) | Local Hit Blocks: %,d (~%.1f MB) | Total Memory Blocks: %,d (~%.1f MB) | Actual Time: %.2f ms",
                    sharedHit, sharedMb, localHit, localMb, totalHit, totalMb,
                    node.getActualTotalTime() != null ? node.getActualTotalTime() : 0.0
            );

            String explanation;
            String nextStep;

            if (isFunction) {
                explanation = String.format(
                        "The function scan '%s' accessed %,d memory buffer blocks (~%.1f MB). " +
                        "Even though memory buffer hits avoid physical disk I/O, scanning and locking hundreds of thousands of 8KB buffer frames " +
                        "consumes significant CPU cycles and memory bus bandwidth (explaining the %.1f ms runtime). " +
                        "This indicates that internal queries, loops, or temporary table manipulations inside the stored procedure are scanning large datasets without adequate indexes.",
                        relation, totalHit, totalMb, node.getActualTotalTime() != null ? node.getActualTotalTime() : 0.0
                );

                nextStep = String.format(
                        "1. Inspect internal SQL queries within '%s': Identify which SELECT/UPDATE statements scan large relations or run inside loops.\n" +
                        "2. Notice the %,d local buffer hits (~%.1f MB): The function creates or queries temporary tables; check if temp table indexing or smaller working batches are possible.\n" +
                        "3. To measure individual internal statements, enable nested statement profiling: 'SET auto_explain.log_nested = on;'.",
                        relation, localHit, localMb
                );
            } else {
                explanation = String.format(
                        "This operation (%s on '%s') accessed %,d buffer pages (~%.1f MB). " +
                        "Heavy in-memory buffer reads typically occur when a query scans entire tables or wide indexes rather than using targeted index lookups.",
                        node.getNodeType(), relation, totalHit, totalMb
                );

                nextStep = "1. Review filter predicates and check if a selective composite index can reduce page visits.\n" +
                        "2. Check for bloat in table '" + relation + "' using VACUUM or pgstattuple.";
            }

            findings.add(Finding.builder()
                    .id("FINDING-BUF-CHURN-" + node.getId())
                    .category(FindingCategory.RESOURCE_UTILIZATION)
                    .severity(severity)
                    .confidence(confidence)
                    .title(title)
                    .evidence(evidence)
                    .explanation(explanation)
                    .suggestedNextStep(nextStep)
                    .targetNodeId(node.getId())
                    .ruleId(RULE_ID)
                    .build());
        }

        for (PlanNode child : node.getPlans()) {
            checkNode(child, findings);
        }
    }
}
