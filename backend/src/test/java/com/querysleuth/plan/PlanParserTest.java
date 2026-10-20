package com.querysleuth.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanParserTest {

    private PlanParser planParser;
    private TextPlanParser textPlanParser;

    @BeforeEach
    void setUp() {
        textPlanParser = new TextPlanParser();
        planParser = new PlanParser(new ObjectMapper(), textPlanParser);
    }

    private String readFixture(String filename) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/" + filename)) {
            if (is == null) {
                throw new IllegalStateException("Fixture not found: " + filename);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Should successfully parse standard EXPLAIN JSON with Seq Scan and buffers")
    void shouldParseSeqScanPlan() throws Exception {
        String json = readFixture("seq_scan_large.json");
        PlanParser.ParseResult result = planParser.parse(json);

        assertThat(result).isNotNull();
        PlanNode root = result.getRootNode();
        assertThat(root.getNodeType()).isEqualTo("Seq Scan");
        assertThat(root.getRelationName()).isEqualTo("tariff");
        assertThat(root.getActualRows()).isEqualTo(150.0);
        assertThat(root.getRowsRemovedByFilter()).isEqualTo(99850L);
        assertThat(root.getSharedHitBlocks()).isEqualTo(1845L);
        assertThat(root.getSharedReadBlocks()).isEqualTo(500L);

        PlanSummary summary = result.getSummary();
        assertThat(summary.isAnalyzeIncluded()).isTrue();
        assertThat(summary.isBuffersIncluded()).isTrue();
        assertThat(summary.getTotalNodes()).isEqualTo(1);
        assertThat(summary.getTotalCost()).isEqualTo(3845.00);
        assertThat(summary.getExecutionTimeMs()).isEqualTo(18.512);
    }

    @Test
    @DisplayName("Should parse nested loop plan with child nodes and compute exclusive times")
    void shouldParseNestedLoopPlan() throws Exception {
        String json = readFixture("nested_loop_high_loops.json");
        PlanParser.ParseResult result = planParser.parse(json);

        PlanNode root = result.getRootNode();
        assertThat(root.getNodeType()).isEqualTo("Nested Loop");
        assertThat(root.getPlans()).hasSize(2);

        PlanNode outer = root.getPlans().get(0);
        assertThat(outer.getNodeType()).isEqualTo("Seq Scan");
        assertThat(outer.getRelationName()).isEqualTo("shipment_records");
        assertThat(outer.getActualLoops()).isEqualTo(1L);

        PlanNode inner = root.getPlans().get(1);
        assertThat(inner.getNodeType()).isEqualTo("Index Scan");
        assertThat(inner.getRelationName()).isEqualTo("tariff");
        assertThat(inner.getActualLoops()).isEqualTo(1200L);

        PlanSummary summary = result.getSummary();
        assertThat(summary.getTotalNodes()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should parse plan with missing ANALYZE and BUFFERS gracefully")
    void shouldParsePlanWithoutAnalyzeAndBuffers() throws Exception {
        String json = readFixture("missing_metrics.json");
        PlanParser.ParseResult result = planParser.parse(json);

        PlanNode root = result.getRootNode();
        assertThat(root.getNodeType()).isEqualTo("Seq Scan");
        assertThat(root.getPlanRows()).isEqualTo(1000.0);
        assertThat(root.getActualRows()).isNull();
        assertThat(root.getActualTotalTime()).isNull();

        PlanSummary summary = result.getSummary();
        assertThat(summary.isAnalyzeIncluded()).isFalse();
        assertThat(summary.isBuffersIncluded()).isFalse();
    }

    @Test
    @DisplayName("Should accept a single JSON object instead of an array")
    void shouldAcceptSingleJsonObject() {
        String json = """
                {
                  "Plan": {
                    "Node Type": "Result",
                    "Startup Cost": 0.00,
                    "Total Cost": 0.01,
                    "Plan Rows": 1,
                    "Plan Width": 4
                  }
                }
                """;
        PlanParser.ParseResult result = planParser.parse(json);
        assertThat(result.getRootNode().getNodeType()).isEqualTo("Result");
    }

    @Test
    @DisplayName("Should reject empty or blank input with clear message")
    void shouldRejectEmptyInput() {
        assertThatThrownBy(() -> planParser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Should parse standard PostgreSQL text EXPLAIN ANALYZE output")
    void shouldParseTextExplainAnalyze() {
        String textPlan = """
                Function Scan on gettariffmainsearchsinglerow_db (cost=0.25..10.25 rows=1000 width=2289) (actual time=440.580..440.737 rows=2000 loops=1)
                 Planning Time: 0.133 ms
                 Execution Time: 442.744 ms
                """;

        PlanParser.ParseResult result = planParser.parse(textPlan);
        assertThat(result).isNotNull();

        PlanNode root = result.getRootNode();
        assertThat(root.getNodeType()).isEqualTo("Function Scan");
        assertThat(root.getRelationName()).isEqualTo("gettariffmainsearchsinglerow_db");
        assertThat(root.getStartupCost()).isEqualTo(0.25);
        assertThat(root.getTotalCost()).isEqualTo(10.25);
        assertThat(root.getPlanRows()).isEqualTo(1000.0);
        assertThat(root.getPlanWidth()).isEqualTo(2289);
        assertThat(root.getActualStartupTime()).isEqualTo(440.580);
        assertThat(root.getActualTotalTime()).isEqualTo(440.737);
        assertThat(root.getActualRows()).isEqualTo(2000.0);
        assertThat(root.getActualLoops()).isEqualTo(1L);

        PlanSummary summary = result.getSummary();
        assertThat(summary.getPlanningTimeMs()).isEqualTo(0.133);
        assertThat(summary.getExecutionTimeMs()).isEqualTo(442.744);
        assertThat(summary.getTotalCost()).isEqualTo(10.25);
        assertThat(summary.isAnalyzeIncluded()).isTrue();
    }

    @Test
    @DisplayName("Should parse multi-node hierarchical text plan with child branches")
    void shouldParseHierarchicalTextPlan() {
        String textPlan = """
                Nested Loop  (cost=0.29..12840.50 rows=1500 width=128) (actual time=0.045..240.500 rows=1200 loops=1)
                  ->  Seq Scan on shipment_records s  (cost=0.00..145.00 rows=1200 width=32) (actual time=0.012..2.100 rows=1200 loops=1)
                  ->  Index Scan using idx_tariff_id on tariff t  (cost=0.29..8.30 rows=1 width=96) (actual time=0.180..0.198 rows=1 loops=1200)
                        Index Cond: (id = s.tariff_id)
                Planning Time: 0.420 ms
                Execution Time: 241.100 ms
                """;

        PlanParser.ParseResult result = planParser.parse(textPlan);
        assertThat(result.getRootNode().getNodeType()).isEqualTo("Nested Loop");
        assertThat(result.getRootNode().getPlans()).hasSize(2);
        assertThat(result.getRootNode().getPlans().get(1).getIndexName()).isEqualTo("idx_tariff_id");
        assertThat(result.getRootNode().getPlans().get(1).getIndexCond()).isEqualTo("(id = s.tariff_id)");
        assertThat(result.getSummary().getTotalNodes()).isEqualTo(3);
    }
}
