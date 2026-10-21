package com.smartsheet.backend.model;

import java.util.List;

// What /api/csv/clean returns:
//   data              -> the cleaned headers and rows
//   appliedOperations -> which operations actually ran, in order
//   before / after    -> profiles, so the user can see what improved
public record CleanResult(
        CsvData data,
        List<CleaningOperation> appliedOperations,
        DataProfile before,
        DataProfile after) {
}