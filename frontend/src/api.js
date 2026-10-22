// Backend address: empty in development (Vite proxy), real URL after deployment.
const API_BASE = import.meta.env.VITE_API_URL || '';

const SERVER_DOWN =
  'The server is not responding. Is the backend running? Please try again in a moment.';

// One shared function for EVERY call to the backend.
// It turns every kind of failure into a simple, readable error message.
async function request(path, options) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, options);
  } catch {
    // fetch only throws when there is no answer at all (server off, no internet)
    throw new Error(SERVER_DOWN);
  }

  // Read the JSON reply. If it isn't JSON (e.g. a proxy error page), use {}.
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    if (data.error) throw new Error(data.error);
    if (response.status >= 500) throw new Error(SERVER_DOWN);
    throw new Error(`Request failed (status ${response.status})`);
  }
  return data;
}

function postForm(path, formData) {
  return request(path, { method: 'POST', body: formData });
}

// GET /api/health -> { status, message }
export function checkHealth() {
  return request('/api/health');
}

// POST /api/csv/upload -> { data: { headers, rows }, profile }
export function uploadCsv(file) {
  const formData = new FormData();
  formData.append('file', file);
  return postForm('/api/csv/upload', formData);
}

// POST /api/csv/clean -> manual mode (checkboxes)
export function cleanCsv(file, operations) {
  const formData = new FormData();
  formData.append('file', file);
  operations.forEach((operation) => formData.append('operations', operation));
  return postForm('/api/csv/clean', formData);
}

// NEW (Step C3)
// POST /api/plan/ai-plan -> { plan, notDone, reason, attempts, preview, aiInput }
// Gemini writes a plan; the backend checks it and returns a preview of the result.
export function aiPlan(file, instruction) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('instruction', instruction);
  return postForm('/api/plan/ai-plan', formData);
}

// NEW (Step C3)
// POST /api/plan/apply -> { data, steps, before, after }
// Runs a plan we give it (used after the user removes or edits steps).
export function applyPlan(file, plan) {
  const formData = new FormData();
  formData.append('file', file);
  // The plan must be sent as a JSON "part". A Blob lets us set its type to application/json,
  // which is what @RequestPart("plan") in PlanController expects.
  formData.append('plan', new Blob([JSON.stringify(plan)], { type: 'application/json' }));
  return postForm('/api/plan/apply', formData);
}
