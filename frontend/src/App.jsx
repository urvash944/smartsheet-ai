import { useEffect, useState } from 'react';
import { checkHealth, uploadCsv, cleanCsv, aiPlan, applyPlan } from './api';
import { downloadCsv, cleanedFileName } from './csvExport';
import DataTable from './components/DataTable';
import ProfileSummary from './components/ProfileSummary';
import PlanPreview from './components/PlanPreview';
import './App.css';

// Operations for manual mode (no AI). "id" values must match the Java enum names.
const OPERATIONS = [
  { id: 'TRIM_WHITESPACE', label: 'Trim extra spaces', hint: 'Removes spaces at the start and end of every cell' },
  { id: 'REMOVE_EMPTY_ROWS', label: 'Remove empty rows', hint: 'Removes rows where every cell is blank' },
  { id: 'FILL_EMPTY_CELLS', label: 'Fill empty cells', hint: 'Puts N/A into blank or missing cells' },
  { id: 'REMOVE_DUPLICATES', label: 'Remove duplicate rows', hint: 'Keeps the first copy of each identical row' },
];
const OPERATION_LABELS = Object.fromEntries(OPERATIONS.map((op) => [op.id, op.label]));

// Example instructions the user can click. They show what the new building blocks can do.
const EXAMPLES = [
  'Clean up this file',
  'Make city names uppercase',
  'Remove rows where email is empty',
  'Convert dates to YYYY-MM-DD',
  'Split name into first_name and last_name',
];

// Same limit as the backend (application.properties)
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB in bytes

function App() {
  const [backendStatus, setBackendStatus] = useState('checking');

  // Upload state
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  // AI plan state (NEW in Step C3)
  const [instruction, setInstruction] = useState('');
  const [planResponse, setPlanResponse] = useState(null); // the AI's plan + preview, waiting for Confirm
  const [planning, setPlanning] = useState(false);        // true while asking the AI or re-running a plan

  // Manual mode state
  const [selectedOps, setSelectedOps] = useState([]);

  // Final result shown in Card 3 (from a confirmed plan OR manual mode)
  const [cleanResult, setCleanResult] = useState(null);
  const [cleaning, setCleaning] = useState(false);
  const [cleanError, setCleanError] = useState('');

  // Check the backend when the page loads (retries while the free server wakes up)
  useEffect(() => {
    let cancelled = false;

    async function connect() {
      for (let attempt = 1; attempt <= 24; attempt++) { // 24 x 5 seconds = 2 minutes
        try {
          await checkHealth();
          if (!cancelled) setBackendStatus('online');
          return;
        } catch {
          if (cancelled) return;
          setBackendStatus('waking');
          await new Promise((resolve) => setTimeout(resolve, 5000));
        }
      }
      if (!cancelled) setBackendStatus('offline');
    }

    connect();
    return () => {
      cancelled = true;
    };
  }, []);

  // Clears the plan and the result (used when a new file is chosen, or a new request starts)
  function resetCleaning() {
    setPlanResponse(null);
    setCleanResult(null);
    setCleanError('');
  }

  function handleFileChange(event) {
    const file = event.target.files[0] || null;
    setUploadResult(null);
    setUploadError('');
    resetCleaning();

    // Quick checks in the browser (the backend checks again; never trust only the browser)
    if (file && !file.name.toLowerCase().endsWith('.csv')) {
      setSelectedFile(null);
      setUploadError('Only .csv files are allowed.');
      return;
    }
    if (file && file.size > MAX_FILE_SIZE) {
      setSelectedFile(null);
      setUploadError(`This file is ${(file.size / 1024 / 1024).toFixed(1)} MB. The maximum size is 5 MB.`);
      return;
    }
    setSelectedFile(file);
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

  // ---------- AI plan (NEW) ----------

  // Step 1: ask the AI for a plan. Nothing is "accepted" yet: we only show a preview.
  async function handleMakePlan() {
    setPlanning(true);
    resetCleaning();
    try {
      setPlanResponse(await aiPlan(selectedFile, instruction.trim()));
    } catch (error) {
      setCleanError(error.message);
    } finally {
      setPlanning(false);
    }
  }

  // The user removes a step -> run the shorter plan again to get a fresh, correct preview
  async function handleRemoveStep(index) {
    const steps = planResponse.plan.steps.filter((_, i) => i !== index);
    if (steps.length === 0) {
      setPlanResponse(null); // nothing left to run
      return;
    }
    const newPlan = { steps };
    setPlanning(true);
    setCleanError('');
    try {
      const preview = await applyPlan(selectedFile, newPlan);
      // Keep the AI's reason and "what the AI saw", replace the plan and the preview
      setPlanResponse({ ...planResponse, plan: newPlan, preview, edited: true });
    } catch (error) {
      setCleanError(error.message);
    } finally {
      setPlanning(false);
    }
  }

  // Step 2: the user confirms -> the preview becomes the result in Card 3
  function handleConfirmPlan() {
    const { preview, reason, notDone, aiInput } = planResponse;
    setCleanResult({
      data: preview.data,
      before: preview.before,
      after: preview.after,
      applied: preview.steps.map((report) => report.summary),
      aiReason: reason,
      notDone,
      aiInput,
    });
    setPlanResponse(null);
  }

  // ---------- Manual mode ----------

  function toggleOperation(id) {
    setSelectedOps((current) =>
      current.includes(id) ? current.filter((op) => op !== id) : [...current, id]
    );
  }

  async function handleManualClean() {
    setCleaning(true);
    resetCleaning();
    try {
      const result = await cleanCsv(selectedFile, selectedOps);
      setCleanResult({
        data: result.data,
        before: result.before,
        after: result.after,
        applied: result.appliedOperations.map((op) => OPERATION_LABELS[op] || op),
      });
    } catch (error) {
      setCleanError(error.message);
    } finally {
      setCleaning(false);
    }
  }

  // ---------- Download ----------

  function handleDownload() {
    downloadCsv(cleanedFileName(selectedFile?.name), cleanResult.data.headers, cleanResult.data.rows);
  }

  function changeSummary(before, after) {
    const rowsRemoved = before.totalRows - after.totalRows;
    const columnChange = after.totalColumns - before.totalColumns;
    const parts = [];
    if (rowsRemoved > 0) parts.push(`${rowsRemoved} row(s) removed`);
    if (columnChange < 0) parts.push(`${-columnChange} column(s) removed`);
    if (columnChange > 0) parts.push(`${columnChange} column(s) added`);
    return parts.length > 0 ? parts.join(', ') : 'No rows or columns removed';
  }

  const busy = planning || cleaning;

  return (
    <div className="app">
      <header className="header">
        <h1>SmartSheet AI</h1>
        <p className="subtitle">CSV Data Cleaning Agent</p>
        <p className={`status status-${backendStatus}`}>
          {backendStatus === 'checking' && 'Checking backend...'}
          {backendStatus === 'waking' && '⏳ Waking up the server (free hosting sleeps when idle, ~1 minute)...'}
          {backendStatus === 'online' && 'Backend connected ✅'}
          {backendStatus === 'offline' && 'Backend offline ❌ Please refresh the page in a minute.'}
        </p>
      </header>

      <main className="content">
        {/* ---------- CARD 1: Upload ---------- */}
        <section className="card">
          <h2>1. Upload your CSV</h2>

          <div className="upload-row">
            {/* onClick clears the old selection, so picking the SAME file again still triggers onChange */}
            <input
              type="file"
              accept=".csv"
              onClick={(event) => {
                event.target.value = '';
              }}
              onChange={handleFileChange}
            />
            <button className="button" onClick={handleUpload} disabled={uploading || !selectedFile}>
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

        {/* ---------- CARD 2: Instruction + plan preview ---------- */}
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
                placeholder='For example: "Make city names uppercase and remove rows where email is empty"'
                value={instruction}
                onChange={(event) => setInstruction(event.target.value)}
              />

              <div className="examples">
                <span>Try:</span>
                {EXAMPLES.map((example) => (
                  <button key={example} className="chip" onClick={() => setInstruction(example)}>
                    {example}
                  </button>
                ))}
              </div>

              <p className="privacy-note">
                🔒 The AI only sees your instruction, the column names, column types and counts — never
                your data. Please don&apos;t type personal information in the instruction.
              </p>

              <button className="button" onClick={handleMakePlan} disabled={busy || !instruction.trim()}>
                {planning && !planResponse ? 'The AI is planning...' : 'Make a plan with AI'}
              </button>

              {planResponse && (
                <PlanPreview
                  planResponse={planResponse}
                  busy={busy}
                  onRemoveStep={handleRemoveStep}
                  onConfirm={handleConfirmPlan}
                  onDiscard={() => setPlanResponse(null)}
                />
              )}

              {cleanError && <p className="error">{cleanError}</p>}

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
                  disabled={busy || selectedOps.length === 0}
                >
                  {cleaning ? 'Cleaning...' : 'Clean Data'}
                </button>
              </details>
            </>
          )}
        </section>

        {/* ---------- CARD 3: Results ---------- */}
        <section className="card">
          <h2>3. Preview and download</h2>

          {!cleanResult ? (
            <p className="placeholder">Confirm a plan (or use manual mode) to see the cleaned data here.</p>
          ) : (
            <>
              {cleanResult.aiReason && (
                <p className="ai-reason">
                  🤖 <strong>AI decision:</strong> {cleanResult.aiReason}
                </p>
              )}

              <h3>What was done</h3>
              <ol className="applied-list">
                {cleanResult.applied.map((line, index) => (
                  <li key={index}>{line}</li>
                ))}
              </ol>

              {cleanResult.notDone && (
                <p className="warning">
                  ⚠️ <strong>Not done:</strong> {cleanResult.notDone}
                </p>
              )}

              {cleanResult.aiInput && (
                <details className="ai-input">
                  <summary>🔍 What the AI saw (exact text sent)</summary>
                  <pre>{cleanResult.aiInput}</pre>
                </details>
              )}

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
