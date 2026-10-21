// Adds up one field across all columns, e.g. total empty cells in the file.
function totalOf(profile, field) {
  return profile.columns.reduce((sum, column) => sum + column[field], 0);
}

// The checks we display. "get" reads the number from a profile object.
const CHECKS = [
  { label: 'Data rows', get: (p) => p.totalRows },
  { label: 'Columns', get: (p) => p.totalColumns },
  { label: 'Duplicate rows', get: (p) => p.duplicateRows },
  { label: 'Duplicate rows (ignoring spaces)', get: (p) => p.duplicateRowsIgnoringSpaces },
  { label: 'Empty rows', get: (p) => p.emptyRows },
  { label: 'Rows with wrong column count', get: (p) => p.rowsWithWrongColumnCount },
  { label: 'Empty cells', get: (p) => totalOf(p, 'emptyCells') },
  { label: 'Cells with extra spaces', get: (p) => totalOf(p, 'cellsWithExtraSpaces') },
];
// Shows a data health table.
// - Only "before" given  -> one column of counts
// - "before" and "after" -> side-by-side comparison, changed values highlighted
function ProfileSummary({ before, after }) {
  return (
    <table className="profile-table">
      <thead>
        <tr>
          <th>Check</th>
          <th>{after ? 'Before' : 'Count'}</th>
          {after && <th>After</th>}
        </tr>
      </thead>
      <tbody>
        {CHECKS.map((check) => {
          const beforeValue = check.get(before);
          const afterValue = after ? check.get(after) : null;
          const changed = after && beforeValue !== afterValue;

          return (
            <tr key={check.label}>
              <td>{check.label}</td>
              <td>{beforeValue}</td>
              {after && <td className={changed ? 'changed' : ''}>{afterValue}</td>}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

export default ProfileSummary;