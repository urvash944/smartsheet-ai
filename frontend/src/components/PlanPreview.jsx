import { describeStep, removesData } from '../planText';
import ProfileSummary from './ProfileSummary';

// Shows the AI's plan BEFORE anything is accepted:
//   - the AI's reason, and what it could not do
//   - each step as a sentence, plus what it changed in the preview run
//   - a remove button (✕) on each step
//   - Confirm / Discard buttons
//
// Props:
//   planResponse -> { plan, notDone, reason, attempts, preview, aiInput, edited }
//   busy         -> true while a request is running (buttons are disabled)
//   onRemoveStep(index), onConfirm(), onDiscard()
function PlanPreview({ planResponse, busy, onRemoveStep, onConfirm, onDiscard }) {
  const { plan, notDone, reason, attempts, preview, aiInput, edited } = planResponse;
  const reports = preview ? preview.steps : [];

  return (
    <div className="plan-preview">
      <h3>The AI&apos;s plan</h3>

      {reason && (
        <p className="ai-reason">
          🤖 <strong>AI:</strong> {reason}
        </p>
      )}

      {attempts === 2 && (
        <p className="hint">
          🔁 The first plan had a mistake, so the AI was shown the error and fixed its plan.
        </p>
      )}

      {notDone && (
        <p className="warning">
          ⚠️ <strong>Not done:</strong> {notDone}
        </p>
      )}

      {edited && <p className="hint">✏️ You edited this plan. The preview below is updated.</p>}

      {/* <ol> = numbered list, because the steps run in this order */}
      <ol className="plan-steps">
        {plan.steps.map((step, index) => {
          const report = reports[index];
          const rowsChanged = report && report.rowsAfter !== report.rowsBefore;

          return (
            <li key={index} className="plan-step">
              <div className="plan-step-main">
                <strong>{describeStep(step)}</strong>
                {removesData(step) && <span className="badge-warning">removes data</span>}
                {report && <div className="plan-step-result">{report.summary}</div>}
                {rowsChanged && (
                  <div className="plan-step-rows">
                    Rows: {report.rowsBefore} → {report.rowsAfter}
                  </div>
                )}
              </div>
              <button
                className="icon-button"
                onClick={() => onRemoveStep(index)}
                disabled={busy}
                title="Remove this step"
                aria-label={`Remove step ${index + 1}`}
              >
                ✕
              </button>
            </li>
          );
        })}
      </ol>

      {preview && (
        <>
          <h3>What will change</h3>
          <ProfileSummary before={preview.before} after={preview.after} />
        </>
      )}

      <div className="plan-actions">
        <button className="button" onClick={onConfirm} disabled={busy || !preview}>
          ✅ Confirm &amp; show result
        </button>
        <button className="button button-secondary" onClick={onDiscard} disabled={busy}>
          Discard plan
        </button>
      </div>

      {aiInput && (
        <details className="ai-input">
          <summary>🔍 What the AI saw (exact text sent)</summary>
          <pre>{aiInput}</pre>
        </details>
      )}
    </div>
  );
}

export default PlanPreview;
