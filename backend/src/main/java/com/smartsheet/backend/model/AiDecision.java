package com.smartsheet.backend.model;

import java.util.Set;

// What the AI decided:
//   operations -> approved operations to run
//   notDone    -> parts of the instruction no approved operation can do ("" if none)
//   reason     -> the AI's one-sentence explanation
public record AiDecision(Set<CleaningOperation> operations, String notDone, String reason) {
}