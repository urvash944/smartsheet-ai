import { useState } from 'react';
import { BLOCKS, BLOCK_GROUPS } from '../planBlocks';
import { describeStep, removesData } from '../planText';
import {
  newStep, insertStep, removeStep, moveStep, updateStep, columnSuggestions,
} from '../planEditing';
import ProfileSummary from './ProfileSummary';

// Drag-and-drop plan editor.
//
// Two kinds of things can be dragged:
//   - a BLOCK from the palette  -> adds a new step where it is dropped
//   - a STEP in the plan        -> moves that step to where it is dropped
// We tell them apart with two custom "data types" in the browser's drag data.
const BLOCK_TYPE = 'application/x-smartsheet-block';
const STEP_TYPE = 'application/x-smartsheet-step';

// Props:
//   steps, onStepsChange(newSteps) -> the plan being edited (App owns it)
//   headers      -> the file's column names (for suggestions)
//   aiInfo       -> { reason, notDone, attempts, aiInput } or null when built by hand
//   preview      -> last result from the backend, or null
//   previewStale -> true when the steps changed after the last preview
//   busy, onUpdatePreview(), onConfirm(), onDiscard()
function PlanEditor({
  steps, onStepsChange, headers, aiInfo, preview, previewStale, busy,
  onUpdatePreview, onConfirm, onDiscard,
}) {
  // Which drop position is highlighted while dragging (null = none). steps.length = the end.
  const [dropIndex, setDropIndex] = useState(null);

  const suggestions = columnSuggestions(headers, steps);
  const reports = preview && !previewStale ? preview.steps : [];

  // ---------- Drag and drop ----------

  function startDragBlock(event, action) {
    event.dataTransfer.setData(BLOCK_TYPE, action);
    event.dataTransfer.effectAllowed = 'copy';
  }

  function startDragStep(event, index) {
    event.dataTransfer.setData(STEP_TYPE, String(index));
    event.dataTransfer.effectAllowed = 'move';
  }

  // The browser only allows a drop where dragover calls preventDefault()
  function allowDrop(event, index) {
    const types = Array.from(event.dataTransfer.types);
    if (types.includes(BLOCK_TYPE) || types.includes(STEP_TYPE)) {
      event.preventDefault();
      setDropIndex(index);
    }
  }

  function drop(event, index) {
    event.preventDefault();
    setDropIndex(null);
    const action = event.dataTransfer.getData(BLOCK_TYPE);
    const from = event.dataTransfer.getData(STEP_TYPE);
    if (action && BLOCKS[action]) {
      onStepsChange(insertStep(steps, index, newStep(action)));
    } else if (from !== '') {
      onStepsChange(moveStep(steps, Number(from), index));
    }
  }

  // ---------- Buttons (work without a mouse, and on phones) ----------

  function addBlock(action) {
    onStepsChange(insertStep(steps, steps.length, newStep(action)));
  }

  function moveUp(index) {
    if (index > 0) onStepsChange(moveStep(steps, index, index - 1));
  }

  function moveDown(index) {
    if (index < steps.length - 1) onStepsChange(moveStep(steps, index, index + 2));
  }

  // ---------- One settings input ----------

  function renderField(step, index, field) {
    if (field.showIf && !field.showIf(step)) return null;
    const id = `step-${step._id}-${field.key}`;
    const raw = step[field.key];
    const value = Array.isArray(raw) ? raw.join(', ') : (raw ?? '');
    const change = (event) => onStepsChange(updateStep(steps, index, field.key, event.target.value));

    let input;
    if (field.kind === 'choice') {
      input = (
        <select id={id} value={value} onChange={change} disabled={busy}>
          {field.choices.map((choice, i) => (
            <option key={choice || 'none'} value={choice}>
              {field.choiceLabels ? field.choiceLabels[i] : choice}
            </option>
          ))}
        </select>
      );
    } else {
      // "list" links the input to the <datalist> of column names below, which gives suggestions
      input = (
        <input
          id={id}
          type="text"
          value={value}
          onChange={change}
          disabled={busy}
          maxLength={100}
          list={field.kind === 'column' || field.kind === 'columns' ? 'plan-column-names' : undefined}
          placeholder={field.placeholder || (field.optional ? (field.kind === 'column' ? 'all columns' : 'optional') : '')}
        />
      );
    }

    return (
      <label key={field.key} className="step-field" htmlFor={id}>
        <span>{field.label}</span>
        {input}
      </label>
    );
  }

  // ---------- Page ----------

  return (
    <div className="plan-editor">
      {/* Suggestions shown while typing a column name */}
      <datalist id="plan-column-names">
        {suggestions.map((name) => (
          <option key={name} value={name} />
        ))}
      </datalist>

      {aiInfo && (
        <>
          {aiInfo.reason && (
            <p className="ai-reason">
              🤖 <strong>AI:</strong> {aiInfo.reason}
            </p>
          )}
          {aiInfo.attempts === 2 && (
            <p className="hint">🔁 The first plan had a mistake, so the AI was shown the error and fixed its plan.</p>
          )}
          {aiInfo.notDone && (
            <p className="warning">
              ⚠️ <strong>Not done:</strong> {aiInfo.notDone}
            </p>
          )}
        </>
      )}

      <div className="editor-layout">
        {/* ---------- Left: the blocks palette ---------- */}
        <aside className="palette">
          <h3>Building blocks</h3>
          <p className="hint">Drag a block into the plan, or click +.</p>
          {BLOCK_GROUPS.map((group) => (
            <div key={group} className="palette-group">
              <h4>{group}</h4>
              {Object.entries(BLOCKS)
                .filter(([, block]) => block.group === group)
                .map(([action, block]) => (
                  <div
                    key={action}
                    className="palette-block"
                    draggable={!busy}
                    onDragStart={(event) => startDragBlock(event, action)}
                    title={block.hint}
                  >
                    <span className="palette-label">{block.label}</span>
                    <button
                      className="icon-button"
                      onClick={() => addBlock(action)}
                      disabled={busy}
                      aria-label={`Add ${block.label}`}
                    >
                      +
                    </button>
                  </div>
                ))}
            </div>
          ))}
        </aside>

        {/* ---------- Right: the plan ---------- */}
        <div className="plan-column">
          <h3>Your plan {steps.length > 0 && `(${steps.length} step${steps.length > 1 ? 's' : ''})`}</h3>

          <ol className="plan-steps">
            {steps.map((step, index) => {
              const block = BLOCKS[step.action];
              const report = reports[index];
              const rowsChanged = report && report.rowsAfter !== report.rowsBefore;

              return (
                <li
                  key={step._id}
                  className={`plan-step editable ${dropIndex === index ? 'drop-before' : ''}`}
                  onDragOver={(event) => allowDrop(event, index)}
                  onDragLeave={() => setDropIndex(null)}
                  onDrop={(event) => drop(event, index)}
                >
                  {/* Only the handle is draggable, so you can still select text inside the inputs */}
                  <span
                    className="drag-handle"
                    draggable={!busy}
                    onDragStart={(event) => startDragStep(event, index)}
                    onDragEnd={() => setDropIndex(null)}
                    title="Drag to move this step"
                  >
                    ⠿
                  </span>

                  <div className="plan-step-main">
                    <strong>{block ? describeStep(step) : step.action}</strong>
                    {removesData(step) && <span className="badge-warning">removes data</span>}

                    {block && block.fields.length > 0 && (
                      <div className="step-fields">{block.fields.map((field) => renderField(step, index, field))}</div>
                    )}

                    {report && <div className="plan-step-result">{report.summary}</div>}
                    {rowsChanged && (
                      <div className="plan-step-rows">
                        Rows: {report.rowsBefore} → {report.rowsAfter}
                      </div>
                    )}
                  </div>

                  <div className="step-buttons">
                    <button className="icon-button" onClick={() => moveUp(index)} disabled={busy || index === 0} aria-label={`Move step ${index + 1} up`}>
                      ↑
                    </button>
                    <button className="icon-button" onClick={() => moveDown(index)} disabled={busy || index === steps.length - 1} aria-label={`Move step ${index + 1} down`}>
                      ↓
                    </button>
                    <button className="icon-button" onClick={() => onStepsChange(removeStep(steps, index))} disabled={busy} aria-label={`Remove step ${index + 1}`}>
                      ✕
                    </button>
                  </div>
                </li>
              );
            })}
          </ol>

          {/* Drop zone at the end of the plan */}
          <div
            className={`drop-zone ${dropIndex === steps.length ? 'active' : ''}`}
            onDragOver={(event) => allowDrop(event, steps.length)}
            onDragLeave={() => setDropIndex(null)}
            onDrop={(event) => drop(event, steps.length)}
          >
            {steps.length === 0 ? 'Drag building blocks here to build your plan' : 'Drop here to add at the end'}
          </div>

          {/* ---------- Preview ---------- */}
          {previewStale && steps.length > 0 && (
            <p className="warning">✏️ The plan changed. Click <strong>Update preview</strong> to see the new result before confirming.</p>
          )}

          {preview && !previewStale && (
            <>
              <h3>What will change</h3>
              <ProfileSummary before={preview.before} after={preview.after} />
            </>
          )}

          <div className="plan-actions">
            <button className="button button-secondary" onClick={onUpdatePreview} disabled={busy || steps.length === 0 || !previewStale}>
              {busy ? 'Working...' : '🔄 Update preview'}
            </button>
            <button className="button" onClick={onConfirm} disabled={busy || !preview || previewStale}>
              ✅ Confirm &amp; show result
            </button>
            <button className="button button-secondary" onClick={onDiscard} disabled={busy}>
              Discard plan
            </button>
          </div>

          {aiInfo && aiInfo.aiInput && (
            <details className="ai-input">
              <summary>🔍 What the AI saw (exact text sent)</summary>
              <pre>{aiInfo.aiInput}</pre>
            </details>
          )}
        </div>
      </div>
    </div>
  );
}

export default PlanEditor;
