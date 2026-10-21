package com.smartsheet.backend.model;

// Counts for ONE column. Contains only the column's position, name, and numbers.
// Never contains any cell values.
public record ColumnProfile(
        int index, // position of the column: 0, 1, 2...
        String name, // the header text, e.g. "email"
        int emptyCells, // cells that are blank
        int cellsWithExtraSpaces // cells with spaces at the start or end
) {
}