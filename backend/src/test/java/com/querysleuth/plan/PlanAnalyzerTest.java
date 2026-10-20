package com.querysleuth.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.querysleuth.common.AnalysisResult;
import com.querysleuth.common.Finding;
import com.querysleuth.common.FindingCategory;
import com.querysleuth.common.Severity;
import com.querysleuth.plan.rule.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanAnalyzerTest {

    private PlanParser planParser;
    private PlanAnalyzer planAnalyzer;

    @BeforeEach
    void setUp() {
        planParser = new PlanParser(new ObjectMapper(), new TextPlanParser());
        List<PlanRule> rules = List.of(
                new RowEstimateMismatchRule(),
                new RepeatedLoopWorkRule(),
                new SuspiciousSeqScanRule(),
                new SortDiskSpillRule(),
                new BufferReadHeavyRule(),
                new HeavyBufferAccessRule()
        );
        planAnalyzer = new PlanAnalyzer(rules);
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
    @DisplayName("Should detect high filter waste on sequential scan")
    void shouldDetectSuspiciousSeqScan() throws Exception {
        String json = readFixture("seq_scan_large.json");
        PlanParser.ParseResult parseResult = planParser.parse(json);
        AnalysisResult result = planAnalyzer.analyze(parseResult.getRootNode(), parseResult.getSummary());

        assertThat(result.getFindings()).isNotEmpty();
        Finding seqFinding = result.getFindings().stream()
                .filter(f -> "PLAN-RULE-003".equals(f.getRuleId()))
                .findFirst()
                .orElse(null);

        assertThat(seqFinding).isNotNull();
        assertThat(seqFinding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(seqFinding.getTitle()).contains("Sequential scan on 'tariff'");
        assertThat(seqFinding.getEvidence()).contains("Rows discarded by filter: 99,850");
        assertThat(seqFinding.getEvidence()).contains("99.9%");
        assertThat(seqFinding.getSuggestedNextStep()).contains("index");
    }

    @Test
    @DisplayName("Should detect high loop iterations on nested loop join")
    void shouldDetectRepeatedLoopWork() throws Exception {
        String json = readFixture("nested_loop_high_loops.json");
        PlanParser.ParseResult parseResult = planParser.parse(json);
        AnalysisResult result = planAnalyzer.analyze(parseResult.getRootNode(), parseResult.getSummary());

        Finding loopFinding = result.getFindings().stream()
                .filter(f -> "PLAN-RULE-002".equals(f.getRuleId()))
                .findFirst()
                .orElse(null);

        assertThat(loopFinding).isNotNull();
        assertThat(loopFinding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(loopFinding.getTitle()).contains("1,200 times");
        assertThat(loopFinding.getEvidence()).contains("Loops: 1,200");
    }

    @Test
    @DisplayName("Should detect severe row estimate mismatch between planner and execution")
    void shouldDetectEstimateMismatch() throws Exception {
        String json = readFixture("estimate_mismatch.json");
        PlanParser.ParseResult parseResult = planParser.parse(json);
        AnalysisResult result = planAnalyzer.analyze(parseResult.getRootNode(), parseResult.getSummary());

        Finding estFinding = result.getFindings().stream()
                .filter(f -> "PLAN-RULE-001".equals(f.getRuleId()))
                .findFirst()
                .orElse(null);

        assertThat(estFinding).isNotNull();
        assertThat(estFinding.getCategory()).isEqualTo(FindingCategory.ESTIMATION_ACCURACY);
        assertThat(estFinding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(estFinding.getTitle()).contains("underestimated");
        assertThat(estFinding.getEvidence()).contains("Estimated rows: 15");
        assertThat(estFinding.getEvidence()).contains("45,000");
    }

    @Test
    @DisplayName("Should detect sort spilling to disk storage")
    void shouldDetectSortDiskSpill() throws Exception {
        String json = readFixture("sort_disk_spill.json");
        PlanParser.ParseResult parseResult = planParser.parse(json);
        AnalysisResult result = planAnalyzer.analyze(parseResult.getRootNode(), parseResult.getSummary());

        Finding sortFinding = result.getFindings().stream()
                .filter(f -> "PLAN-RULE-004".equals(f.getRuleId()))
                .findFirst()
                .orElse(null);

        assertThat(sortFinding).isNotNull();
        assertThat(sortFinding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(sortFinding.getTitle()).contains("Sort operation spilled to disk");
        assertThat(sortFinding.getEvidence()).contains("external merge");
        assertThat(sortFinding.getEvidence()).contains("14,520 kB");
    }

    @Test
    @DisplayName("Should report explicit limitations when ANALYZE or BUFFERS was omitted")
    void shouldReportLimitationsForIncompletePlan() throws Exception {
        String json = readFixture("missing_metrics.json");
        PlanParser.ParseResult parseResult = planParser.parse(json);
        AnalysisResult result = planAnalyzer.analyze(parseResult.getRootNode(), parseResult.getSummary());

        assertThat(result.getLimitations()).hasSize(2);
        assertThat(result.getLimitations().get(0)).contains("without ANALYZE");
        assertThat(result.getLimitations().get(1)).contains("without BUFFERS");
    }

    @Test
    @DisplayName("Should detect massive buffer churn and parse multiline function call on stored procedure text plan")
    void shouldDetectHeavyBufferAccessOnFunctionScan() {
        String planText = """
                Function Scan on dbo.gettariffmainsearchsinglerow_db (cost=0.25..10.25 rows=1000 width=2289) (actual time=402.410..402.566 rows=2000 loops=1)
                 Output: totalcount, idtariffrate, linktarifftype
                 Function Call: gettariffmainsearchsinglerow_db(' 
                 { 
                 "legalentityid": 24, 
                 "dep": "E", 
                 "getActiveTariff": true 
                 } 
                 '::character varying)
                 Buffers: shared hit=233054 read=4 written=3, local hit=20729 read=7 dirtied=138 written=135
                 I/O Timings: shared read=5.203
                 Settings: effective_cache_size = '21509072kB', search_path = 'dbo, public, "$user"', work_mem = '32MB'
                 Query Identifier: 7694549067571221108
                 Planning Time: 0.104 ms
                 Execution Time: 404.568 ms
                """;

        PlanParser.ParseResult parseResult = planParser.parse(planText);
        PlanNode root = parseResult.getRootNode();

        assertThat(root.getNodeType()).isEqualTo("Function Scan");
        assertThat(root.getSchema()).isEqualTo("dbo");
        assertThat(root.getRelationName()).isEqualTo("gettariffmainsearchsinglerow_db");
        assertThat(root.getSharedHitBlocks()).isEqualTo(233054L);
        assertThat(root.getLocalHitBlocks()).isEqualTo(20729L);
        assertThat(root.getSharedReadBlocks()).isEqualTo(4L);
        assertThat(root.getFunctionCall()).contains("\"legalentityid\": 24");

        AnalysisResult result = planAnalyzer.analyze(root, parseResult.getSummary());
        assertThat(result.getFindings()).isNotEmpty();

        Finding churnFinding = result.getFindings().stream()
                .filter(f -> "PLAN-RULE-006".equals(f.getRuleId()))
                .findFirst()
                .orElse(null);

        assertThat(churnFinding).isNotNull();
        assertThat(churnFinding.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(churnFinding.getTitle()).contains("Massive buffer memory churn");
        assertThat(churnFinding.getEvidence()).contains("Shared Hit Blocks: 233,054");
        assertThat(churnFinding.getEvidence()).contains("Local Hit Blocks: 20,729");
        assertThat(churnFinding.getExplanation()).contains("stored procedure are scanning large datasets");
    }
}
