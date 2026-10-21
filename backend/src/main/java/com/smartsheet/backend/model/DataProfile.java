package com.smartsheet.backend.model;

import java.util.List;

// A privacy-safe summary of the whole file: ONLY numbers and column names.
public record DataProfile(
        int totalRows, // data rows (not counting the header)
        int totalColumns, // number of headers
        int duplicateRows, // rows that exactly copy an earlier row
        int duplicateRowsIgnoringSpaces, // rows that WOULD be duplicates after trimming spaces
        int emptyRows, // rows where every cell is blank, e.g. ",,"
        int rowsWithWrongColumnCount, // rows with more or fewer cells than headers
        List<ColumnProfile> columns // per-column counts
) {
}