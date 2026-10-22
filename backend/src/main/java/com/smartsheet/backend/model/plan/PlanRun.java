package com.smartsheet.backend.model.plan;

import java.util.List;

import com.smartsheet.backend.model.CsvData;

// The output of PlanExecutor: the cleaned data plus one report per step.
public record PlanRun(CsvData data, List<StepReport> reports) {
}
