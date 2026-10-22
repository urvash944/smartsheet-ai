// Small helpers for editing a plan in the browser.
// They never change the old list: each one returns a NEW list, which is what React needs
// to notice the change and redraw the page.

import { BLOCKS } from './planBlocks';

// Every step in the editor gets a hidden "_id", so React can keep track of it while it moves.
// (Using the list position as the key would mix up the inputs when steps are reordered.)
let nextId = 1;
function withId(step) {
  return { ...step, _id: nextId++ };
}

// A new step for a block, with its first choices already picked
// e.g. CHANGE_CASE -> { action: "CHANGE_CASE", option: "UPPER" }
export function newStep(action) {
  const step = { action };
  for (const field of BLOCKS[action].fields) {
    if (field.kind === 'choice') {
      step[field.key] = field.choices[0];
    }
  }
  return withId(step);
}

// The AI's plan -> steps the editor can use
export function fromPlan(plan) {
  return plan.steps.map((step) => withId(step));
}

export function insertStep(steps, index, step) {
  const copy = [...steps];
  copy.splice(index, 0, step);
  return copy;
}

export function removeStep(steps, index) {
  return steps.filter((_, i) => i !== index);
}

// Moves a step from one position to another.
// "to" is the position the step should land BEFORE, in the list as it is now.
export function moveStep(steps, from, to) {
  if (from === to || from + 1 === to) {
    return steps; // dropping a step just before or onto itself changes nothing
  }
  const copy = [...steps];
  const [moved] = copy.splice(from, 1);
  copy.splice(from < to ? to - 1 : to, 0, moved);
  return copy;
}

export function updateStep(steps, index, key, value) {
  return steps.map((step, i) => (i === index ? { ...step, [key]: value } : step));
}

// Editor steps -> the JSON the backend expects:
//   - drop the hidden "_id"
//   - drop empty settings ("" or [])
//   - "columns" typed as "a, b" becomes ["a", "b"]
export function toPlan(steps) {
  return {
    steps: steps.map((step) => {
      const clean = {};
      for (const [key, value] of Object.entries(step)) {
        if (key.startsWith('_')) continue;
        if (key === 'columns') {
          const list = (Array.isArray(value) ? value : String(value).split(','))
            .map((c) => c.trim())
            .filter((c) => c !== '');
          if (list.length > 0) clean.columns = list;
          continue;
        }
        // Keep a single space: it is a real separator for SPLIT_COLUMN and MERGE_COLUMNS
        if (value === undefined || value === null || value === '') continue;
        clean[key] = value;
      }
      return clean;
    }),
  };
}

// Column names to suggest while typing: the file's columns,
// plus names created by earlier steps (rename, split, merge)
export function columnSuggestions(headers, steps) {
  const names = new Set(headers);
  for (const step of steps) {
    if (step.action === 'RENAME_COLUMN' && step.newText) names.add(step.newText.trim());
    if (step.action === 'MERGE_COLUMNS' && step.newText) names.add(step.newText.trim());
    if (step.action === 'SPLIT_COLUMN' && step.newText) {
      step.newText.split(',').forEach((n) => n.trim() && names.add(n.trim()));
    }
  }
  return [...names];
}
