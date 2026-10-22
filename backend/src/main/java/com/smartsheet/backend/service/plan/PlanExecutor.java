package com.smartsheet.backend.service.plan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.smartsheet.backend.model.CsvData;
import com.smartsheet.backend.model.plan.CleaningPlan;
import com.smartsheet.backend.model.plan.PlanAction;
import com.smartsheet.backend.model.plan.PlanRun;
import com.smartsheet.backend.model.plan.PlanStep;
import com.smartsheet.backend.model.plan.StepReport;

// Runs a cleaning plan step by step.
//
// SAFETY:
//   - Each step is checked right before it runs, against the CURRENT columns.
//     (A step may rename or delete columns, so later steps must see the new names.)
//   - Only actions in PlanAction can run. Unknown actions are refused.
//   - No step can run code or use regex patterns from the plan: text is always matched literally.
//
// PRIVACY:
//   - Error messages and step summaries contain only step numbers, column names and counts.
//
// It never changes the input data: it works on a copy.
@Service
public class PlanExecutor {

    public static final int MAX_STEPS = 20;
    public static final int MAX_TEXT_LENGTH = 100;
    public static final String DEFAULT_FILL = "N/A";

    // Date formats people can choose from, mapped to Java's date pattern letters.
    // "uuuu" = year; STRICT checking means "31/02/2024" is rejected, not silently changed.
    private static final Map<String, String> DATE_FORMATS = Map.of(
            "YYYY-MM-DD", "uuuu-MM-dd",
            "DD-MM-YYYY", "dd-MM-uuuu",
            "DD/MM/YYYY", "dd/MM/uuuu",
            "MM/DD/YYYY", "MM/dd/uuuu",
            "DD.MM.YYYY", "dd.MM.uuuu");

    public PlanRun run(CsvData input, CleaningPlan plan) {
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            throw new PlanException("The plan has no steps.");
        }
        if (plan.steps().size() > MAX_STEPS) {
            throw new PlanException("A plan can have at most " + MAX_STEPS + " steps.");
        }

        // Work on a COPY so the original data is never changed
        Table table = new Table(input);
        List<StepReport> reports = new ArrayList<>();

        for (int i = 0; i < plan.steps().size(); i++) {
            int stepNumber = i + 1;
            PlanStep step = plan.steps().get(i);
            if (step == null) {
                throw new PlanException(stepNumber, "the step is empty.");
            }

            PlanAction action = parseAction(stepNumber, step.action());
            checkTextLengths(stepNumber, step);

            int rowsBefore = table.rows.size();
            String summary = runStep(stepNumber, action, step, table);
            reports.add(new StepReport(stepNumber, action.name(), summary, rowsBefore, table.rows.size()));
        }

        return new PlanRun(table.toCsvData(), reports);
    }

    // ---------------------------------------------------------------------
    // One switch decides which building block runs. Each block returns a short summary.
    // ---------------------------------------------------------------------
    private String runStep(int n, PlanAction action, PlanStep step, Table t) {
        return switch (action) {
            case TRIM_WHITESPACE -> trim(n, step, t);
            case COLLAPSE_SPACES -> collapseSpaces(n, step, t);
            case CHANGE_CASE -> changeCase(n, step, t);
            case FIND_REPLACE -> findReplace(n, step, t);
            case FILL_EMPTY -> fillEmpty(n, step, t);
            case REMOVE_EMPTY_ROWS -> removeEmptyRows(t);
            case DELETE_ROWS_IF -> deleteRowsIf(n, step, t);
            case DELETE_COLUMN -> deleteColumn(n, step, t);
            case RENAME_COLUMN -> renameColumn(n, step, t);
            case SPLIT_COLUMN -> splitColumn(n, step, t);
            case MERGE_COLUMNS -> mergeColumns(n, step, t);
            case FORMAT_DATE -> formatDate(n, step, t);
            case SORT_ROWS -> sortRows(n, step, t);
            case REMOVE_DUPLICATES -> removeDuplicates(n, step, t);
        };
    }

    // ---------- Text blocks ----------

    private String trim(int n, PlanStep step, Table t) {
        List<Integer> cols = targetColumns(n, step, t);
        if (step.column() == null || step.column().isBlank()) {
            // Trimming the whole file also cleans the column names
            t.headers.replaceAll(String::strip);
        }
        int changed = t.changeCells(cols, String::strip);
        return "Trimmed spaces in " + changed + " cell(s) in " + describe(cols, t) + ".";
    }

    private String collapseSpaces(int n, PlanStep step, Table t) {
        List<Integer> cols = targetColumns(n, step, t);
        // [ \t ]{2,} = two or more spaces, tabs or non-breaking spaces (line breaks are kept)
        int changed = t.changeCells(cols, cell -> cell.replaceAll("[ \\t\\u00A0]{2,}", " "));
        return "Collapsed repeated spaces in " + changed + " cell(s) in " + describe(cols, t) + ".";
    }

    private String changeCase(int n, PlanStep step, Table t) {
        List<Integer> cols = targetColumns(n, step, t);
        String option = requireOption(n, step, "UPPER", "LOWER", "TITLE");
        int changed = t.changeCells(cols, cell -> switch (option) {
            case "UPPER" -> cell.toUpperCase(Locale.ROOT);
            case "LOWER" -> cell.toLowerCase(Locale.ROOT);
            default -> toTitleCase(cell);
        });
        return "Changed " + changed + " cell(s) in " + describe(cols, t) + " to " + option + " case.";
    }

    private String findReplace(int n, PlanStep step, Table t) {
        List<Integer> cols = targetColumns(n, step, t);
        String find = requireText(n, step.text(), "text (what to find)");
        String replacement = step.newText() == null ? "" : step.newText();
        boolean ignoreCase = "IGNORE_CASE".equalsIgnoreCase(clean(step.option()));

        // SAFETY: Pattern.quote treats the text literally, so it can never act as a regex pattern.
        Pattern pattern = Pattern.compile(Pattern.quote(find), ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        String safeReplacement = Matcher.quoteReplacement(replacement);

        int changed = t.changeCells(cols, cell -> pattern.matcher(cell).replaceAll(safeReplacement));
        return "Replaced text in " + changed + " cell(s) in " + describe(cols, t) + ".";
    }

    private String fillEmpty(int n, PlanStep step, Table t) {
        List<Integer> cols = targetColumns(n, step, t);
        String value = (step.text() == null || step.text().isBlank()) ? DEFAULT_FILL : step.text();
        t.padRows(); // missing cells at the end of short rows become blank cells first
        int changed = t.changeCells(cols, cell -> cell.isBlank() ? value : cell);
        return "Filled " + changed + " empty cell(s) in " + describe(cols, t) + ".";
    }

    // ---------- Row blocks ----------

    private String removeEmptyRows(Table t) {
        int before = t.rows.size();
        t.rows.removeIf(row -> row.stream().allMatch(String::isBlank));
        return "Removed " + (before - t.rows.size()) + " empty row(s).";
    }

    private String deleteRowsIf(int n, PlanStep step, Table t) {
        int col = requireColumn(n, step.column(), t);
        String option = requireOption(n, step, "IS_EMPTY", "EQUALS", "CONTAINS");
        String value = option.equals("IS_EMPTY") ? "" : requireText(n, step.text(), "text (value to compare)");
        String lowerValue = value.strip().toLowerCase(Locale.ROOT);

        int before = t.rows.size();
        t.rows.removeIf(row -> {
            String cell = Table.cell(row, col).strip();
            return switch (option) {
                case "IS_EMPTY" -> cell.isEmpty();
                case "EQUALS" -> cell.equalsIgnoreCase(value.strip());
                default -> cell.toLowerCase(Locale.ROOT).contains(lowerValue);
            };
        });
        String rule = option.equals("IS_EMPTY") ? "is empty" : (option.equals("EQUALS") ? "equals the given value" : "contains the given value");
        return "Deleted " + (before - t.rows.size()) + " row(s) where '" + t.headers.get(col) + "' " + rule + ".";
    }

    private String sortRows(int n, PlanStep step, Table t) {
        int col = requireColumn(n, step.column(), t);
        String option = step.option() == null || step.option().isBlank() ? "ASC" : requireOption(n, step, "ASC", "DESC");

        // Numbers sort as numbers only if EVERY non-empty cell in the column is a number
        boolean numeric = t.rows.stream()
                .map(row -> Table.cell(row, col).strip())
                .filter(cell -> !cell.isEmpty())
                .allMatch(PlanExecutor::isNumber);

        Comparator<String> byValue = numeric
                ? Comparator.comparingDouble(Double::parseDouble)
                : String.CASE_INSENSITIVE_ORDER;
        if (option.equals("DESC")) {
            byValue = byValue.reversed();
        }
        Comparator<String> valueOrder = byValue;

        // Empty cells always go last. List.sort is "stable": equal rows keep their old order.
        t.rows.sort((a, b) -> {
            String x = Table.cell(a, col).strip();
            String y = Table.cell(b, col).strip();
            if (x.isEmpty() || y.isEmpty()) {
                return Boolean.compare(x.isEmpty(), y.isEmpty());
            }
            return valueOrder.compare(x, y);
        });
        return "Sorted " + t.rows.size() + " row(s) by '" + t.headers.get(col) + "' "
                + (option.equals("ASC") ? "ascending" : "descending") + (numeric ? " (as numbers)." : " (as text).");
    }

    private String removeDuplicates(int n, PlanStep step, Table t) {
        boolean ignoreCase = "IGNORE_CASE".equalsIgnoreCase(clean(step.option()));
        List<Integer> keyCols = (step.columns() == null || step.columns().isEmpty())
                ? null // null = compare whole rows
                : requireColumns(n, step.columns(), t);

        Set<List<String>> seen = new LinkedHashSet<>();
        int before = t.rows.size();
        t.rows.removeIf(row -> {
            List<String> key = new ArrayList<>();
            if (keyCols == null) {
                key.addAll(row);
            } else {
                for (int c : keyCols) {
                    key.add(Table.cell(row, c));
                }
            }
            if (ignoreCase) {
                key.replaceAll(cell -> cell.toLowerCase(Locale.ROOT));
            }
            return !seen.add(key); // add() is false if we saw this key before -> remove the row
        });
        String scope = keyCols == null ? "whole rows" : describe(keyCols, t);
        return "Removed " + (before - t.rows.size()) + " duplicate row(s), comparing " + scope
                + (ignoreCase ? ", ignoring capital letters." : ".");
    }

    // ---------- Column blocks ----------

    private String deleteColumn(int n, PlanStep step, Table t) {
        int col = requireColumn(n, step.column(), t);
        String name = t.headers.get(col);
        if (t.headers.size() == 1) {
            throw new PlanException(n, "cannot delete the only column.");
        }
        t.headers.remove(col);
        for (List<String> row : t.rows) {
            if (col < row.size()) {
                row.remove(col);
            }
        }
        return "Deleted column '" + name + "'.";
    }

    private String renameColumn(int n, PlanStep step, Table t) {
        int col = requireColumn(n, step.column(), t);
        String newName = requireText(n, step.newText(), "newText (the new column name)").strip();
        t.requireNewName(n, newName, col);
        String oldName = t.headers.get(col);
        t.headers.set(col, newName);
        return "Renamed column '" + oldName + "' to '" + newName + "'.";
    }

    private String splitColumn(int n, PlanStep step, Table t) {
        int col = requireColumn(n, step.column(), t);
        String original = t.headers.get(col);
        String separator = (step.text() == null || step.text().isEmpty()) ? " " : step.text();

        String firstName = original + "_1";
        String secondName = original + "_2";
        if (step.newText() != null && !step.newText().isBlank()) {
            String[] names = step.newText().split(",");
            if (names.length != 2 || names[0].isBlank() || names[1].isBlank()) {
                throw new PlanException(n, "newText must be two column names separated by a comma.");
            }
            firstName = names[0].strip();
            secondName = names[1].strip();
        }
        if (firstName.equalsIgnoreCase(secondName)) {
            throw new PlanException(n, "the two new columns need different names.");
        }
        t.requireNewName(n, firstName, col);
        t.requireNewName(n, secondName, col);

        int split = 0;
        t.padRows();
        for (List<String> row : t.rows) {
            String cell = row.get(col);
            int at = cell.indexOf(separator);
            String first = at < 0 ? cell : cell.substring(0, at);
            String second = at < 0 ? "" : cell.substring(at + separator.length());
            if (at >= 0) {
                split++;
            }
            row.set(col, first.strip());
            row.add(col + 1, second.strip());
        }
        t.headers.set(col, firstName);
        t.headers.add(col + 1, secondName);
        return "Split column '" + original + "' into '" + firstName + "' and '" + secondName + "' (" + split + " cell(s) had the separator).";
    }

    private String mergeColumns(int n, PlanStep step, Table t) {
        if (step.columns() == null || step.columns().size() < 2) {
            throw new PlanException(n, "MERGE_COLUMNS needs at least 2 columns in 'columns'.");
        }
        List<Integer> cols = requireColumns(n, step.columns(), t);
        if (new LinkedHashSet<>(cols).size() != cols.size()) {
            throw new PlanException(n, "the same column is listed twice.");
        }
        String separator = step.text() == null ? " " : step.text();
        List<String> names = new ArrayList<>();
        for (int c : cols) {
            names.add(t.headers.get(c));
        }
        String newName = (step.newText() == null || step.newText().isBlank())
                ? String.join("_", names)
                : step.newText().strip();

        // The new name may reuse a merged column's name (those columns disappear),
        // but must not clash with any OTHER column.
        for (int i = 0; i < t.headers.size(); i++) {
            if (!cols.contains(i) && t.headers.get(i).strip().equalsIgnoreCase(newName)) {
                throw new PlanException(n, "a column named '" + newName + "' already exists.");
            }
        }

        // The new column goes where the first listed column was; the other merged columns are removed.
        int target = cols.get(0);
        t.padRows();
        for (List<String> row : t.rows) {
            List<String> parts = new ArrayList<>();
            for (int c : cols) {
                String value = row.get(c).strip();
                if (!value.isEmpty()) {
                    parts.add(value); // skip empty parts, so we never get double separators
                }
            }
            row.set(target, String.join(separator, parts));
        }
        t.headers.set(target, newName);

        // Remove the other merged columns, from right to left so earlier positions stay correct
        List<Integer> toRemove = new ArrayList<>(cols.subList(1, cols.size()));
        toRemove.sort(Comparator.reverseOrder());
        for (int c : toRemove) {
            t.headers.remove(c);
            for (List<String> row : t.rows) {
                row.remove(c);
            }
        }
        return "Merged " + names.size() + " columns into '" + newName + "'.";
    }

    private String formatDate(int n, PlanStep step, Table t) {
        int col = requireColumn(n, step.column(), t);
        String fromName = clean(step.text()).toUpperCase(Locale.ROOT);
        String toName = clean(step.option()).toUpperCase(Locale.ROOT);
        if (!DATE_FORMATS.containsKey(fromName) || !DATE_FORMATS.containsKey(toName)) {
            throw new PlanException(n, "date formats must be one of " + DATE_FORMATS.keySet()
                    + " (text = current format, option = new format).");
        }
        DateTimeFormatter from = DateTimeFormatter.ofPattern(DATE_FORMATS.get(fromName)).withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter to = DateTimeFormatter.ofPattern(DATE_FORMATS.get(toName)).withResolverStyle(ResolverStyle.STRICT);

        int converted = 0;
        int failed = 0;
        for (List<String> row : t.rows) {
            if (col >= row.size() || row.get(col).isBlank()) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(row.get(col).strip(), from);
                row.set(col, date.format(to));
                converted++;
            } catch (DateTimeParseException e) {
                failed++; // not in the expected format: leave the cell exactly as it was
            }
        }
        return "Changed " + converted + " date(s) in '" + t.headers.get(col) + "' from " + fromName + " to " + toName
                + (failed > 0 ? "; " + failed + " cell(s) did not match " + fromName + " and were left unchanged." : ".");
    }

    // ---------------------------------------------------------------------
    // Checks used by the blocks
    // ---------------------------------------------------------------------

    private PlanAction parseAction(int n, String name) {
        if (name == null || name.isBlank()) {
            throw new PlanException(n, "the action is missing.");
        }
        try {
            return PlanAction.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PlanException(n, "'" + shorten(name) + "' is not an approved action.");
        }
    }

    private void checkTextLengths(int n, PlanStep step) {
        for (String value : new String[] {step.text(), step.newText(), step.option(), step.column()}) {
            if (value != null && value.length() > MAX_TEXT_LENGTH) {
                throw new PlanException(n, "a setting is longer than " + MAX_TEXT_LENGTH + " characters.");
            }
        }
        if (step.columns() != null && step.columns().size() > 50) {
            throw new PlanException(n, "too many columns listed.");
        }
    }

    // column given -> just that column; no column -> all columns
    private List<Integer> targetColumns(int n, PlanStep step, Table t) {
        if (step.column() == null || step.column().isBlank()) {
            List<Integer> all = new ArrayList<>();
            for (int i = 0; i < t.headers.size(); i++) {
                all.add(i);
            }
            return all;
        }
        return List.of(requireColumn(n, step.column(), t));
    }

    private int requireColumn(int n, String name, Table t) {
        if (name == null || name.isBlank()) {
            throw new PlanException(n, "this action needs a column.");
        }
        int index = t.findColumn(name);
        if (index < 0) {
            throw new PlanException(n, "column '" + shorten(name) + "' does not exist. Columns: " + String.join(", ", t.headers));
        }
        return index;
    }

    private List<Integer> requireColumns(int n, List<String> names, Table t) {
        List<Integer> result = new ArrayList<>();
        for (String name : names) {
            result.add(requireColumn(n, name, t));
        }
        return result;
    }

    private String requireOption(int n, PlanStep step, String... allowed) {
        String option = clean(step.option()).toUpperCase(Locale.ROOT);
        for (String a : allowed) {
            if (a.equals(option)) {
                return option;
            }
        }
        throw new PlanException(n, "option must be one of " + String.join(", ", allowed) + ".");
    }

    private String requireText(int n, String value, String what) {
        if (value == null || value.isEmpty()) {
            throw new PlanException(n, "this action needs " + what + ".");
        }
        return value;
    }

    // ---------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String shorten(String value) {
        String v = value.strip();
        return v.length() > 40 ? v.substring(0, 40) + "..." : v;
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String describe(List<Integer> cols, Table t) {
        if (cols.size() == t.headers.size() && cols.size() > 1) {
            return "all columns";
        }
        List<String> names = new ArrayList<>();
        for (int c : cols) {
            names.add("'" + t.headers.get(c) + "'");
        }
        return String.join(", ", names);
    }

    // "rahul sHARMA" -> "Rahul Sharma": first letter of each word upper, the rest lower
    static String toTitleCase(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean startOfWord = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                startOfWord = true;
                result.append(c);
            } else if (startOfWord) {
                result.append(Character.toUpperCase(c));
                startOfWord = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    // ---------------------------------------------------------------------
    // A changeable copy of the table, used only while a plan runs
    // ---------------------------------------------------------------------
    private static final class Table {
        final List<String> headers;
        final List<List<String>> rows;

        Table(CsvData data) {
            headers = new ArrayList<>(data.headers());
            rows = new ArrayList<>();
            for (List<String> row : data.rows()) {
                rows.add(new ArrayList<>(row)); // copy each row
            }
        }

        static String cell(List<String> row, int col) {
            return col < row.size() ? row.get(col) : "";
        }

        // Exact name first, then ignoring capitals and spaces at the ends
        int findColumn(String name) {
            int exact = headers.indexOf(name);
            if (exact >= 0) {
                return exact;
            }
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).strip().equalsIgnoreCase(name.strip())) {
                    return i;
                }
            }
            return -1;
        }

        // A new column name must not be empty and must not clash with another column
        void requireNewName(int n, String newName, int ownPosition) {
            if (newName.isBlank()) {
                throw new PlanException(n, "the new column name is empty.");
            }
            for (int i = 0; i < headers.size(); i++) {
                if (i != ownPosition && headers.get(i).strip().equalsIgnoreCase(newName.strip())) {
                    throw new PlanException(n, "a column named '" + newName + "' already exists.");
                }
            }
        }

        // Short rows get blank cells at the end, so every column position exists
        void padRows() {
            for (List<String> row : rows) {
                while (row.size() < headers.size()) {
                    row.add("");
                }
            }
        }

        // Applies a change to the chosen columns and counts how many cells really changed
        int changeCells(List<Integer> cols, java.util.function.UnaryOperator<String> change) {
            int changed = 0;
            for (List<String> row : rows) {
                for (int c : cols) {
                    if (c < row.size()) {
                        String before = row.get(c);
                        String after = change.apply(before);
                        if (!after.equals(before)) {
                            row.set(c, after);
                            changed++;
                        }
                    }
                }
            }
            return changed;
        }

        CsvData toCsvData() {
            return new CsvData(headers, rows);
        }
    }
}
