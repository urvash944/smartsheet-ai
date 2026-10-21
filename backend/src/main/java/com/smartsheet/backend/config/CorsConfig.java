package com.smartsheet.backend.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CORS = Cross-Origin Resource Sharing.
// Browsers block a website from reading replies from a DIFFERENT website,
// unless that other website (our backend) says "I trust you".
// This class tells browsers exactly which frontend website is trusted.
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    // Reads app.frontend-url, e.g. "https://smartsheet-ai.vercel.app"
    // Several sites can be given, separated by commas.
    public CorsConfig(@Value("${app.frontend-url:http://localhost:5173}") String frontendUrls) {
        this.allowedOrigins = Arrays.stream(frontendUrls.split(","))
                .map(String::strip)
                .map(url -> url.endsWith("/") ? url.substring(0, url.length() - 1) : url) // no trailing "/"
                .filter(url -> !url.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // only our API URLs
                .allowedOrigins(allowedOrigins) // only the listed websites, never "*" (everyone)
                .allowedMethods("GET", "POST"); // the only methods our API uses
    }
}