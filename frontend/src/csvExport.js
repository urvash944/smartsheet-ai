// Turns table data back into CSV text, and downloads it as a file.
// Everything happens in the browser, so the data is never uploaded again.

// CSV rules for one cell:
// if a cell contains a comma, a double quote, or a line break,
// wrap it in double quotes and double any quotes inside.
// Example:  Iyer, Karthik   ->  "Iyer, Karthik"
//           says "hi"       ->  "says ""hi"""
function escapeCell(value) {
  const text = value ?? ''; // treat a missing cell as empty
  if (/[",\r\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

// Builds the full CSV text: header line first, then one line per row.
// "\r\n" is the standard CSV line ending (it works well in Excel).
export function toCsv(headers, rows) {
  const lines = [headers, ...rows].map((row) => row.map(escapeCell).join(','));
  return lines.join('\r\n') + '\r\n';
}

// "customers.csv" -> "customers_cleaned.csv"
export function cleanedFileName(originalName) {
  const base = (originalName || 'data.csv').replace(/\.csv$/i, '');
  return `${base}_cleaned.csv`;
}

// Creates a file in the browser's memory and starts the download.
export function downloadCsv(fileName, headers, rows) {
  // "\uFEFF" is a BOM: an invisible mark that tells Excel the file is UTF-8,
  // so characters like é or ₹ display correctly.
  // (Our Java CsvService already removes it if this file is uploaded again.)
  const csvText = '\uFEFF' + toCsv(headers, rows);

  // A Blob is a file that lives only in the browser's memory
  const blob = new Blob([csvText], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);

  // Create an invisible link, click it, then clean up
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url); // free the memory
}