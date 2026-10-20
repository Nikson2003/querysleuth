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
 * Detects nodes with high loop iterations causing repetitive, non-set-based overhead (e.g. repeated inner table evaluations).
 */
@Component
public class RepeatedLoopWorkRule implements PlanRule {

    private static final String RULE_ID = "PLAN-RULE-002";
    private static final String RULE_NAME = "Repeated Loop Execution";

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

        Long loops = node.getActualLoops();
        if (loops != null && loops >= 50) {
            double actualRowsPerLoop = node.getActualRows() != null ? node.getActualRows() : 0.0;
            double actualTimePerLoop = node.getActualTotalTime() != null ? node.getActualTotalTime() : 0.0;
            double totalTime = actualTimePerLoop * loops;
            double totalRows = actualRowsPerLoop * loops;

            boolean isSubstantial = totalTime >= 50.0 || totalRows >= 5000 || loops >= 500;

            if (isSubstantial) {
                Severity severity = (loops >= 1000 || totalTime >= 500.0) ? Severity.HIGH : Severity.MEDIUM;
                Confidence confidence = Confidence.HIGH;

                String relationContext = node.getRelationName() != null ? " on '" + node.getRelationName() + "'" : "";

                String title = String.format("Node %s%s executed %,d times with significant cumulative overhead",
                        node.getNodeType(), relationContext, loops);

                String evidence = String.format(
                        "Loops: %,d | Per-loop time: %.3f ms | Cumulative time: %,.2f ms | Rows per loop: %,.1f | Cumulative rows: %,.0f",
                        loops, actualTimePerLoop, totalTime, actualRowsPerLoop, totalRows
                );

                String explanation = String.format(
                        "This operation (%s) was re-executed %,d times in a loop. In PostgreSQL, this typically happens on the inner branch of a Nested Loop join. " +
                        "When the outer relation has many rows or the planner's row estimate was too low, a nested loop becomes a bottleneck compared to set-based joins (Hash Join or Merge Join).",
                        node.getNodeType(), loops
                );

                String nextStep = "1. Inspect whether the join condition on the inner table has a matching index to support repeated lookups.\n" +
                        "2. Check if the outer relation had an underestimated row count that misled the optimizer into choosing a Nested Loop.\n" +
                        "3. Test running 'EXPLAIN ANALYZE' with 'SET LOCAL enable_nestloop = off;' to check if a Hash Join performs faster.";

                findings.add(Finding.builder()
                        .id("FINDING-LOOP-" + node.getId())
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

        for (PlanNode child : node.getPlans()) {
            checkNode(child, findings);
        }
    }
}
