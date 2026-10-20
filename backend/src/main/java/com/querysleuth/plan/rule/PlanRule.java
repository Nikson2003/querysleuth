package com.querysleuth.plan.rule;

import com.querysleuth.common.Finding;
import com.querysleuth.plan.PlanNode;
import com.querysleuth.plan.PlanSummary;

import java.util.List;

/**
 * Interface for execution plan analysis rules.
 */
public interface PlanRule {
    /**
     * Unique identifier for this rule.
     */
    String getRuleId();

    /**
     * Human-readable name for this rule.
     */
    String getRuleName();

    /**
     * Evaluates this rule against the parsed plan tree and summary.
     *
     * @param rootNode root of the parsed plan tree
     * @param summary computed summary metrics across the plan
     * @return list of findings detected by this rule (empty if none)
     */
    List<Finding> evaluate(PlanNode rootNode, PlanSummary summary);
}
