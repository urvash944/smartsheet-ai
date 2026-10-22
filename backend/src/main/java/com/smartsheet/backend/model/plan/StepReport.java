package com.smartsheet.backend.model.plan;

// What ONE step did, shown to the user, e.g.
//   step 1, CHANGE_CASE, "Changed 4 cells in 'city' to UPPER case", rows 6 -> 6
//
// PRIVACY: the summary only ever contains column names and numbers, never cell values.
public record StepReport(
        int stepNumber,
        String action,
        String summary,
        int rowsBefore,
        int rowsAfter
) {
}
