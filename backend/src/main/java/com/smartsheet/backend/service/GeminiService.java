package com.smartsheet.backend.service;

import java.time.Duration;
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
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    // {model} is filled in safely by RestClient
    private static final String GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    // @Value reads settings from application.properties (which reads them from
    // .env)
    public GeminiService(@Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model}") String model) {

        // Timeouts: never let a slow AI response hang our server forever
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model;
    }

    // Sends the rules (system instruction) and the request (user message) to
    // Gemini.
    public String ask(String systemText, String userText) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiUnavailableException(
                    "The AI is not set up: GEMINI_API_KEY is missing on the server. You can still use manual mode.");
        }

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemText))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userText)))));

        try {
            GeminiResponse response = restClient.post()
                    .uri(GENERATE_URL, model)
                    .header("x-goog-api-key", apiKey) // key goes in a header, never in the URL
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(GeminiResponse.class);

            return extractText(response);

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();

            // Google's error explanation, e.g. "API key not valid".
            // It never contains our API key. We cut it to 300 characters to keep logs
            // short.
            String details = e.getResponseBodyAsString();
            if (details.length() > 300) {
                details = details.substring(0, 300);
            }
            log.warn("Gemini request failed with HTTP status {}: {}", status, details);

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
            // Skip internal "thinking" parts; keep only the real answer
            if (part.text() != null && !Boolean.TRUE.equals(part.thought())) {
                text.append(part.text());
            }
        }
        return text.toString();
    }

    // Turns an HTTP error code into a friendly message
    private String messageForStatus(int status) {
        return switch (status) {
            case 429 -> "The AI free-tier limit was reached. Wait a minute and try again, or use manual mode.";
            case 400, 401, 403 -> "The AI rejected the request. Check that GEMINI_API_KEY is correct.";
            case 404 -> "The AI model was not found. Check the GEMINI_MODEL setting.";
            default -> "The AI service is having problems right now. Try again later, or use manual mode.";
        };
    }

    // ---- Records matching the parts of Gemini's JSON reply we need ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text, Boolean thought) {
    }
}