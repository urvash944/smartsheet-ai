// Backend address: empty in development (Vite proxy), real URL after deployment.
const API_BASE = import.meta.env.VITE_API_URL || '';

// GET /api/health -> { status, message }
export async function checkHealth() {
  const response = await fetch(`${API_BASE}/api/health`);
  if (!response.ok) {
    throw new Error(`Server error: ${response.status}`);
  }
  return response.json();
}

// Shared helper: sends FormData with POST and handles errors the same way every time.
async function postForm(path, formData) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: formData,
  });

  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(data.error || `Request failed (status ${response.status})`);
  }
  return data;
}

// POST /api/csv/upload -> { data: { headers, rows }, profile }
export function uploadCsv(file) {
  const formData = new FormData();
  formData.append('file', file);
  return postForm('/api/csv/upload', formData);
}

// POST /api/csv/clean -> { data, appliedOperations, before, after }
export function cleanCsv(file, operations) {
  const formData = new FormData();
  formData.append('file', file);
  // Add "operations" once per selected operation; Spring collects them into a List
  operations.forEach((operation) => formData.append('operations', operation));
  return postForm('/api/csv/clean', formData);
}