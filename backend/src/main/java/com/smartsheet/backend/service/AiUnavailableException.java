package com.smartsheet.backend.service;

// A custom error for "the AI can't help right now"
// (no key, no internet, free-tier limit reached, etc.).
// The message is always safe to show to the user — it never contains data.
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }
}