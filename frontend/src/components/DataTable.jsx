// Only show the first 20 rows, so huge files don't freeze the browser.
const PREVIEW_LIMIT = 20;

// A reusable table. "headers" = column names, "rows" = list of rows.
function DataTable({ headers, rows }) {
  const visibleRows = rows.slice(0, PREVIEW_LIMIT);

  return (
    <div className="table-wrapper">
      <table className="data-table">
        <thead>
          <tr>
            <th className="row-number">#</th>
            {headers.map((header, index) => (
              <th key={index}>{header}</th>
            ))}
          </tr>
        </thead>

        <tbody>
          {visibleRows.map((row, rowIndex) => {
            // A "broken" row has more or fewer cells than there are columns
            const isBroken = row.length !== headers.length;
            const problem =
              row.length < headers.length
                ? `This row has ${headers.length - row.length} missing cell(s)`
                : `This row has ${row.length - headers.length} extra cell(s) that are not shown`;

            return (
              <tr
                key={rowIndex}
                className={isBroken ? 'row-broken' : ''}
                title={isBroken ? problem : undefined} // message shown on mouse hover
              >
                <td className="row-number">
                  {rowIndex + 1}
                  {isBroken && ' ⚠️'}
                </td>
                {headers.map((_, colIndex) => (
                  <td key={colIndex}>{row[colIndex] ?? ''}</td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>

      {rows.length > PREVIEW_LIMIT && (
        <p className="table-note">
          Showing the first {PREVIEW_LIMIT} of {rows.length} rows. The download contains all rows.
        </p>
      )}
    </div>
  );
}

export default DataTable;