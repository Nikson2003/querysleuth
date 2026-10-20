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
 * Detects Sort or Materialize operations that spilled to temporary disk storage due to exceeding work_mem.
 */
@Component
public class SortDiskSpillRule implements PlanRule {

    private static final String RULE_ID = "PLAN-RULE-004";
    private static final String RULE_NAME = "Sort Spilled to Disk";

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

        boolean isDiskSort = "Disk".equalsIgnoreCase(node.getSortSpaceType()) ||
                (node.getSortMethod() != null && node.getSortMethod().toLowerCase().contains("external"));
        boolean hasTempWrites = (node.getTempWrittenBlocks() != null && node.getTempWrittenBlocks() > 0);

        if (isDiskSort || hasTempWrites) {
            String title = String.format("Sort operation spilled to disk storage on %s", node.getNodeType());

            Long spaceKb = node.getSortSpaceUsed();
            String spaceStr = spaceKb != null ? String.format("%,d kB", spaceKb) : "unknown size";
            String sortMethod = node.getSortMethod() != null ? node.getSortMethod() : "external sort";

            String evidence = String.format(
                    "Sort Method: %s | Space Type: %s | Space Used: %s | Temp Blocks Written: %,d",
                    sortMethod,
                    node.getSortSpaceType() != null ? node.getSortSpaceType() : "Disk",
                    spaceStr,
                    node.getTempWrittenBlocks() != null ? node.getTempWrittenBlocks() : 0
            );

            String explanation = "PostgreSQL allocates memory up to 'work_mem' for sorting operations. " +
                    "When the dataset to sort exceeds work_mem, PostgreSQL writes temporary files to disk (external merge sort). " +
                    "Disk-based sorting is orders of magnitude slower than in-memory quicksort or top-N heapsort.";

            String nextStep = "1. If this query requires sorting large datasets and is run selectively, consider increasing 'work_mem' locally for the session: 'SET LOCAL work_mem = '64MB';'.\n" +
                    "2. Check if an index matching the ORDER BY clause already exists to avoid the explicit sort step entirely.\n" +
                    "3. Review the SELECT clause: Eliminate unnecessary columns or large text fields that bloat the sort record width.";

            findings.add(Finding.builder()
                    .id("FINDING-SORT-" + node.getId())
                    .category(FindingCategory.RESOURCE_UTILIZATION)
                    .severity(Severity.HIGH)
                    .confidence(Confidence.HIGH)
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
