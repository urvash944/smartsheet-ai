// The base address of our backend.
// - During development this is empty (""), so calls go to "/api/..."
//   and the Vite proxy forwards them to localhost:8080.
// - After deployment (Step 10) we will set VITE_API_URL to the real backend address.
const API_BASE = import.meta.env.VITE_API_URL || '';

// Asks the backend "are you alive?"
// Returns the JSON object, e.g. { status: "UP", message: "..." }
export async function checkHealth() {
  const response = await fetch(`${API_BASE}/api/health`);

  // If the server replied with an error code (like 404 or 500), stop here
  if (!response.ok) {
    throw new Error(`Server error: ${response.status}`);
  }

  // Convert the response text into a JavaScript object
  return response.json();
}