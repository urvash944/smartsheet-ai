// Only show the first 20 rows, so huge files don't freeze the browser.
const PREVIEW_LIMIT = 20;

// A reusable table. It receives "headers" and "rows" as props
// (props = inputs passed into a component, like function arguments).
function DataTable({ headers, rows }) {
  const visibleRows = rows.slice(0, PREVIEW_LIMIT);

  return (
    <div className="table-wrapper">
      <table className="data-table">
        <thead>
          <tr>
            {/* One <th> header cell for each column name */}
            {headers.map((header, index) => (
              <th key={index}>{header}</th>
            ))}
          </tr>
        </thead>

        <tbody>
          {visibleRows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              {/* Loop over headers (not row) so every row has the same number of cells.
                  If a row is shorter, "?? ''" shows an empty cell instead of crashing. */}
              {headers.map((_, colIndex) => (
                <td key={colIndex}>{row[colIndex] ?? ''}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {rows.length > PREVIEW_LIMIT && (
        <p className="table-note">
          Showing the first {PREVIEW_LIMIT} of {rows.length} rows.
        </p>
      )}
    </div>
  );
}

export default DataTable;