// Turns a plan step (JSON from the AI) into a short sentence people can read.
// Example: { action: "CHANGE_CASE", column: "city", option: "UPPER" }
//       -> 'Change "city" to UPPER case'

// "city" -> '"city"', missing column -> "all columns"
function target(step) {
  return step.column ? `"${step.column}"` : 'all columns';
}

// Show a separator so people can see it: " " -> "space"
function separatorName(text) {
  if (text === undefined || text === null || text === '' || text === ' ') return 'space';
  return `"${text}"`;
}

export function describeStep(step) {
  const option = (step.option || '').toUpperCase();

  switch (step.action) {
    case 'TRIM_WHITESPACE':
      return `Trim spaces in ${target(step)}`;
    case 'COLLAPSE_SPACES':
      return `Collapse repeated spaces in ${target(step)}`;
    case 'CHANGE_CASE':
      return `Change ${target(step)} to ${option || 'TITLE'} case`;
    case 'FIND_REPLACE':
      return `Replace "${step.text ?? ''}" with "${step.newText ?? ''}" in ${target(step)}`;
    case 'FILL_EMPTY':
      return `Fill empty cells in ${target(step)} with "${step.text || 'N/A'}"`;
    case 'REMOVE_EMPTY_ROWS':
      return 'Remove empty rows';
    case 'DELETE_ROWS_IF': {
      const rule =
        option === 'EQUALS'
          ? `equals "${step.text ?? ''}"`
          : option === 'CONTAINS'
            ? `contains "${step.text ?? ''}"`
            : 'is empty';
      return `Delete rows where "${step.column}" ${rule}`;
    }
    case 'DELETE_COLUMN':
      return `Delete column "${step.column}"`;
    case 'RENAME_COLUMN':
      return `Rename column "${step.column}" to "${step.newText}"`;
    case 'SPLIT_COLUMN': {
      const names = step.newText ? ` into ${step.newText.split(',').map((n) => `"${n.trim()}"`).join(' and ')}` : ' into two columns';
      return `Split "${step.column}"${names} at the first ${separatorName(step.text)}`;
    }
    case 'MERGE_COLUMNS': {
      const cols = (step.columns || []).map((c) => `"${c}"`).join(' + ');
      const name = step.newText || (step.columns || []).join('_');
      return `Merge ${cols} into "${name}"`;
    }
    case 'FORMAT_DATE':
      return `Change dates in "${step.column}" from ${step.text} to ${option}`;
    case 'SORT_ROWS':
      return `Sort rows by "${step.column}" (${option === 'DESC' ? 'largest / Z first' : 'smallest / A first'})`;
    case 'REMOVE_DUPLICATES': {
      const by = step.columns && step.columns.length > 0 ? ` (comparing ${step.columns.map((c) => `"${c}"`).join(', ')})` : '';
      const caseNote = option === 'IGNORE_CASE' ? ', ignoring capital letters' : '';
      return `Remove duplicate rows${by}${caseNote}`;
    }
    default:
      return step.action;
  }
}

// Steps that REMOVE data get a warning badge, so the user looks twice before confirming
const REMOVES_DATA = ['DELETE_ROWS_IF', 'DELETE_COLUMN', 'REMOVE_DUPLICATES', 'REMOVE_EMPTY_ROWS'];

export function removesData(step) {
  return REMOVES_DATA.includes(step.action);
}
