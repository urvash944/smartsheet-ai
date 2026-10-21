package com.smartsheet.backend.model;

import java.util.List;

// What /api/csv/ai-clean returns.
public record AiCleanResult(
        CsvData data,
        List<CleaningOperation> appliedOperations,
        DataProfile before,
        DataProfile after,
        String aiReason,
        String notDone, // parts of the instruction that could not be done
        String aiInput // the exact text sent to the AI
) {
}