package com.smartsheet.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;
import com.smartsheet.backend.model.plan.CleaningPlan;
import com.smartsheet.backend.model.plan.PlanResult;
import com.smartsheet.backend.model.plan.PlanRun;
import com.smartsheet.backend.service.CsvService;
import com.smartsheet.backend.service.ProfileService;
import com.smartsheet.backend.service.plan.PlanException;
import com.smartsheet.backend.service.plan.PlanExecutor;

// Endpoints for cleaning PLANS (lists of building-block steps).
// In Step C1 the plan is written by hand. In Step C2 Gemini will write it.
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private static final String READ_ERROR =
            "Could not read this file. Please check that it is a valid CSV.";

    private final CsvService csvService;
    private final ProfileService profileService;
    private final PlanExecutor planExecutor;

    public PlanController(CsvService csvService, ProfileService profileService, PlanExecutor planExecutor) {
        this.csvService = csvService;
        this.profileService = profileService;
        this.planExecutor = planExecutor;
    }

    // POST /api/plan/apply
    // A multipart request with two parts:
    //   "file" -> the CSV file
    //   "plan" -> the plan as JSON (Content-Type: application/json)
    // @RequestPart lets Spring turn the JSON part straight into a CleaningPlan record.
    @PostMapping("/apply")
    public ResponseEntity<?> apply(@RequestPart("file") MultipartFile file,
                                   @RequestPart("plan") CleaningPlan plan) {
        String fileError = validateFile(file);
        if (fileError != null) {
            return error(fileError);
        }

        CsvData data;
        try {
            data = csvService.parse(file);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage()); // e.g. "The CSV file is empty."
        } catch (Exception e) {
            return error(READ_ERROR);
        }

        try {
            DataProfile before = profileService.profile(data);
            PlanRun run = planExecutor.run(data, plan);
            DataProfile after = profileService.profile(run.data());
            return ResponseEntity.ok(new PlanResult(run.data(), run.reports(), before, after));
        } catch (PlanException e) {
            return error(e.getMessage()); // e.g. "Step 2: column 'cty' does not exist."
        }
    }

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

    private ResponseEntity<Map<String, String>> error(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
