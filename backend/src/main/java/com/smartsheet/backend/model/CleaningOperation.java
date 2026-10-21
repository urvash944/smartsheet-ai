package com.smartsheet.backend.model;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// An "enum" is a fixed list of allowed values.
// This is our APPROVED LIST of cleaning operations.
// Nothing outside this list can ever run — not from the user, not from the AI.
public enum CleaningOperation {

    // The order here is also the order they run in: trim first, then remove
    // duplicates.
    TRIM_WHITESPACE("Remove extra spaces at the start and end of every cell"),
    REMOVE_DUPLICATES("Remove rows that are exact copies of an earlier row");

    // A short explanation of each operation (we will also give this to the AI in
    // Step 7)
    private final String description;

    CleaningOperation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    // Converts names like ["trim_whitespace", "REMOVE_DUPLICATES"] into real enum
    // values.
    // If a name is not on the approved list, valueOf() throws
    // IllegalArgumentException.
    // EnumSet keeps them in the order declared above and ignores repeats.
    public static Set<CleaningOperation> fromNames(List<String> names) {
        Set<CleaningOperation> operations = EnumSet.noneOf(CleaningOperation.class);
        for (String name : names) {
            operations.add(CleaningOperation.valueOf(name.strip().toUpperCase()));
        }
        return operations;
    }
}