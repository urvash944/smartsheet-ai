package com.smartsheet.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.DataProfile;

// Checks that the profiler counts problems correctly.
// The table below is the same as sample-data/messy.csv.
class ProfileServiceTest {

    private final ProfileService profileService = new ProfileService();

    @Test
    void countsProblemsInMessyFile() {
        CsvData messy = new CsvData(
                List.of("name", "email", "city"),
                List.of(
                        List.of("Asha Patel", "asha@example.com", "Mumbai"),
                        List.of("  Asha Patel  ", "asha@example.com", "Mumbai"),
                        List.of("", "", ""),
                        List.of("Rahul Sharma", "rahul@example.com"),
                        List.of("Neha Singh", "", "Delhi")));

        DataProfile profile = profileService.profile(messy);

        assertEquals(5, profile.totalRows());
        assertEquals(3, profile.totalColumns());
        assertEquals(0, profile.duplicateRows());
        assertEquals(1, profile.duplicateRowsIgnoringSpaces());
        assertEquals(1, profile.emptyRows());
        assertEquals(1, profile.rowsWithWrongColumnCount());

        int emptyCells = profile.columns().stream().mapToInt(c -> c.emptyCells()).sum();
        int extraSpaces = profile.columns().stream().mapToInt(c -> c.cellsWithExtraSpaces()).sum();
        assertEquals(4, emptyCells);
        assertEquals(1, extraSpaces);
    }

    @Test
    void profileContainsOnlyColumnNamesNotCellValues() {
        CsvData data = new CsvData(
                List.of("email"),
                List.of(List.of("secret@example.com")));

        DataProfile profile = profileService.profile(data);

        // PRIVACY: the summary must never contain a cell value
        assertEquals(false, profile.toString().contains("secret@example.com"));
    }
}