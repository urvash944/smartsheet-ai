package com.smartsheet.backend.model.plan;

// What the AI answered:
//   plan    -> the steps it wants to run (not trusted yet: Java checks every step)
//   notDone -> parts of the instruction no building block can do ("" if none)
//   reason  -> one short sentence explaining the plan
public record AiPlan(CleaningPlan plan, String notDone, String reason) {
}
