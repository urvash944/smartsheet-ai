package com.smartsheet.backend.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartsheet.backend.model.CleanResult;
import com.smartsheet.backend.model.CleaningOperation;
import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;
import com.smartsheet.backend.model.UploadResult;
import com.smartsheet.backend.service.CleaningService;
import com.smartsheet.backend.service.CsvService;
import com.smartsheet.backend.service.ProfileService;

@RestController
@RequestMapping("/api/csv")
public class CsvController {

    // Error message used when a file cannot be read. It never includes file
    // contents.
    private static final String READ_ERROR = "Could not read this file. Please check that it is a valid CSV.";

    private final CsvService csvService;
    private final ProfileService profileService;
    private final CleaningService cleaningService;

    // Spring automatically passes in all three services
    public CsvController(CsvService csvService,
            ProfileService profileService,
            CleaningService cleaningService) {
        this.csvService = csvService;
        this.profileService = profileService;
        this.cleaningService = cleaningService;
    }

    // POST /api/csv/upload -> returns { data, profile }
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        String fileError = validateFile(file);
        if (fileError != null) {
            return error(fileError);
        }

        try {
            CsvData data = csvService.parse(file);
            DataProfile profile = profileService.profile(data);
            return ResponseEntity.ok(new UploadResult(data, profile));

        } catch (IllegalArgumentException e) {
            return error(e.getMessage()); // e.g. "The CSV file is empty."
        } catch (Exception e) {
            return error(READ_ERROR);
        }
    }

    // POST /api/csv/clean -> returns { data, appliedOperations, before, after }
    // "operations" can be sent several times, e.g.
    // operations=TRIM_WHITESPACE&operations=REMOVE_DUPLICATES
    @PostMapping("/clean")
    public ResponseEntity<?> clean(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "operations", required = false) List<String> operationNames) {
        String fileError = validateFile(file);
        if (fileError != null) {
            return error(fileError);
        }

        if (operationNames == null || operationNames.isEmpty()) {
            return error("Please choose at least one cleaning operation.");
        }

        // SECURITY: only operations on the approved list are accepted
        Set<CleaningOperation> operations;
        try {
            operations = CleaningOperation.fromNames(operationNames);
        } catch (IllegalArgumentException e) {
            return error("Unknown cleaning operation. Allowed operations: "
                    + List.of(CleaningOperation.values()));
        }

        try {
            CsvData data = csvService.parse(file);
            DataProfile before = profileService.profile(data);
            CsvData cleaned = cleaningService.clean(data, operations);
            DataProfile after = profileService.profile(cleaned);

            return ResponseEntity.ok(
                    new CleanResult(cleaned, new ArrayList<>(operations), before, after));

        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(READ_ERROR);
        }
    }

    // Shared file checks for both endpoints. Returns an error message, or null if
    // the file is OK.
    private String validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "Please choose a CSV file to upload.";
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".csv")) {
            return "Only .csv files are allowed.";
        }
        return null;
    }

    // Small helper so every error has the same JSON shape: { "error": "..." }
    private ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}