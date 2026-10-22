package com.smartsheet.backend.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;
import com.smartsheet.backend.model.plan.AiPlanResponse;
import com.smartsheet.backend.model.plan.CleaningPlan;
import com.smartsheet.backend.model.plan.PlanResult;
import com.smartsheet.backend.model.plan.PlanRun;
import com.smartsheet.backend.service.AiUnavailableException;
import com.smartsheet.backend.service.CsvService;
import com.smartsheet.backend.service.ProfileService;
import com.smartsheet.backend.service.plan.AiPlanService;
import com.smartsheet.backend.service.plan.PlanException;
import com.smartsheet.backend.service.plan.PlanExecutor;

// Endpoints for cleaning PLANS (lists of building-block steps).
//   POST /api/plan/apply   -> run a plan you give (hand-written or edited)
//   POST /api/plan/ai-plan -> Gemini writes the plan from an instruction; we return it with a preview
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private static final String READ_ERROR =
            "Could not read this file. Please check that it is a valid CSV.";
    private static final int MAX_INSTRUCTION_LENGTH = 300;

    private final CsvService csvService;
    private final ProfileService profileService;
    private final PlanExecutor planExecutor;
    private final AiPlanService aiPlanService;

    public PlanController(CsvService csvService, ProfileService profileService,
                          PlanExecutor planExecutor, AiPlanService aiPlanService) {
        this.csvService = csvService;
        this.profileService = profileService;
        this.planExecutor = planExecutor;
        this.aiPlanService = aiPlanService;
    }

    // POST /api/plan/apply  (parts: "file" = CSV, "plan" = JSON with Content-Type application/json)
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
            return error(e.getMessage());
        } catch (Exception e) {
            return error(READ_ERROR);
        }

        try {
            DataProfile before = profileService.profile(data);
            PlanRun run = planExecutor.run(data, plan);
            DataProfile after = profileService.profile(run.data());
            return ResponseEntity.ok(new PlanResult(run.data(), run.reports(), before, after));
        } catch (PlanException e) {
            return error(e.getMessage());
        }
    }

    // POST /api/plan/ai-plan  (form fields: "file" = CSV, "instruction" = text)
    @PostMapping("/ai-plan")
    public ResponseEntity<?> aiPlan(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "instruction", required = false) String instruction) {
        String fileError = validateFile(file);
        if (fileError != null) {
            return error(fileError);
        }
        if (instruction == null || instruction.isBlank()) {
            return error("Please type an instruction, for example \"Make city names uppercase\".");
        }
        if (instruction.length() > MAX_INSTRUCTION_LENGTH) {
            return error("Instruction is too long (maximum " + MAX_INSTRUCTION_LENGTH + " characters).");
        }

        CsvData data;
        try {
            data = csvService.parse(file);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            return error(READ_ERROR);
        }

        try {
            AiPlanResponse response = aiPlanService.planAndPreview(data, instruction);
            if (response.plan().steps().isEmpty()) {
                String notDone = response.notDone().isBlank() ? "" : " Not supported: " + response.notDone();
                return error("The AI could not turn this instruction into cleaning steps." + notDone);
            }
            return ResponseEntity.ok(response);
        } catch (AiUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (PlanException e) {
            return error(e.getMessage());
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
