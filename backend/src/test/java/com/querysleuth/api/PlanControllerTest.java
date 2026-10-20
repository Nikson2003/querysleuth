package com.querysleuth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/health should return UP status and active modules")
    void shouldReturnHealthStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("QuerySleuth"))
                .andExpect(jsonPath("$.activeModules").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/plans/analyze with valid plan should return 200 and findings")
    void shouldAnalyzeValidPlan() throws Exception {
        String planJson = """
                [
                  {
                    "Plan": {
                      "Node Type": "Seq Scan",
                      "Relation Name": "tariff",
                      "Startup Cost": 0.00,
                      "Total Cost": 3500.00,
                      "Plan Rows": 100,
                      "Actual Rows": 120,
                      "Actual Loops": 1,
                      "Rows Removed by Filter": 50000,
                      "Filter": "(origin_port = 'ANR')"
                    },
                    "Execution Time": 14.50
                  }
                ]
                """;

        mockMvc.perform(post("/api/v1/plans/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(planJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.planTree.nodeType").value("Seq Scan"))
                .andExpect(jsonPath("$.findings", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.summary.executionTimeMs").value(14.50));
    }

    @Test
    @DisplayName("POST /api/v1/plans/analyze with malformed JSON should return 400 with structured ErrorResponse")
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/plans/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid Input"));
    }

    @Test
    @DisplayName("POST /api/v1/plans/analyze with text format should return 200 and parsed tree")
    void shouldAnalyzeValidTextPlan() throws Exception {
        String textPlan = """
                Function Scan on gettariffmainsearchsinglerow_db (cost=0.25..10.25 rows=1000 width=2289) (actual time=440.580..440.737 rows=2000 loops=1)
                 Planning Time: 0.133 ms
                 Execution Time: 442.744 ms
                """;

        mockMvc.perform(post("/api/v1/plans/analyze")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(textPlan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.planTree.nodeType").value("Function Scan"))
                .andExpect(jsonPath("$.planTree.relationName").value("gettariffmainsearchsinglerow_db"))
                .andExpect(jsonPath("$.summary.executionTimeMs").value(442.744));
    }
}
