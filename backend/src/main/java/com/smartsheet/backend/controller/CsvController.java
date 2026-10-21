package com.smartsheet.backend.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartsheet.backend.model.AiCleanResult;
import com.smartsheet.backend.model.AiDecision;
import com.smartsheet.backend.model.CleanResult;
import com.smartsheet.backend.model.CleaningOperation;
import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;
import com.smartsheet.backend.model.UploadResult;
import com.smartsheet.backend.service.AiPlannerService;
import com.smartsheet.backend.service.AiUnavailableException;
import com.smartsheet.backend.service.CleaningService;
import com.smartsheet.backend.service.CsvService;
import com.smartsheet.backend.service.ProfileService;

@RestController
@RequestMapping("/api/csv")
public class CsvController {

    private static final String READ_ERROR = "Could not read this file. Please check that it is a valid CSV.";

    // Short instructions keep AI costs low and limit room for prompt tricks
    private static final int MAX_INSTRUCTION_LENGTH = 300;

    private final CsvService csvService;
    private final ProfileService profileService;
    private final CleaningService cleaningService;
    private final AiPlannerService aiPlannerService;

    public CsvController(CsvService csvService,
            ProfileService profileService,
            CleaningService cleaningService,
            AiPlannerService aiPlannerService) {
        this.csvService = csvService;
        this.profileService = profileService;
        this.cleaningService = cleaningService;
        this.aiPlannerService = aiPlannerService;
    }

    // POST /api/csv/upload -> { data, profile }
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        String fileError = validateFile(file);
        if (fileError != null) {
            return error(fileError);
        }

        try {
            CsvData data = csvService.parse(file);
            return ResponseEntity.ok(new UploadResult(data, profileService.profile(data)));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(READ_ERROR);
        }
    }

    // POST /api/csv/clean -> manual mode (checkboxes)
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
            return ResponseEntity.ok(new CleanResult(cleaned, new ArrayList<>(operations), before, after));
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(READ_ERROR);
        }
    }

    // POST /api/csv/ai-clean -> AI mode: the AI picks operations from the
    // instruction
    @PostMapping("/ai-clean")
    public ResponseEntity<?> aiClean(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "instruction", required = false) String instruction) {
        String fileError = validateFile(file);
        if (fileError != null) {
            return error(fileError);
        }
        if (instruction == null || instruction.isBlank()) {
            return error("Please type an instruction, for example \"Remove duplicate rows\".");
        }
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            return error("Instruction is too long (maximum " + MAX_INSTRUCTION_LENGTH + " characters).");
        }

        // 1. Read the file and build the privacy-safe profile
        CsvData data;
        DataProfile before;
        try {
            data = csvService.parse(file);
            before = profileService.profile(data);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(READ_ERROR);
        }

        // 2. Ask the AI (it only receives the instruction + profile)
        AiDecision decision;
        try {
            decision = aiPlannerService.decide(instruction, before);
        } catch (AiUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        }

        // Nothing approved matched -> explain what is not supported
        if (decision.operations().isEmpty()) {
            String notDone = decision.notDone().isBlank() ? "" : " Not supported: " + decision.notDone() + ".";
            return error("The AI could not match your instruction to an approved cleaning operation." + notDone);
        }

        // 3. Java does the actual cleaning
        CsvData cleaned = cleaningService.clean(data, decision.operations());
        DataProfile after = profileService.profile(cleaned);

        return ResponseEntity.ok(new AiCleanResult(
                cleaned,
                new ArrayList<>(decision.operations()),
                before,
                after,
                decision.reason(),
                decision.notDone(),
                aiPlannerService.describeAiInput(instruction, before)));
    }

    // Shared file checks. Returns an error message, or null if the file is OK.
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

    // Every error has the same JSON shape: { "error": "..." }
    private ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}