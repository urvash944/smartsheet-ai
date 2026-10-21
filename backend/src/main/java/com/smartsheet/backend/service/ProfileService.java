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
// The result (DataProfile) contains only numbers and column names — no cell values.
@Service
public class ProfileService {

    public DataProfile profile(CsvData data) {
        List<String> headers = data.headers();
        int columnCount = headers.size();

        // One counter per column. Example: emptyCells[1] = empty cells in column 1.
        int[] emptyCells = new int[columnCount];
        int[] cellsWithExtraSpaces = new int[columnCount];

        int duplicateRows = 0;
        int emptyRows = 0;
        int rowsWithWrongColumnCount = 0;

        // A Set only keeps unique items. If add() returns false,
        // we have seen this exact row before, so it is a duplicate.
        Set<List<String>> seenRows = new HashSet<>();

        for (List<String> row : data.rows()) {

            if (!seenRows.add(row)) {
                duplicateRows++;
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

                // Extra cells beyond the headers have no column to count against
                if (i >= columnCount) {
                    continue;
                }

                // isBlank() is true for "" and for " " (only spaces)
                if (cell.isBlank()) {
                    emptyCells[i]++;
                }

                // strip() removes spaces at the start and end.
                // If the stripped version is different, the cell had extra spaces.
                if (!cell.equals(cell.strip())) {
                    cellsWithExtraSpaces[i]++;
                }
            }

            if (allCellsBlank) {
                emptyRows++;
            }
        }

        // Build one ColumnProfile per column
        List<ColumnProfile> columns = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            columns.add(new ColumnProfile(i, headers.get(i), emptyCells[i], cellsWithExtraSpaces[i]));
        }

        return new DataProfile(
                data.rows().size(),
                columnCount,
                duplicateRows,
                emptyRows,
                rowsWithWrongColumnCount,
                columns);
    }
}