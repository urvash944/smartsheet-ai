package com.smartsheet.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.smartsheet.backend.model.CleaningOperation;
import com.smartsheet.backend.model.CsvData;

// Automated tests for the cleaning logic.
// Each @Test method builds a tiny table, cleans it, and checks the result.
// No Spring, no browser, no AI: just plain Java, so these run in milliseconds.
class CleaningServiceTest {

    private final CleaningService cleaningService = new CleaningService();

    // A small helper so tests are short to write
    private CsvData table(List<String> headers, List<List<String>> rows) {
        return new CsvData(headers, rows);
    }

    @Test
    void trimRunsBeforeDuplicateRemoval() {
        CsvData data = table(
                List.of("name", "city"),
                List.of(
                        List.of("Asha Patel", "Mumbai"),
                        List.of("  Asha Patel  ", "Mumbai")));

        CsvData result = cleaningService.clean(data,
                EnumSet.of(CleaningOperation.REMOVE_DUPLICATES, CleaningOperation.TRIM_WHITESPACE));

        // After trimming, both rows are identical, so only one is kept
        assertEquals(1, result.rows().size());
        assertEquals(List.of("Asha Patel", "Mumbai"), result.rows().get(0));
    }

    @Test
    void duplicateRemovalAloneKeepsRowsThatDifferOnlyBySpaces() {
        CsvData data = table(
                List.of("name"),
                List.of(List.of("Asha"), List.of(" Asha ")));

        CsvData result = cleaningService.clean(data, EnumSet.of(CleaningOperation.REMOVE_DUPLICATES));

        // Not exact copies, so both stay (we never delete rows that might be different)
        assertEquals(2, result.rows().size());
    }

    @Test
    void emptyRowsAreRemovedBeforeFilling() {
        CsvData data = table(
                List.of("name", "city"),
                List.of(
                        List.of("", ""),
                        List.of("Rahul")));

        CsvData result = cleaningService.clean(data,
                EnumSet.of(CleaningOperation.FILL_EMPTY_CELLS, CleaningOperation.REMOVE_EMPTY_ROWS));

        // The empty row is gone (not turned into "N/A,N/A"), and Rahul's missing city
        // is filled
        assertEquals(1, result.rows().size());
        assertEquals(List.of("Rahul", "N/A"), result.rows().get(0));
    }

    @Test
    void originalDataIsNeverChanged() {
        CsvData data = table(
                List.of("name"),
                List.of(List.of("  Neha  ")));

        cleaningService.clean(data, EnumSet.of(CleaningOperation.TRIM_WHITESPACE));

        // The input still has its spaces: cleaning always builds NEW lists
        assertEquals("  Neha  ", data.rows().get(0).get(0));
    }
}