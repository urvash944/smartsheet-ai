package com.smartsheet.backend.model;

// What /api/csv/upload returns: the data (for the preview) and its profile.
public record UploadResult(CsvData data, DataProfile profile) {
}