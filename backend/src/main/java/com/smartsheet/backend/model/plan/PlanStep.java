package com.smartsheet.backend.model.plan;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// ONE step of a cleaning plan, e.g.
//   { "action": "CHANGE_CASE", "column": "city", "option": "UPPER" }
//
// Every action uses the same few fields, so the shape stays simple:
//   action  -> which building block (must be a PlanAction name)
//   column  -> the column to work on (null = all columns, for actions that allow it)
//   columns -> several columns (MERGE_COLUMNS, REMOVE_DUPLICATES)
//   option  -> a choice, e.g. UPPER, IS_EMPTY, DESC
//   text    -> a value, e.g. text to find, fill value, separator, current date format
//   newText -> a second value, e.g. replacement text, new column name
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanStep(
        String action,
        String column,
        List<String> columns,
        String option,
        String text,
        String newText
) {
}
