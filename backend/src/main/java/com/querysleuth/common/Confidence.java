package com.querysleuth.common;

/**
 * Confidence level of an analysis finding.
 * High: Direct evidence supports the finding (e.g., measured timings or explicit buffer counts).
 * Medium: Multiple signals point toward an issue, but alternative explanations are possible.
 * Low: Heuristic pattern detected without full runtime confirmation.
 */
public enum Confidence {
    LOW,
    MEDIUM,
    HIGH
}
