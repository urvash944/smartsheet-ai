package com.smartsheet.backend.model.plan;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// A whole plan: the steps run in the order they are listed.
// JSON shape: { "steps": [ { ...step 1... }, { ...step 2... } ] }
@JsonIgnoreProperties(ignoreUnknown = true)
public record CleaningPlan(List<PlanStep> steps) {
}
