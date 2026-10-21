package com.smartsheet.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.smartsheet.backend.model.CleaningOperation;
import com.smartsheet.backend.model.CsvData;

// Performs the actual cleaning. Always builds NEW lists; never changes the original data.
@Service
public class CleaningService {

    // The value we put into blank or missing cells
    public static final String FILL_VALUE = "N/A";

    public CsvData clean(CsvData data, Set<CleaningOperation> operations) {
        List<String> headers = data.headers();
        List<List<String>> rows = data.rows();

        // The order is fixed here, no matter what order the user or AI asked for.

        // 1. Trim first: " " becomes "" and near-duplicates become identical
        if (operations.contains(CleaningOperation.TRIM_WHITESPACE)) {
            headers = trimCells(headers);
            List<List<String>> trimmedRows = new ArrayList<>();
            for (List<String> row : rows) {
                trimmedRows.add(trimCells(row));
            }
            rows = trimmedRows;
        }

        // 2. Remove fully empty rows BEFORE filling (otherwise they'd become
        // N/A,N/A,N/A)
        if (operations.contains(CleaningOperation.REMOVE_EMPTY_ROWS)) {
            rows = removeEmptyRows(rows);
        }

        // 3. Fill blank cells, and add missing cells to short rows
        if (operations.contains(CleaningOperation.FILL_EMPTY_CELLS)) {
            rows = fillEmptyCells(rows, headers.size());
        }

        // 4. Remove duplicates last, so it catches rows that became identical
        if (operations.contains(CleaningOperation.REMOVE_DUPLICATES)) {
            rows = removeDuplicates(rows);
        }

        return new CsvData(headers, rows);
    }

    private List<String> trimCells(List<String> cells) {
        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cell.strip());
        }
        return result;
    }

    // Keeps a row only if at least one cell has real content
    private List<List<String>> removeEmptyRows(List<List<String>> rows) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> row : rows) {
            boolean hasContent = false;
            for (String cell : row) {
                if (!cell.isBlank()) {
                    hasContent = true;
                    break;
                }
            }
            if (hasContent) {
                result.add(row);
            }
        }
        return result;
    }

    // Replaces blank cells with N/A, and pads short rows up to the number of
    // columns.
    // Rows with TOO MANY cells are left as they are (we never delete data).
    private List<List<String>> fillEmptyCells(List<List<String>> rows, int columnCount) {
        List<List<String>> result = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> filled = new ArrayList<>();
            for (String cell : row) {
                filled.add(cell.isBlank() ? FILL_VALUE : cell);
            }
            while (filled.size() < columnCount) {
                filled.add(FILL_VALUE);
            }
            result.add(filled);
        }
        return result;
    }

    // LinkedHashSet keeps the FIRST copy of each row, in the original order
    private List<List<String>> removeDuplicates(List<List<String>> rows) {
        return new ArrayList<>(new LinkedHashSet<>(rows));
    }
}