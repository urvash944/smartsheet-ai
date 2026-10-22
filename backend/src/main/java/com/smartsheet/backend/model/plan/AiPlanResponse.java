package com.smartsheet.backend.model.plan;

// What /api/plan/ai-plan returns:
//   plan     -> the checked plan the AI wrote (the user can confirm or edit it)
//   notDone  -> what the AI could not do
//   reason   -> the AI's short explanation
//   attempts -> 1 normally, 2 if the AI had to fix its first plan
//   preview  -> the result of running the plan (cleaned data, step reports, before/after counts)
//   aiInput  -> the EXACT text sent to the AI, for "What the AI saw"
public record AiPlanResponse(
        CleaningPlan plan,
        String notDone,
        String reason,
        int attempts,
        PlanResult preview,
        String aiInput
) {
}
