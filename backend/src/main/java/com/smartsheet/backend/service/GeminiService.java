package com.smartsheet.backend.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Sends text to the Gemini API and returns the text reply.
// This is the ONLY class that talks to Google.
//
// Two ways to ask:
//   ask(...)     -> free text reply (used by the old AiPlannerService)
//   askJson(...) -> a JSON reply that must follow a schema (used by the new AiPlanService)
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private static final String GENERATE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    // The part after ":" is the default used if the setting is missing.
    public GeminiService(@Value("${gemini.api-key:}") String apiKey,
                         @Value("${gemini.model:gemini-flash-lite-latest}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        // strip() removes accidental spaces from the key (a common copy-paste problem)
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
    }

    // Free text reply
    public String ask(String systemText, String userText) {
        return send(baseBody(systemText, userText));
    }

    // JSON reply that follows "schema".
    // "temperature" 0.1 = very little randomness: the same instruction gives (almost) the same plan.
    // If Google refuses the schema (HTTP 400), we try once more asking only for "some JSON";
    // our Java code checks the reply either way.
    public String askJson(String systemText, String userText, Map<String, Object> schema) {
        Map<String, Object> body = baseBody(systemText, userText);
        body.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", schema,
                "temperature", 0.1));
        try {
            return send(body);
        } catch (SchemaRejectedException e) {
            log.warn("Gemini refused the JSON schema; retrying with plain JSON mode");
            body.put("generationConfig", Map.of(
                    "responseMimeType", "application/json",
                    "temperature", 0.1));
            return send(body);
        }
    }

    // The JSON body Gemini expects:
    // { "systemInstruction": { "parts": [ { "text": "..." } ] },
    //   "contents": [ { "role": "user", "parts": [ { "text": "..." } ] } ] }
    private Map<String, Object> baseBody(String systemText, String userText) {
        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemText))));
        body.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userText)))));
        return body;
    }

    private String send(Map<String, Object> body) {
        if (apiKey.isBlank()) {
            throw new AiUnavailableException(
                    "The AI is not set up: GEMINI_API_KEY is missing on the server. You can still use manual mode.");
        }
        try {
            GeminiResponse response = restClient.post()
                    .uri(GENERATE_URL, model)
                    .header("x-goog-api-key", apiKey)   // key goes in a header, never in the URL
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
            return extractText(response);

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            // Google's explanation (never contains our key), cut to 300 characters
            String details = e.getResponseBodyAsString();
            if (details.length() > 300) {
                details = details.substring(0, 300);
            }
            log.warn("Gemini request failed with HTTP status {}: {}", status, details);

            if (status == 400 && body.get("generationConfig") instanceof Map<?, ?> config
                    && config.containsKey("responseSchema")) {
                throw new SchemaRejectedException();
            }
            throw new AiUnavailableException(messageForStatus(status));

        } catch (RestClientException e) {
            log.warn("Could not reach Gemini: {}", e.getClass().getSimpleName());
            throw new AiUnavailableException(
                    "Could not reach the AI service. Check your internet connection, or use manual mode.");
        }
    }

    // Gets the reply text from: candidates[0].content.parts[*].text
    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()
                || response.candidates().get(0).content() == null
                || response.candidates().get(0).content().parts() == null) {
            throw new AiUnavailableException("The AI returned an empty answer. Please try again, or use manual mode.");
        }
        StringBuilder text = new StringBuilder();
        for (Part part : response.candidates().get(0).content().parts()) {
            if (part.text() != null && !Boolean.TRUE.equals(part.thought())) {
                text.append(part.text());
            }
        }
        return text.toString();
    }

    private String messageForStatus(int status) {
        return switch (status) {
            case 429 -> "The AI free-tier limit was reached. Wait a minute and try again, or use manual mode.";
            case 400, 401, 403 -> "The AI rejected the request. Check that GEMINI_API_KEY is correct.";
            case 404 -> "The AI model was not found. Check the GEMINI_MODEL setting.";
            default -> "The AI service is having problems right now. Try again later, or use manual mode.";
        };
    }

    // Used only inside this class to trigger the "retry without schema" path
    private static class SchemaRejectedException extends RuntimeException {
    }

    // ---- Records matching the parts of Gemini's JSON reply we need ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiResponse(List<Candidate> candidates) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text, Boolean thought) { }
}
