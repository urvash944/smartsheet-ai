// The 14 building blocks, as the EDITOR shows them.
// "action" names must match PlanAction in Java.
//
// Each block lists its settings ("fields"). The editor draws one input per field:
//   kind "column"  -> one column name (typing gives suggestions)
//   kind "columns" -> several column names, separated by commas
//   kind "choice"  -> a dropdown with fixed choices
//   kind "text"    -> free text
// "key" is the PlanStep field it fills: column, columns, option, text or newText.

const DATE_FORMATS = ['DD/MM/YYYY', 'MM/DD/YYYY', 'YYYY-MM-DD', 'DD-MM-YYYY', 'DD.MM.YYYY'];

export const BLOCK_GROUPS = ['Text', 'Rows', 'Columns'];

export const BLOCKS = {
  TRIM_WHITESPACE: {
    label: 'Trim spaces',
    group: 'Text',
    hint: 'Remove spaces at the start and end of cells',
    fields: [{ key: 'column', kind: 'column', label: 'Column', optional: true }],
  },
  COLLAPSE_SPACES: {
    label: 'Collapse spaces',
    group: 'Text',
    hint: 'Turn "Asha    Patel" into "Asha Patel"',
    fields: [{ key: 'column', kind: 'column', label: 'Column', optional: true }],
  },
  CHANGE_CASE: {
    label: 'Change case',
    group: 'Text',
    hint: 'UPPER, lower or Title case',
    fields: [
      { key: 'column', kind: 'column', label: 'Column', optional: true },
      { key: 'option', kind: 'choice', label: 'Case', choices: ['UPPER', 'LOWER', 'TITLE'] },
    ],
  },
  FIND_REPLACE: {
    label: 'Find & replace',
    group: 'Text',
    hint: 'Replace plain text (not patterns)',
    fields: [
      { key: 'column', kind: 'column', label: 'Column', optional: true },
      { key: 'text', kind: 'text', label: 'Find' },
      { key: 'newText', kind: 'text', label: 'Replace with', optional: true },
      { key: 'option', kind: 'choice', label: 'Capitals', choices: ['', 'IGNORE_CASE'], choiceLabels: ['Must match', 'Ignore'] },
    ],
  },
  FILL_EMPTY: {
    label: 'Fill empty cells',
    group: 'Text',
    hint: 'Put a value in blank cells',
    fields: [
      { key: 'column', kind: 'column', label: 'Column', optional: true },
      { key: 'text', kind: 'text', label: 'Fill with', optional: true, placeholder: 'N/A' },
    ],
  },
  REMOVE_EMPTY_ROWS: {
    label: 'Remove empty rows',
    group: 'Rows',
    hint: 'Rows where every cell is blank',
    fields: [],
  },
  DELETE_ROWS_IF: {
    label: 'Delete rows if...',
    group: 'Rows',
    hint: 'Delete rows that match a rule',
    fields: [
      { key: 'column', kind: 'column', label: 'Column' },
      { key: 'option', kind: 'choice', label: 'Rule', choices: ['IS_EMPTY', 'EQUALS', 'CONTAINS'], choiceLabels: ['is empty', 'equals', 'contains'] },
      { key: 'text', kind: 'text', label: 'Value', showIf: (step) => step.option !== 'IS_EMPTY' },
    ],
  },
  SORT_ROWS: {
    label: 'Sort rows',
    group: 'Rows',
    hint: 'Numbers sort as numbers',
    fields: [
      { key: 'column', kind: 'column', label: 'Column' },
      { key: 'option', kind: 'choice', label: 'Order', choices: ['ASC', 'DESC'], choiceLabels: ['Smallest / A first', 'Largest / Z first'] },
    ],
  },
  REMOVE_DUPLICATES: {
    label: 'Remove duplicates',
    group: 'Rows',
    hint: 'Keep the first copy of each row',
    fields: [
      { key: 'columns', kind: 'columns', label: 'Compare only these columns', optional: true, placeholder: 'all columns' },
      { key: 'option', kind: 'choice', label: 'Capitals', choices: ['EXACT', 'IGNORE_CASE'], choiceLabels: ['Must match', 'Ignore'] },
    ],
  },
  DELETE_COLUMN: {
    label: 'Delete column',
    group: 'Columns',
    hint: 'Remove a whole column',
    fields: [{ key: 'column', kind: 'column', label: 'Column' }],
  },
  RENAME_COLUMN: {
    label: 'Rename column',
    group: 'Columns',
    hint: 'Give a column a new name',
    fields: [
      { key: 'column', kind: 'column', label: 'Column' },
      { key: 'newText', kind: 'text', label: 'New name' },
    ],
  },
  SPLIT_COLUMN: {
    label: 'Split column',
    group: 'Columns',
    hint: '"Asha Patel" → "Asha" + "Patel"',
    fields: [
      { key: 'column', kind: 'column', label: 'Column' },
      { key: 'text', kind: 'text', label: 'Separator', optional: true, placeholder: 'space' },
      { key: 'newText', kind: 'text', label: 'New names (a,b)', optional: true, placeholder: 'first_name,last_name' },
    ],
  },
  MERGE_COLUMNS: {
    label: 'Merge columns',
    group: 'Columns',
    hint: 'Join columns into one',
    fields: [
      { key: 'columns', kind: 'columns', label: 'Columns to merge', placeholder: 'first_name, last_name' },
      { key: 'text', kind: 'text', label: 'Separator', optional: true, placeholder: 'space' },
      { key: 'newText', kind: 'text', label: 'New name', optional: true },
    ],
  },
  FORMAT_DATE: {
    label: 'Format dates',
    group: 'Columns',
    hint: '15/08/2024 → 2024-08-15',
    fields: [
      { key: 'column', kind: 'column', label: 'Column' },
      { key: 'text', kind: 'choice', label: 'From', choices: DATE_FORMATS },
      { key: 'option', kind: 'choice', label: 'To', choices: ['YYYY-MM-DD', ...DATE_FORMATS.filter((f) => f !== 'YYYY-MM-DD')] },
    ],
  },
};
