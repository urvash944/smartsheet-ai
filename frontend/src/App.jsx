import { useEffect, useState } from 'react';
import { checkHealth, uploadCsv, aiPlan, applyPlan } from './api';
import { downloadCsv, cleanedFileName } from './csvExport';
import { fromPlan, toPlan } from './planEditing';
import DataTable from './components/DataTable';
import ProfileSummary from './components/ProfileSummary';
import PlanEditor from './components/PlanEditor';
import './App.css';

// Example instructions the user can click. They show what the building blocks can do.
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

  // Plan editor state
  const [instruction, setInstruction] = useState('');
  const [planSteps, setPlanSteps] = useState(null);     // null = editor closed; [] = empty plan
  const [planPreview, setPlanPreview] = useState(null); // last result from the backend
  const [planStale, setPlanStale] = useState(false);    // true = steps changed after the last preview
  const [aiInfo, setAiInfo] = useState(null);           // { reason, notDone, attempts, aiInput } or null
  const [working, setWorking] = useState(false);        // true while waiting for the backend

  // Final result shown in Card 3
  const [cleanResult, setCleanResult] = useState(null);
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

  // Closes the editor and clears the result (new file, or a new plan starts)
  function resetCleaning() {
    setPlanSteps(null);
    setPlanPreview(null);
    setPlanStale(false);
    setAiInfo(null);
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

  // ---------- Making a plan ----------

  // The AI writes the plan; the backend checks it and sends a preview
  async function handleMakePlan() {
    setWorking(true);
    resetCleaning();
    try {
      const response = await aiPlan(selectedFile, instruction.trim());
      setPlanSteps(fromPlan(response.plan));
      setPlanPreview(response.preview);
      setPlanStale(false);
      setAiInfo({
        reason: response.reason,
        notDone: response.notDone,
        attempts: response.attempts,
        aiInput: response.aiInput,
      });
    } catch (error) {
      setCleanError(error.message);
    } finally {
      setWorking(false);
    }
  }

  // Start with an empty plan and build it by hand (no AI)
  function handleBuildYourself() {
    resetCleaning();
    setPlanSteps([]);
    setPlanStale(true);
  }

  // Any edit (drag, add, remove, reorder, change a setting) makes the old preview out of date
  function handleStepsChange(newSteps) {
    setPlanSteps(newSteps);
    setPlanStale(true);
    setCleanError('');
  }

  // Java runs the edited plan and returns a fresh preview
  async function handleUpdatePreview() {
    setWorking(true);
    setCleanError('');
    try {
      setPlanPreview(await applyPlan(selectedFile, toPlan(planSteps)));
      setPlanStale(false);
    } catch (error) {
      setCleanError(error.message); // e.g. "Step 2: column 'cty' does not exist."
    } finally {
      setWorking(false);
    }
  }

  // The user accepts the previewed result
  function handleConfirmPlan() {
    setCleanResult({
      data: planPreview.data,
      before: planPreview.before,
      after: planPreview.after,
      applied: planPreview.steps.map((report) => report.summary),
      aiReason: aiInfo ? aiInfo.reason : '',
      notDone: aiInfo ? aiInfo.notDone : '',
      aiInput: aiInfo ? aiInfo.aiInput : '',
    });
    setPlanSteps(null);
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

        {/* ---------- CARD 2: Plan ---------- */}
        <section className="card">
          <h2>2. Plan the cleaning</h2>

          {!uploadResult ? (
            <p className="placeholder">Upload a file first.</p>
          ) : (
            <>
              <textarea
                className="instruction"
                rows={3}
                maxLength={300}
                placeholder='Tell the AI what to do, e.g. "Make city names uppercase and remove rows where email is empty"'
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

              <div className="plan-actions">
                <button className="button" onClick={handleMakePlan} disabled={working || !instruction.trim()}>
                  {working && planSteps === null ? 'The AI is planning...' : '🤖 Make a plan with AI'}
                </button>
                <button className="button button-secondary" onClick={handleBuildYourself} disabled={working}>
                  🧱 Build a plan yourself (no AI)
                </button>
              </div>

              {cleanError && <p className="error">{cleanError}</p>}

              {planSteps !== null && (
                <PlanEditor
                  steps={planSteps}
                  onStepsChange={handleStepsChange}
                  headers={uploadResult.data.headers}
                  aiInfo={aiInfo}
                  preview={planPreview}
                  previewStale={planStale}
                  busy={working}
                  onUpdatePreview={handleUpdatePreview}
                  onConfirm={handleConfirmPlan}
                  onDiscard={() => setPlanSteps(null)}
                />
              )}
            </>
          )}
        </section>

        {/* ---------- CARD 3: Results ---------- */}
        <section className="card">
          <h2>3. Preview and download</h2>

          {!cleanResult ? (
            <p className="placeholder">Confirm a plan to see the cleaned data here.</p>
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
                  ⚠️ {cleanResult.after.duplicateRows} duplicate row(s) remain. Add a &quot;Remove
                  duplicates&quot; step to remove them.
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
