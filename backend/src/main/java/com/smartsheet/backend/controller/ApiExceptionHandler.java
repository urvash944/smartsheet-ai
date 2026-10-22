package com.smartsheet.backend.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// @RestControllerAdvice = "watch ALL controllers; if an error escapes, handle it here."
// Every error becomes the same safe JSON shape: { "error": "..." }
// PRIVACY: messages never include file contents, and logs only record the error TYPE.
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // The upload is bigger than the 5MB limit in application.properties
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> fileTooLarge(MaxUploadSizeExceededException e) {
        return error(413, "This file is too large. The maximum size is 5 MB.");
    }

    // The request was sent without a required part ("file" or "plan")
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> missingPart(MissingServletRequestPartException e) {
        if ("plan".equals(e.getRequestPartName())) {
            return error(400, "Please send a cleaning plan.");
        }
        return error(400, "Please choose a CSV file to upload.");
    }

    // NEW (Step C1): the plan JSON is broken, e.g. a missing bracket or wrong field type
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> badJson(HttpMessageNotReadableException e) {
        return error(400, "The cleaning plan is not valid JSON.");
    }

    // NEW (Step C1): the plan part was sent without "Content-Type: application/json"
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> wrongType(HttpMediaTypeNotSupportedException e) {
        return error(415, "The cleaning plan must be sent as JSON (application/json).");
    }

    // The upload itself was broken (for example, cut off halfway)
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> brokenUpload(MultipartException e) {
        return error(400, "The upload failed. Please try again.");
    }

    // Wrong HTTP method, e.g. opening a POST-only URL in the browser (a GET)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> wrongMethod(HttpRequestMethodNotSupportedException e) {
        return error(405, "This address does not support " + e.getMethod() + " requests.");
    }

    // A URL that does not exist
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NoResourceFoundException e) {
        return error(404, "This address does not exist.");
    }

    // Safety net for anything we did not expect.
    // We log only the error's class name; its message could contain data.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Unexpected error: {}", e.getClass().getName());
        return error(500, "Something went wrong on the server. Please try again.");
    }

    private ResponseEntity<Map<String, String>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
