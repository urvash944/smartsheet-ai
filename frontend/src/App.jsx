import { useEffect, useState } from 'react';
import { checkHealth, uploadCsv, cleanCsv, aiCleanCsv } from './api';
import { downloadCsv, cleanedFileName } from './csvExport';
import DataTable from './components/DataTable';
import ProfileSummary from './components/ProfileSummary';
import './App.css';

// Operations for manual mode. "id" values must match the Java enum names.
const OPERATIONS = [
  {
    id: 'TRIM_WHITESPACE',
    label: 'Trim extra spaces',
    hint: 'Removes spaces at the start and end of every cell',
  },
  {
    id: 'REMOVE_EMPTY_ROWS',
    label: 'Remove empty rows',
    hint: 'Removes rows where every cell is blank',
  },
  {
    id: 'FILL_EMPTY_CELLS',
    label: 'Fill empty cells',
    hint: 'Puts N/A into blank or missing cells',
  },
  {
    id: 'REMOVE_DUPLICATES',
    label: 'Remove duplicate rows',
    hint: 'Keeps the first copy of each identical row',
  },
];

// Example instructions the user can click to fill the text box
const EXAMPLES = ['Remove duplicate rows', 'Trim extra spaces', 'Clean up this file'];

function App() {
  const [backendStatus, setBackendStatus] = useState('checking');

  // Upload state
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  // Cleaning state (shared by AI mode and manual mode)
  const [instruction, setInstruction] = useState('');
  const [selectedOps, setSelectedOps] = useState([]);
  const [cleanResult, setCleanResult] = useState(null);
  const [cleaning, setCleaning] = useState(false);
  const [cleanError, setCleanError] = useState('');

  useEffect(() => {
    checkHealth()
      .then(() => setBackendStatus('online'))
      .catch(() => setBackendStatus('offline'));
  }, []);

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

  function toggleOperation(id) {
    setSelectedOps((current) =>
      current.includes(id) ? current.filter((op) => op !== id) : [...current, id]
    );
  }

  // Shared helper: runs a cleaning request (AI or manual) and handles loading + errors.
  async function runCleaning(request) {
    setCleaning(true);
    resetCleaning();

    try {
      setCleanResult(await request());
    } catch (error) {
      setCleanError(error.message);
    } finally {
      setCleaning(false);
    }
  }

  function handleAiClean() {
    runCleaning(() => aiCleanCsv(selectedFile, instruction.trim()));
  }

  function handleManualClean() {
    runCleaning(() => cleanCsv(selectedFile, selectedOps));
  }

  // Creates the cleaned CSV in the browser and downloads it
  function handleDownload() {
    downloadCsv(
      cleanedFileName(selectedFile?.name),
      cleanResult.data.headers,
      cleanResult.data.rows
    );
  }

  // Short summary of what changed, e.g. "2 row(s) removed"
  function changeSummary(before, after) {
    const rowsRemoved = before.totalRows - after.totalRows;
    const columnsRemoved = before.totalColumns - after.totalColumns;
    const parts = [];
    if (rowsRemoved > 0) parts.push(`${rowsRemoved} row(s) removed`);
    if (columnsRemoved > 0) parts.push(`${columnsRemoved} column(s) removed`);
    return parts.length > 0 ? parts.join(', ') : 'No rows or columns removed';
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
            {/* onClick clears the old selection, so picking the SAME file again
                still triggers onChange (browsers skip onChange if nothing changed) */}
            <input
              type="file"
              accept=".csv"
              onClick={(event) => {
                event.target.value = '';
              }}
              onChange={handleFileChange}
            />
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

        {/* ---------- CARD 2: Instruction ---------- */}
        <section className="card">
          <h2>2. Tell the AI what to clean</h2>

          {!uploadResult ? (
            <p className="placeholder">Upload a file first.</p>
          ) : (
            <>
              <textarea
                className="instruction"
                rows={3}
                maxLength={300}
                placeholder='For example: "Remove duplicate rows and fix extra spaces"'
                value={instruction}
                onChange={(event) => setInstruction(event.target.value)}
              />

              <div className="examples">
                <span>Try:</span>
                {EXAMPLES.map((example) => (
                  <button
                    key={example}
                    className="chip"
                    onClick={() => setInstruction(example)}
                  >
                    {example}
                  </button>
                ))}
              </div>

              <p className="privacy-note">
                🔒 The AI only sees your instruction, the column names and counts — never your
                data. Please don&apos;t type personal information in the instruction.
              </p>

              <button
                className="button"
                onClick={handleAiClean}
                disabled={cleaning || !instruction.trim()}
              >
                {cleaning ? 'Working...' : 'Clean with AI'}
              </button>

              <details className="manual-mode">
                <summary>Manual mode (no AI)</summary>

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
                  className="button button-secondary"
                  onClick={handleManualClean}
                  disabled={cleaning || selectedOps.length === 0}
                >
                  Clean Data
                </button>
              </details>

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
              {cleanResult.aiReason && (
                <p className="ai-reason">
                  🤖 <strong>AI decision:</strong> {cleanResult.aiReason}
                </p>
              )}

              <p className="summary">
                Applied: <strong>{cleanResult.appliedOperations.join(', ')}</strong>
              </p>

              {cleanResult.notDone && (
                <p className="warning">
                  ⚠️ <strong>Not done:</strong> {cleanResult.notDone} (no approved operation for
                  this yet)
                </p>
              )}

              {cleanResult.aiInput && (
                <details className="ai-input">
                  <summary>🔍 What the AI saw (exact text sent)</summary>
                  <pre>{cleanResult.aiInput}</pre>
                </details>
              )}

              {/* ---- Download bar ---- */}
              <div className="download-bar">
                <div>
                  <strong>{cleanedFileName(selectedFile?.name)}</strong>
                  <br />
                  <small>
                    {cleanResult.data.rows.length} rows · {cleanResult.data.headers.length} columns ·{' '}
                    {changeSummary(cleanResult.before, cleanResult.after)}
                  </small>
                </div>
                <button className="button" onClick={handleDownload}>
                  ⬇ Download cleaned CSV
                </button>
              </div>

              <h3>Before vs after</h3>
              <ProfileSummary before={cleanResult.before} after={cleanResult.after} />

              {cleanResult.after.duplicateRows > 0 && (
                <p className="warning">
                  ⚠️ {cleanResult.after.duplicateRows} duplicate row(s) remain. Try the instruction
                  &quot;Remove duplicate rows&quot; to remove them.
                </p>
              )}

              <h3>Cleaned data</h3>
              <DataTable headers={cleanResult.data.headers} rows={cleanResult.data.rows} />
            </>
          )}
        </section>
      </main>
    </div>
  );
}

export default App;