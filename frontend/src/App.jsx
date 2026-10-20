import { useEffect, useState } from 'react';
import { checkHealth } from './api';
import './App.css';

function App() {
  // "useState" creates a variable React watches.
  // When it changes, React automatically redraws the page.
  // Possible values: "checking", "online", "offline"
  const [backendStatus, setBackendStatus] = useState('checking');

  // "useEffect" with an empty list [] runs ONCE when the page first loads.
  // We use it to check if the backend is alive.
  useEffect(() => {
    checkHealth()
      .then(() => setBackendStatus('online'))
      .catch(() => setBackendStatus('offline'));
  }, []);

  return (
    <div className="app">
      <header className="header">
        <h1>SmartSheet AI</h1>
        <p className="subtitle">CSV Data Cleaning Agent</p>

        {/* Show a different badge depending on backendStatus */}
        <p className={`status status-${backendStatus}`}>
          {backendStatus === 'checking' && 'Checking backend...'}
          {backendStatus === 'online' && 'Backend connected ✅'}
          {backendStatus === 'offline' && 'Backend offline ❌ (is Spring Boot running?)'}
        </p>
      </header>

      <main className="content">
        <section className="card">
          <h2>1. Upload your CSV</h2>
          <p className="placeholder">File upload will go here (Step 5).</p>
        </section>

        <section className="card">
          <h2>2. Tell the AI what to clean</h2>
          <p className="placeholder">Instruction box will go here (Step 7).</p>
        </section>

        <section className="card">
          <h2>3. Preview and download</h2>
          <p className="placeholder">Cleaned data preview will go here (Step 8).</p>
        </section>
      </main>
    </div>
  );
}

export default App;