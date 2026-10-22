package com.smartsheet.backend.model.plan;

// What KIND of data a column holds, worked out by Java. Sent to the AI instead of real values.
//
// Example: { name: "joined", looksLike: "DATE", matchPercent: 100, dateFormat: "DD/MM/YYYY" }
//
// PRIVACY: only the column name, a type word, a date FORMAT and counts. Never a cell value.
public record ColumnHint(
        int index,
        String name,
        int filledCells,        // cells that are not blank
        String looksLike,       // NUMBER | DATE | EMAIL | TEXT | EMPTY
        int matchPercent,       // how many filled cells fit that type (0-100)
        String dateFormat,      // e.g. "DD/MM/YYYY" when looksLike = DATE, otherwise null
        boolean dateFormatUnclear, // true if the dates could be DD/MM or MM/DD (all days are 12 or less)
        int lowercaseCells,     // cells written all in lowercase, e.g. "mumbai"
        int uppercaseCells,     // cells written ALL IN CAPITALS, e.g. "MUMBAI"
        int doubleSpaceCells    // cells with 2+ spaces in a row inside the text
) {
}
