package com.querysleuth.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "QuerySleuth",
                "version", "0.0.1-SNAPSHOT",
                "timestamp", Instant.now().toString(),
                "activeModules", List.of(
                        "Execution Plan Explorer (Phase 1/2)",
                        "SQL Function Investigator (Upcoming)",
                        "Persistent Error Investigator (Upcoming)"
                )
        ));
    }
}
