package com.smartsheet.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.smartsheet.backend.model.ColumnProfile;
import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;

// Looks at the data and COUNTS problems.
// The result contains only numbers and column names — no cell values.
@Service
public class ProfileService {

    public DataProfile profile(CsvData data) {
        List<String> headers = data.headers();
        int columnCount = headers.size();

        int[] emptyCells = new int[columnCount];
        int[] cellsWithExtraSpaces = new int[columnCount];

        int duplicateRows = 0;
        int duplicateRowsIgnoringSpaces = 0;
        int emptyRows = 0;
        int rowsWithWrongColumnCount = 0;

        Set<List<String>> seenRows = new HashSet<>(); // exact rows
        Set<List<String>> seenTrimmedRows = new HashSet<>(); // rows with spaces trimmed

        for (List<String> row : data.rows()) {

            // Exact duplicate check
            if (!seenRows.add(row)) {
                duplicateRows++;
            }

            // "Would be a duplicate after trimming" check.
            // We trim a COPY of the row just for counting; the real data is not changed.
            List<String> trimmedRow = new ArrayList<>();
            for (String cell : row) {
                trimmedRow.add(cell.strip());
            }
            if (!seenTrimmedRows.add(trimmedRow)) {
                duplicateRowsIgnoringSpaces++;
            }

            if (row.size() != columnCount) {
                rowsWithWrongColumnCount++;
            }

            boolean allCellsBlank = true;

            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);

                if (!cell.isBlank()) {
                    allCellsBlank = false;
                }
                if (i >= columnCount) {
                    continue;
                }
                if (cell.isBlank()) {
                    emptyCells[i]++;
                }
                if (!cell.equals(cell.strip())) {
                    cellsWithExtraSpaces[i]++;
                }
            }

            if (allCellsBlank) {
                emptyRows++;
            }
        }

        List<ColumnProfile> columns = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            columns.add(new ColumnProfile(i, headers.get(i), emptyCells[i], cellsWithExtraSpaces[i]));
        }

        return new DataProfile(
                data.rows().size(),
                columnCount,
                duplicateRows,
                duplicateRowsIgnoringSpaces,
                emptyRows,
                rowsWithWrongColumnCount,
                columns);
    }
}