import './App.css';

// App is the main component of our website.
// For now it only shows a title and placeholder sections.
// In later steps we will add file upload, instructions, and preview.
function App() {
  return (
    <div className="app">
      <header className="header">
        <h1>SmartSheet AI</h1>
        <p className="subtitle">CSV Data Cleaning Agent</p>
      </header>

      <main className="content">
        <section className="card">
          <h2>1. Upload your CSV</h2>
          <p className="placeholder">File upload will go here (Step 5).</p>
        </section>

        <section className="card">
          <h2>2. Tell the AI what to clean</h2>
          <p className="placeholder">Instruction box will go here (Step 7).</p>
        </section>

        <section className="card">
          <h2>3. Preview and download</h2>
          <p className="placeholder">Cleaned data preview will go here (Step 8).</p>
        </section>
      </main>
    </div>
  );
}

// "export default" lets main.jsx import and display this component.
export default App;