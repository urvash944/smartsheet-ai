package com.smartsheet.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.smartsheet.backend.model.CleaningOperation;
import com.smartsheet.backend.model.CsvData;

// Performs the actual cleaning.
// It never changes the original data — it always builds NEW lists.
// This makes the code safer and easier to reason about.
@Service
public class CleaningService {

    public CsvData clean(CsvData data, Set<CleaningOperation> operations) {
        List<String> headers = data.headers();
        List<List<String>> rows = data.rows();

        // STEP 1: Trim first, so rows that differ only by spaces become identical.
        if (operations.contains(CleaningOperation.TRIM_WHITESPACE)) {
            headers = trimCells(headers);

            List<List<String>> trimmedRows = new ArrayList<>();
            for (List<String> row : rows) {
                trimmedRows.add(trimCells(row));
            }
            rows = trimmedRows;
        }

        // STEP 2: Then remove duplicates.
        if (operations.contains(CleaningOperation.REMOVE_DUPLICATES)) {
            rows = removeDuplicates(rows);
        }

        return new CsvData(headers, rows);
    }

    // Returns a new list where every cell has its leading/trailing spaces removed.
    // Spaces in the middle ("Asha Patel") are kept.
    private List<String> trimCells(List<String> cells) {
        List<String> result = new ArrayList<>();
        for (String cell : cells) {
            result.add(cell.strip());
        }
        return result;
    }

    // A LinkedHashSet keeps only unique rows AND remembers their original order.
    // So the FIRST copy of each row is kept and later copies are dropped.
    private List<List<String>> removeDuplicates(List<List<String>> rows) {
        return new ArrayList<>(new LinkedHashSet<>(rows));
    }
}