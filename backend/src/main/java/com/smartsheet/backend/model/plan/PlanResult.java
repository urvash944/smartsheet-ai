package com.smartsheet.backend.model.plan;

import java.util.List;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;

// What /api/plan/apply returns:
//   data   -> the cleaned headers and rows
//   steps  -> what each step did
//   before / after -> data health counts, to compare
public record PlanResult(
        CsvData data,
        List<StepReport> steps,
        DataProfile before,
        DataProfile after
) {
}
