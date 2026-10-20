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
 * Detects significant mismatches between estimated rows (Plan Rows) and actual rows processed (Actual Rows).
 */
@Component
public class RowEstimateMismatchRule implements PlanRule {

    private static final String RULE_ID = "PLAN-RULE-001";
    private static final String RULE_NAME = "Row Estimate Discrepancy";

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

        if (node.getPlanRows() != null && node.getActualRows() != null) {
            double estimated = node.getPlanRows();
            long loops = node.getActualLoops() != null ? node.getActualLoops() : 1L;
            double actualTotal = node.getActualRows() * loops;

            double min = Math.max(0.1, Math.min(estimated, actualTotal));
            double max = Math.max(estimated, actualTotal);
            double ratio = max / min;
            double absoluteDiff = Math.abs(estimated - actualTotal);

            if (ratio >= 5.0 && absoluteDiff >= 200) {
                Severity severity = (ratio >= 10.0 || absoluteDiff >= 10000) ? Severity.HIGH : Severity.MEDIUM;
                Confidence confidence = Confidence.HIGH;

                String relationContext = node.getRelationName() != null ? " on '" + node.getRelationName() + "'" : "";
                String direction = actualTotal > estimated ? "underestimated" : "overestimated";

                String title = String.format("Planner significantly %s row count on %s%s (%.1fx difference)",
                        direction, node.getNodeType(), relationContext, ratio);

                String evidence = String.format(
                        "Estimated rows: %,.0f | Actual rows per loop: %,.0f (Loops: %,d) | Total actual rows: %,.0f | Ratio: %,.1fx | Difference: %,.0f rows",
                        estimated, node.getActualRows(), loops, actualTotal, ratio, absoluteDiff
                );

                String explanation = String.format(
                        "The PostgreSQL optimizer predicted %,.0f rows, but execution actually processed %,.0f rows on %s. " +
                        "Poor cardinality estimates deceive the optimizer into selecting inappropriate join orders, choosing nested loops over hash joins, or under-allocating memory buffers.",
                        estimated, actualTotal, node.getNodeType()
                );

                String nextStep = "1. Run 'ANALYZE " + (node.getRelationName() != null ? node.getRelationName() : "<table>") + ";' to update table statistics.\n" +
                        "2. If multiple WHERE conditions are correlated, inspect creating extended statistics: 'CREATE STATISTICS stat_name ON col1, col2 FROM " +
                        (node.getRelationName() != null ? node.getRelationName() : "table") + ";'.\n" +
                        "3. Check default_statistics_target settings if data distribution is highly skewed.";

                findings.add(Finding.builder()
                        .id("FINDING-EST-" + node.getId())
                        .category(FindingCategory.ESTIMATION_ACCURACY)
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
