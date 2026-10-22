package com.smartsheet.backend.service.plan;

// Thrown when a plan step is not valid, e.g. an unknown action or a column that does not exist.
// The message is shown to the user, so it may contain step numbers and column names,
// but NEVER cell values.
public class PlanException extends RuntimeException {

    public PlanException(int stepNumber, String message) {
        super("Step " + stepNumber + ": " + message);
    }

    public PlanException(String message) {
        super(message);
    }
}
