package com.smartsheet.backend.service.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.plan.ColumnHint;

// Checks that Java detects the KIND of data in each column correctly.
class ColumnHintServiceTest {

    private final ColumnHintService service = new ColumnHintService();

    private CsvData data() {
        return new CsvData(
                List.of("email", "joined", "price", "city", "notes"),
                List.of(
                        List.of("asha@example.com", "25/08/2024", "1,250", "mumbai", ""),
                        List.of("rahul@example.com", "5/6/2024", "99.5", "PUNE", ""),
                        List.of("neha@example.com", "13/01/2023", "12", "Delhi  NCR", "")));
    }

    @Test
    void detectsTypes() {
        List<ColumnHint> hints = service.hints(data());

        assertEquals("EMAIL", hints.get(0).looksLike());
        assertEquals("DATE", hints.get(1).looksLike());
        assertEquals("NUMBER", hints.get(2).looksLike());
        assertEquals("TEXT", hints.get(3).looksLike());
        assertEquals("EMPTY", hints.get(4).looksLike());
    }

    @Test
    void detectsDayFirstDateFormat() {
        ColumnHint joined = service.hints(data()).get(1);

        // 25/08 and 13/01 can only be day-first, so DD/MM/YYYY is certain
        assertEquals("DD/MM/YYYY", joined.dateFormat());
        assertTrue(!joined.dateFormatUnclear());
    }

    @Test
    void marksUnclearDateFormat() {
        CsvData data = new CsvData(List.of("d"), List.of(List.of("01/02/2024"), List.of("03/04/2024")));

        ColumnHint hint = service.hints(data).get(0);

        // Every day is 12 or less: could be 1 Feb or Jan 2
        assertTrue(hint.dateFormatUnclear());
    }

    @Test
    void countsLetterCaseAndDoubleSpaces() {
        ColumnHint city = service.hints(data()).get(3);

        assertEquals(1, city.lowercaseCells());   // mumbai
        assertEquals(1, city.uppercaseCells());   // PUNE
        assertEquals(1, city.doubleSpaceCells()); // "Delhi  NCR"
    }

    @Test
    void hintsNeverContainValues() {
        String everything = service.hints(data()).toString();

        assertTrue(!everything.contains("asha@example.com"));
        assertTrue(!everything.contains("mumbai"));
    }
}
