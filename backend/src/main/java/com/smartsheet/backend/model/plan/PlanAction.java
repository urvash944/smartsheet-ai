package com.smartsheet.backend.model.plan;

// The APPROVED BUILDING BLOCKS a cleaning plan can use.
// A plan is a list of steps; each step uses exactly one of these actions.
// Nothing outside this list can ever run, whether the plan comes from the user or the AI.
//
// "fields" explains which settings each action uses. In Step C2 we send this
// text to the AI, so it knows how to fill in each step.
public enum PlanAction {

    TRIM_WHITESPACE(
            "Remove spaces at the start and end of cells",
            "column (optional, empty = all columns)"),
    COLLAPSE_SPACES(
            "Turn several spaces inside text into one space",
            "column (optional)"),
    CHANGE_CASE(
            "Change text to UPPER, LOWER or TITLE case",
            "column (optional), option = UPPER | LOWER | TITLE"),
    FIND_REPLACE(
            "Replace plain text with other text (no patterns)",
            "column (optional), text = what to find, newText = replacement (may be empty), option = IGNORE_CASE (optional)"),
    FILL_EMPTY(
            "Fill blank or missing cells with a value",
            "column (optional), text = fill value (default N/A)"),
    REMOVE_EMPTY_ROWS(
            "Remove rows where every cell is blank",
            "no settings"),
    DELETE_ROWS_IF(
            "Delete rows where a column matches a condition",
            "column (required), option = IS_EMPTY | EQUALS | CONTAINS, text = value to compare (for EQUALS and CONTAINS)"),
    DELETE_COLUMN(
            "Delete a whole column",
            "column (required)"),
    RENAME_COLUMN(
            "Give a column a new name",
            "column (required), newText = new name"),
    SPLIT_COLUMN(
            "Split a column into two at the first separator",
            "column (required), text = separator (default one space), newText = two new names separated by a comma (optional)"),
    MERGE_COLUMNS(
            "Join several columns into one",
            "columns (required, at least 2), text = separator (default one space), newText = new column name (optional)"),
    FORMAT_DATE(
            "Change the format of dates in a column",
            "column (required), text = current format, option = new format; formats: YYYY-MM-DD | DD-MM-YYYY | DD/MM/YYYY | MM/DD/YYYY | DD.MM.YYYY"),
    SORT_ROWS(
            "Sort rows by a column (numbers sort as numbers)",
            "column (required), option = ASC | DESC (default ASC)"),
    REMOVE_DUPLICATES(
            "Remove rows that repeat an earlier row",
            "columns (optional, compare only these columns), option = EXACT | IGNORE_CASE (default EXACT)");

    private final String description;
    private final String fields;

    PlanAction(String description, String fields) {
        this.description = description;
        this.fields = fields;
    }

    public String getDescription() {
        return description;
    }

    public String getFields() {
        return fields;
    }
}
