import { FormEvent, useEffect, useMemo, useState } from 'react'
import './App.css'

type IndexStatus = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED'

type IndexReport = {
  totalFiles: number
  indexed: number
  skipped: number
  failed: number
  deleted: number
  elapsed: string
}

type IndexingSnapshot = {
  status: IndexStatus
  startedAt: string | null
  finishedAt: string | null
  lastReport: IndexReport | null
  lastError: string | null
}

type SearchResult = {
  path: string
  filename: string
  extension: string
  preview: string
  modifiedAt: string
}

const API_BASE = 'http://localhost:7070/api'

function App() {
  const [root, setRoot] = useState('D:\\UTCN\\An3\\Sem2\\SD\\Local-File-Search-System')
  const [ignoreRules, setIgnoreRules] = useState('*.log')
  const [maxFileSizeMb, setMaxFileSizeMb] = useState(10)
  const [previewLines, setPreviewLines] = useState(3)
  const [batchSize, setBatchSize] = useState(250)

  const [indexing, setIndexing] = useState<IndexingSnapshot | null>(null)
  const [indexMessage, setIndexMessage] = useState('')

  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(20)
  const [searchResults, setSearchResults] = useState<SearchResult[]>([])
  const [searchMessage, setSearchMessage] = useState('')

  const running = indexing?.status === 'RUNNING'

  const statusClass = useMemo(() => {
    if (!indexing) return 'status-badge idle'
    switch (indexing.status) {
      case 'RUNNING':
        return 'status-badge running'
      case 'COMPLETED':
        return 'status-badge completed'
      case 'FAILED':
        return 'status-badge failed'
      default:
        return 'status-badge idle'
    }
  }, [indexing])

  useEffect(() => {
    const timer = setInterval(() => {
      void fetchStatus()
    }, 1500)
    void fetchStatus()
    return () => clearInterval(timer)
  }, [])

  async function fetchStatus() {
    try {
      const response = await fetch(`${API_BASE}/index/status`)
      const payload = (await response.json()) as IndexingSnapshot | { message: string }
      if (!response.ok) {
        setIndexMessage((payload as { message: string }).message || 'Failed to fetch indexing status.')
        return
      }
      setIndexing(payload as IndexingSnapshot)
    } catch {
      setIndexMessage('Cannot reach backend at http://localhost:7070. Start server mode first.')
    }
  }

  async function startIndexing(event: FormEvent) {
    event.preventDefault()
    setIndexMessage('')

    const rules = ignoreRules
      .split(',')
      .map((rule) => rule.trim())
      .filter((rule) => rule.length > 0)

    try {
      const response = await fetch(`${API_BASE}/index/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          root,
          dbPath: '',
          ignoreRules: rules,
          maxFileSizeMb,
          previewLines,
          batchSize
        })
      })
      const payload = (await response.json()) as { message: string }
      setIndexMessage(payload.message)
      await fetchStatus()
    } catch {
      setIndexMessage('Failed to start indexing. Ensure backend server is running.')
    }
  }

  async function runSearch(event: FormEvent) {
    event.preventDefault()
    setSearchMessage('')
    try {
      const params = new URLSearchParams({ q: query, limit: String(limit) })
      const response = await fetch(`${API_BASE}/search?${params.toString()}`)
      const payload = await response.json()
      if (!response.ok) {
        setSearchMessage((payload as { message: string }).message || 'Search failed.')
        return
      }
      setSearchResults(payload as SearchResult[])
      if ((payload as SearchResult[]).length === 0) {
        setSearchMessage('No results.')
      }
    } catch {
      setSearchMessage('Failed to search. Ensure backend server is running.')
    }
  }

  return (
    <main className="container">
      <header>
        <h1>Local File Search</h1>
      </header>

      <section className="panel">
        <div className="panel-header">
          <h2>Indexing</h2>
          <span className={statusClass}>{indexing?.status ?? 'IDLE'}</span>
        </div>

        <form className="form-grid" onSubmit={startIndexing}>
          <label>
            Root path
            <input value={root} onChange={(e) => setRoot(e.target.value)} />
          </label>
          <label>
            Ignore rules (comma-separated)
            <input
              value={ignoreRules}
              onChange={(e) => setIgnoreRules(e.target.value)}
              placeholder="*.log, node_modules"
            />
          </label>
          <label>
            Max file size (MB)
            <input
              type="number"
              min={1}
              value={maxFileSizeMb}
              onChange={(e) => setMaxFileSizeMb(Number(e.target.value))}
            />
          </label>
          <label>
            Preview lines
            <input
              type="number"
              min={1}
              value={previewLines}
              onChange={(e) => setPreviewLines(Number(e.target.value))}
            />
          </label>
          <label>
            Batch size
            <input
              type="number"
              min={1}
              value={batchSize}
              onChange={(e) => setBatchSize(Number(e.target.value))}
            />
          </label>
          <button type="submit" disabled={running}>
            {running ? 'Indexing...' : 'Start indexing'}
          </button>
        </form>

        {indexMessage && <p className="message">{indexMessage}</p>}

        {indexing?.lastReport && (
          <div className="stats-grid">
            <div>Total: {indexing.lastReport.totalFiles}</div>
            <div>Indexed: {indexing.lastReport.indexed}</div>
            <div>Skipped: {indexing.lastReport.skipped}</div>
            <div>Failed: {indexing.lastReport.failed}</div>
            <div>Deleted: {indexing.lastReport.deleted}</div>
            <div>Elapsed: {indexing.lastReport.elapsed}</div>
          </div>
        )}

        {indexing?.lastError && <p className="error">{indexing.lastError}</p>}
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>Search</h2>
        </div>
        <form className="search-row" onSubmit={runSearch}>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder='Try: "readme", "ext:java", "config ext:json"'
          />
          <input
            className="limit"
            type="number"
            min={1}
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
          />
          <button type="submit">Search</button>
        </form>

        {searchMessage && <p className="message">{searchMessage}</p>}

        <div className="results">
          {searchResults.map((result) => (
            <article key={result.path} className="result-card">
              <h3>{result.filename}</h3>
              <p className="meta">
                .{result.extension || 'none'} | {new Date(result.modifiedAt).toLocaleString()}
              </p>
              <p className="path">{result.path}</p>
              <pre>{result.preview}</pre>
            </article>
          ))}
        </div>
      </section>
    </main>
  )
}

export default App
