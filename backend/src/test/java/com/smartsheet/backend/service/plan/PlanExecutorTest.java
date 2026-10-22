package com.smartsheet.backend.service.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.plan.CleaningPlan;
import com.smartsheet.backend.model.plan.PlanRun;
import com.smartsheet.backend.model.plan.PlanStep;

// Tests for the building blocks. Plain Java: no Spring, no AI, runs in milliseconds.
class PlanExecutorTest {

    private final PlanExecutor executor = new PlanExecutor();

    // A small sample table used by most tests
    private CsvData people() {
        return new CsvData(
                List.of("name", "email", "city", "joined"),
                List.of(
                        List.of("asha patel", "asha@example.com", "mumbai", "15/08/2024"),
                        List.of("Rahul Sharma", "", "Pune", "01/01/2023"),
                        List.of("ASHA PATEL", "asha@example.com", "Mumbai", "15/08/2024"),
                        List.of("", "", "", "")));
    }

    // Helper: build a step with only the fields we need
    private PlanStep step(String action, String column, String option, String text, String newText) {
        return new PlanStep(action, column, null, option, text, newText);
    }

    private PlanRun run(CsvData data, PlanStep... steps) {
        return executor.run(data, new CleaningPlan(List.of(steps)));
    }

    @Test
    void changeCaseOnOneColumnOnly() {
        PlanRun result = run(people(), step("CHANGE_CASE", "city", "UPPER", null, null));

        assertEquals("MUMBAI", result.data().rows().get(0).get(2));
        assertEquals("asha patel", result.data().rows().get(0).get(0)); // other columns untouched
    }

    @Test
    void titleCaseFixesMixedCapitals() {
        PlanRun result = run(people(), step("CHANGE_CASE", "name", "TITLE", null, null));

        assertEquals("Asha Patel", result.data().rows().get(2).get(0));
    }

    @Test
    void deleteRowsWhereEmailIsEmpty() {
        PlanRun result = run(people(), step("DELETE_ROWS_IF", "email", "IS_EMPTY", null, null));

        assertEquals(2, result.data().rows().size());
        assertEquals(4, result.reports().get(0).rowsBefore());
        assertEquals(2, result.reports().get(0).rowsAfter());
    }

    @Test
    void laterStepsSeeRenamedColumns() {
        PlanRun result = run(people(),
                step("RENAME_COLUMN", "name", null, null, "full_name"),
                step("CHANGE_CASE", "full_name", "UPPER", null, null));

        assertEquals("full_name", result.data().headers().get(0));
        assertEquals("ASHA PATEL", result.data().rows().get(0).get(0));
    }

    @Test
    void unknownColumnGivesClearErrorWithoutData() {
        PlanException error = assertThrows(PlanException.class,
                () -> run(people(), step("CHANGE_CASE", "cty", "UPPER", null, null)));

        assertTrue(error.getMessage().startsWith("Step 1: column 'cty' does not exist"));
        // PRIVACY: the message lists column names, never cell values
        assertTrue(!error.getMessage().contains("asha@example.com"));
    }

    @Test
    void unknownActionIsRefused() {
        PlanException error = assertThrows(PlanException.class,
                () -> run(people(), step("DELETE_EVERYTHING", null, null, null, null)));

        assertTrue(error.getMessage().contains("is not an approved action"));
    }

    @Test
    void formatDateConvertsAndCountsFailures() {
        CsvData data = new CsvData(List.of("joined"),
                List.of(List.of("15/08/2024"), List.of("not a date"), List.of("31/02/2024")));

        PlanRun result = run(data, step("FORMAT_DATE", "joined", "YYYY-MM-DD", "DD/MM/YYYY", null));

        assertEquals("2024-08-15", result.data().rows().get(0).get(0));
        assertEquals("not a date", result.data().rows().get(1).get(0)); // left unchanged
        assertEquals("31/02/2024", result.data().rows().get(2).get(0)); // impossible date, unchanged
        assertTrue(result.reports().get(0).summary().contains("2 cell(s) did not match"));
    }

    @Test
    void splitFullNameIntoTwoColumns() {
        CsvData data = new CsvData(List.of("name", "city"),
                List.of(List.of("Asha Patel", "Mumbai"), List.of("Neha", "Delhi")));

        PlanRun result = run(data, step("SPLIT_COLUMN", "name", null, " ", "first_name,last_name"));

        assertEquals(List.of("first_name", "last_name", "city"), result.data().headers());
        assertEquals(List.of("Asha", "Patel", "Mumbai"), result.data().rows().get(0));
        assertEquals(List.of("Neha", "", "Delhi"), result.data().rows().get(1));
    }

    @Test
    void mergeColumnsSkipsEmptyParts() {
        CsvData data = new CsvData(List.of("first", "last", "city"),
                List.of(List.of("Asha", "Patel", "Mumbai"), List.of("Neha", "", "Delhi")));

        PlanRun result = executor.run(data, new CleaningPlan(List.of(
                new PlanStep("MERGE_COLUMNS", null, List.of("first", "last"), null, " ", "name"))));

        assertEquals(List.of("name", "city"), result.data().headers());
        assertEquals(List.of("Asha Patel", "Mumbai"), result.data().rows().get(0));
        assertEquals(List.of("Neha", "Delhi"), result.data().rows().get(1)); // no extra space
    }

    @Test
    void sortNumbersAsNumbers() {
        CsvData data = new CsvData(List.of("item", "price"),
                List.of(List.of("a", "100"), List.of("b", "9"), List.of("c", ""), List.of("d", "25")));

        PlanRun result = run(data, step("SORT_ROWS", "price", "ASC", null, null));

        // As text, "100" would come before "9". As numbers: 9, 25, 100, then the empty cell last.
        assertEquals("b", result.data().rows().get(0).get(0));
        assertEquals("d", result.data().rows().get(1).get(0));
        assertEquals("a", result.data().rows().get(2).get(0));
        assertEquals("c", result.data().rows().get(3).get(0));
    }

    @Test
    void removeDuplicatesIgnoringCaseByChosenColumns() {
        PlanRun result = executor.run(people(), new CleaningPlan(List.of(
                new PlanStep("REMOVE_DUPLICATES", null, List.of("name", "email"), "IGNORE_CASE", null, null))));

        // "asha patel" and "ASHA PATEL" with the same email are now the same person
        assertEquals(3, result.data().rows().size());
    }

    @Test
    void findReplaceIsLiteralNotRegex() {
        CsvData data = new CsvData(List.of("price"), List.of(List.of("1.000"), List.of("1a000")));

        PlanRun result = run(data, step("FIND_REPLACE", "price", null, ".", ""));

        // "." is NOT a regex "any character": only the real dot is removed
        assertEquals("1000", result.data().rows().get(0).get(0));
        assertEquals("1a000", result.data().rows().get(1).get(0));
    }

    @Test
    void fillEmptyPadsShortRows() {
        CsvData data = new CsvData(List.of("name", "city"), List.of(List.of("Rahul")));

        PlanRun result = run(data, step("FILL_EMPTY", null, null, "Unknown", null));

        assertEquals(List.of("Rahul", "Unknown"), result.data().rows().get(0));
    }

    @Test
    void originalDataIsNeverChanged() {
        CsvData data = people();

        run(data, step("CHANGE_CASE", null, "UPPER", null, null), step("REMOVE_EMPTY_ROWS", null, null, null, null));

        assertEquals("asha patel", data.rows().get(0).get(0));
        assertEquals(4, data.rows().size());
    }

    @Test
    void tooManyStepsIsRefused() {
        List<PlanStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < PlanExecutor.MAX_STEPS + 1; i++) {
            steps.add(step("TRIM_WHITESPACE", null, null, null, null));
        }
        assertThrows(PlanException.class, () -> executor.run(people(), new CleaningPlan(steps)));
    }
}
