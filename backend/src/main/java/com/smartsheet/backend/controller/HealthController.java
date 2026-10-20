package com.smartsheet.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController tells Spring:
// "This class handles web requests and returns data (JSON), not web pages."
@RestController
public class HealthController {

    // @GetMapping means: when someone visits this URL in a browser (a GET request),
    // run this method.
    @GetMapping("/api/health")
    public Map<String, String> health() {
        // Spring automatically converts this Map into JSON:
        // {"status":"UP","message":"SmartSheet AI backend is running"}
        return Map.of(
                "status", "UP",
                "message", "SmartSheet AI backend is running");
    }
}