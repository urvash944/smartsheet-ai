package com.smartsheet.backend.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Our APPROVED LIST of cleaning operations.
// Nothing outside this list can ever run — not from the user, not from the AI.
// The order here is also the order they always run in.
public enum CleaningOperation {

    TRIM_WHITESPACE("Remove extra spaces at the start and end of every cell"),
    REMOVE_EMPTY_ROWS("Remove rows where every cell is blank"),
    FILL_EMPTY_CELLS("Fill blank or missing cells with N/A"),
    REMOVE_DUPLICATES("Remove rows that are exact copies of an earlier row");

    private final String description;

    CleaningOperation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    // Converts names into enum values. Unknown names throw
    // IllegalArgumentException.
    public static Set<CleaningOperation> fromNames(List<String> names) {
        Set<CleaningOperation> operations = EnumSet.noneOf(CleaningOperation.class);
        for (String name : names) {
            operations.add(CleaningOperation.valueOf(name.strip().toUpperCase()));
        }
        return operations;
    }
}