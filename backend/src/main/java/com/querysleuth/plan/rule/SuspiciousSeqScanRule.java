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
 * Detects sequential scans where many rows are read from disk/cache and discarded by a filter,
 * indicating high waste and potential missing index opportunity.
 */
@Component
public class SuspiciousSeqScanRule implements PlanRule {

    private static final String RULE_ID = "PLAN-RULE-003";
    private static final String RULE_NAME = "Wasteful Sequential Scan";

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

        if ("Seq Scan".equalsIgnoreCase(node.getNodeType())) {
            Long removed = node.getRowsRemovedByFilter();
            Double actualRows = node.getActualRows();
            long loops = node.getActualLoops() != null ? node.getActualLoops() : 1L;

            if (removed != null && removed > 500) {
                long totalRemoved = removed * loops;
                double totalReturned = (actualRows != null ? actualRows : 0.0) * loops;
                double totalExamined = totalRemoved + totalReturned;
                double wastePercentage = totalExamined > 0 ? (totalRemoved * 100.0 / totalExamined) : 0.0;

                // Only flag if a high percentage of rows were discarded
                if (wastePercentage >= 60.0 && totalRemoved >= 1000) {
                    Severity severity;
                    if (totalRemoved >= 20000 || wastePercentage >= 95.0) {
                        severity = Severity.HIGH;
                    } else if (totalRemoved >= 5000 || wastePercentage >= 80.0) {
                        severity = Severity.MEDIUM;
                    } else {
                        severity = Severity.LOW;
                    }

                    Confidence confidence = node.hasRuntimeMetrics() ? Confidence.HIGH : Confidence.MEDIUM;
                    String relation = node.getRelationName() != null ? node.getRelationName() : "table";

                    String title = String.format("Sequential scan on '%s' examined %,d rows but discarded %.1f%% of them",
                            relation, (long) totalExamined, wastePercentage);

                    String evidence = String.format(
                            "Relation: %s | Rows returned: %,.0f | Rows discarded by filter: %,d | Waste ratio: %.1f%% | Filter: %s",
                            relation, totalReturned, totalRemoved, wastePercentage,
                            node.getFilter() != null ? node.getFilter() : "N/A"
                    );

                    String explanation = String.format(
                            "The query performed a sequential table scan on '%s', reading every page and checking the filter against %,d rows, " +
                            "only to discard %,d rows (%.1f%%). Sequential scans are optimal for small tables or when retrieving a majority of rows, " +
                            "but scanning a large table to keep only a tiny fraction wastes CPU cycles and memory buffer space.",
                            relation, (long) totalExamined, totalRemoved, wastePercentage
                    );

                    String nextStep = String.format(
                            "1. Inspect the filter predicate: %s\n" +
                            "2. Evaluate creating a B-Tree or composite index on '%s' covering the filtered columns.\n" +
                            "3. Confirm table size and write volume: If the table has frequent writes or few total pages, verify if an index outweighs maintenance overhead.",
                            node.getFilter() != null ? node.getFilter() : "<filter>",
                            relation
                    );

                    findings.add(Finding.builder()
                            .id("FINDING-SEQ-" + node.getId())
                            .category(FindingCategory.PERFORMANCE)
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
        }

        for (PlanNode child : node.getPlans()) {
            checkNode(child, findings);
        }
    }
}
