package com.querysleuth.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.querysleuth.api.dto.PlanAnalysisRequest;
import com.querysleuth.api.dto.PlanAnalysisResponse;
import com.querysleuth.common.AnalysisResult;
import com.querysleuth.plan.PlanAnalyzer;
import com.querysleuth.plan.PlanParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/plans")
@CrossOrigin(origins = "*")
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    private final PlanParser planParser;
    private final PlanAnalyzer planAnalyzer;
    private final ObjectMapper objectMapper;

    public PlanController(PlanParser planParser, PlanAnalyzer planAnalyzer, ObjectMapper objectMapper) {
        this.planParser = planParser;
        this.planAnalyzer = planAnalyzer;
        this.objectMapper = objectMapper;
    }

    /**
     * Accepts a plan analysis request and returns structured execution tree and findings.
     * Supports both structured { "planJson": "..." } and raw JSON objects.
     */
    @PostMapping(value = "/analyze", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<PlanAnalysisResponse> analyzePlan(@RequestBody String rawBody) {
        String planContent = extractPlanContent(rawBody);

        log.debug("Analyzing execution plan (payload size: {} chars)", planContent.length());

        PlanParser.ParseResult parseResult = planParser.parse(planContent);
        AnalysisResult analysisResult = planAnalyzer.analyze(parseResult.getRootNode(), parseResult.getSummary());

        PlanAnalysisResponse response = new PlanAnalysisResponse(
                "SUCCESS",
                parseResult.getSummary(),
                parseResult.getRootNode(),
                analysisResult.getFindings(),
                analysisResult.getLimitations(),
                analysisResult.getSummary()
        );

        return ResponseEntity.ok(response);
    }

    private String extractPlanContent(String rawBody) {
        if (rawBody == null || rawBody.trim().isEmpty()) {
            throw new IllegalArgumentException("Plan content cannot be empty.");
        }

        String trimmed = rawBody.trim();
        try {
            JsonNode tree = objectMapper.readTree(trimmed);
            // If the body is { "planJson": "..." }, extract the inner planJson
            if (tree.isObject() && tree.has("planJson")) {
                JsonNode inner = tree.get("planJson");
                if (inner.isTextual()) {
                    return inner.asText();
                } else {
                    return objectMapper.writeValueAsString(inner);
                }
            }
        } catch (Exception ignored) {
            // If not JSON envelope, proceed with raw body
        }
        return trimmed;
    }
}
