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
 * Detects nodes with heavy Shared Read Blocks (cache misses) relative to Shared Hit Blocks (cache hits),
 * indicating disk I/O pressure and buffer pool misses.
 */
@Component
public class BufferReadHeavyRule implements PlanRule {

    private static final String RULE_ID = "PLAN-RULE-005";
    private static final String RULE_NAME = "Heavy Buffer Read Misses";

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

        Long readBlocks = node.getSharedReadBlocks();
        Long hitBlocks = node.getSharedHitBlocks() != null ? node.getSharedHitBlocks() : 0L;

        if (readBlocks != null && readBlocks >= 1000) {
            long totalBlocks = readBlocks + hitBlocks;
            double missPercentage = totalBlocks > 0 ? (readBlocks * 100.0 / totalBlocks) : 0.0;

            if (missPercentage >= 50.0) {
                Severity severity = (readBlocks >= 10000 || missPercentage >= 85.0) ? Severity.MEDIUM : Severity.LOW;
                Confidence confidence = Confidence.HIGH;

                String relationContext = node.getRelationName() != null ? " on '" + node.getRelationName() + "'" : "";

                String title = String.format("High disk buffer reads on %s%s (%.1f%% cache miss rate)",
                        node.getNodeType(), relationContext, missPercentage);

                // PostgreSQL buffer block size is standard 8kB
                double mbRead = (readBlocks * 8.0) / 1024.0;

                String evidence = String.format(
                        "Shared Read Blocks: %,d (approx. %.1f MB) | Shared Hit Blocks: %,d | Cache miss rate: %.1f%%",
                        readBlocks, mbRead, hitBlocks, missPercentage
                );

                String explanation = String.format(
                        "This operation (%s) had to fetch %,d 8KB blocks (approx. %.1f MB) from disk/OS cache because they were not cached in PostgreSQL shared_buffers. " +
                        "High read blocks indicate cold cache state, undersized shared_buffers, or an access pattern that scans more data than necessary.",
                        node.getNodeType(), readBlocks, mbRead
                );

                String nextStep = "1. If this is a frequently run query, verify if PostgreSQL 'shared_buffers' is adequately sized for the active working set.\n" +
                        "2. Inspect whether selective indexes can restrict the scan to fewer pages rather than reading entire table pages.\n" +
                        "3. Check for table/index bloat using pgstattuple or VACUUM ANALYZE.";

                findings.add(Finding.builder()
                        .id("FINDING-BUF-" + node.getId())
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
        }

        for (PlanNode child : node.getPlans()) {
            checkNode(child, findings);
        }
    }
}
