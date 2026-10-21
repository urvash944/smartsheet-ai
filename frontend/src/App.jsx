import { useEffect, useState } from 'react';
import { checkHealth, uploadCsv, cleanCsv } from './api';
import DataTable from './components/DataTable';
import ProfileSummary from './components/ProfileSummary';
import './App.css';

// The operations the user can pick. The "id" values must match the Java enum names.
const OPERATIONS = [
  {
    id: 'TRIM_WHITESPACE',
    label: 'Trim extra spaces',
    hint: 'Removes spaces at the start and end of every cell',
  },
  {
    id: 'REMOVE_DUPLICATES',
    label: 'Remove duplicate rows',
    hint: 'Keeps the first copy of each identical row',
  },
];

function App() {
  const [backendStatus, setBackendStatus] = useState('checking');

  // Upload state
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadResult, setUploadResult] = useState(null); // { data, profile }
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  // Cleaning state
  const [selectedOps, setSelectedOps] = useState([]);     // e.g. ['TRIM_WHITESPACE']
  const [cleanResult, setCleanResult] = useState(null);   // { data, appliedOperations, before, after }
  const [cleaning, setCleaning] = useState(false);
  const [cleanError, setCleanError] = useState('');

  useEffect(() => {
    checkHealth()
      .then(() => setBackendStatus('online'))
      .catch(() => setBackendStatus('offline'));
  }, []);

  // Clears everything about cleaning (used when a new file is chosen or uploaded)
  function resetCleaning() {
    setCleanResult(null);
    setCleanError('');
  }

  function handleFileChange(event) {
    setSelectedFile(event.target.files[0] || null);
    setUploadResult(null);
    setUploadError('');
    resetCleaning();
  }

  async function handleUpload() {
    if (!selectedFile) {
      setUploadError('Please choose a CSV file first.');
      return;
    }
    setUploading(true);
    setUploadError('');
    resetCleaning();

    try {
      setUploadResult(await uploadCsv(selectedFile));
    } catch (error) {
      setUploadError(error.message);
    } finally {
      setUploading(false);
    }
  }

  // Tick or untick one operation checkbox
  function toggleOperation(id) {
    setSelectedOps((current) =>
      current.includes(id)
        ? current.filter((op) => op !== id) // was ticked -> remove it
        : [...current, id]                  // was not ticked -> add it
    );
  }

  async function handleClean() {
    setCleaning(true);
    setCleanError('');

    try {
      // We send the SAME file again, because the backend stores nothing
      setCleanResult(await cleanCsv(selectedFile, selectedOps));
    } catch (error) {
      setCleanError(error.message);
    } finally {
      setCleaning(false);
    }
  }

  return (
    <div className="app">
      <header className="header">
        <h1>SmartSheet AI</h1>
        <p className="subtitle">CSV Data Cleaning Agent</p>
        <p className={`status status-${backendStatus}`}>
          {backendStatus === 'checking' && 'Checking backend...'}
          {backendStatus === 'online' && 'Backend connected ✅'}
          {backendStatus === 'offline' && 'Backend offline ❌ (is Spring Boot running?)'}
        </p>
      </header>

      <main className="content">
        {/* ---------- CARD 1: Upload ---------- */}
        <section className="card">
          <h2>1. Upload your CSV</h2>

          <div className="upload-row">
            <input type="file" accept=".csv" onChange={handleFileChange} />
            <button
              className="button"
              onClick={handleUpload}
              disabled={uploading || !selectedFile}
            >
              {uploading ? 'Uploading...' : 'Upload & Preview'}
            </button>
          </div>

          {uploadError && <p className="error">{uploadError}</p>}

          {uploadResult && (
            <>
              <h3>Data health check</h3>
              <ProfileSummary before={uploadResult.profile} />

              <h3>Original data</h3>
              <DataTable headers={uploadResult.data.headers} rows={uploadResult.data.rows} />
            </>
          )}
        </section>

        {/* ---------- CARD 2: Choose operations ---------- */}
        <section className="card">
          <h2>2. Choose what to clean</h2>

          {!uploadResult ? (
            <p className="placeholder">Upload a file first.</p>
          ) : (
            <>
              <p className="hint">
                Manual mode: tick the operations. (In Step 7, the AI will choose for you.)
              </p>

              <div className="ops-list">
                {OPERATIONS.map((op) => (
                  <label key={op.id} className="op-option">
                    <input
                      type="checkbox"
                      checked={selectedOps.includes(op.id)}
                      onChange={() => toggleOperation(op.id)}
                    />
                    <span>
                      <strong>{op.label}</strong>
                      <br />
                      <small>{op.hint}</small>
                    </span>
                  </label>
                ))}
              </div>

              <button
                className="button"
                onClick={handleClean}
                disabled={cleaning || selectedOps.length === 0}
              >
                {cleaning ? 'Cleaning...' : 'Clean Data'}
              </button>

              {cleanError && <p className="error">{cleanError}</p>}
            </>
          )}
        </section>

        {/* ---------- CARD 3: Results ---------- */}
        <section className="card">
          <h2>3. Preview and download</h2>

          {!cleanResult ? (
            <p className="placeholder">Cleaned data will appear here.</p>
          ) : (
            <>
              <p className="summary">
                Applied: <strong>{cleanResult.appliedOperations.join(', ')}</strong>
              </p>

              <h3>Before vs after</h3>
              <ProfileSummary before={cleanResult.before} after={cleanResult.after} />

              <h3>Cleaned data</h3>
              <DataTable headers={cleanResult.data.headers} rows={cleanResult.data.rows} />

              <p className="placeholder">Download button comes in Step 8.</p>
            </>
          )}
        </section>
      </main>
    </div>
  );
}

export default App;