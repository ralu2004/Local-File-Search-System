import { FormEvent, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
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

type IndexRunRow = {
  id: number
  startedAt: string | null
  finishedAt: string | null
  rootPath: string | null
  totalFiles: number
  indexed: number
  skipped: number
  failed: number
  deleted: number
  elapsedSeconds: number
}

type SearchResult = {
  path: string
  filename: string
  extension: string
  preview: string
  modifiedAt: string
  sizeBytes?: number | null
}

const API_BASE = 'http://localhost:7070/api'

function getFileTypeLabel(extension: string): string {
  const ext = extension?.trim().toLowerCase()
  if (!ext) return 'File'

  const knownTypes: Record<string, string> = {
    txt: 'Text Document',
    md: 'Markdown Document',
    json: 'JSON File',
    xml: 'XML File',
    html: 'HTML Document',
    css: 'Style Sheet',
    js: 'JavaScript File',
    ts: 'TypeScript File',
    java: 'Java Source File',
    py: 'Python Script',
    sql: 'SQL File',
    yml: 'YAML File',
    yaml: 'YAML File',
    csv: 'CSV File',
    pdf: 'PDF Document'
  }

  return knownTypes[ext] ?? `${ext.toUpperCase()} File`
}

function getFolderPath(pathValue: string): string {
  if (!pathValue) return ''
  const withoutScheme = pathValue.replace(/^file:\/+/, '')
  const normalized = withoutScheme.replace(/\\/g, '/')
  const lastSlash = normalized.lastIndexOf('/')
  return lastSlash > 0 ? normalized.slice(0, lastSlash) : withoutScheme
}

function formatModifiedAt(value: unknown): string {
  if (value === null || value === undefined) return 'Unknown date'

  const trimmed = String(value).trim()
  if (!trimmed) return 'Unknown date'

  const parts = trimmed.split(',').map((part) => part.trim())
  if (parts.length >= 6 && parts.every((part) => /^-?\d+$/.test(part))) {
    const year = Number(parts[0])
    const monthIndex = Number(parts[1]) - 1
    const day = Number(parts[2])
    const hour = Number(parts[3])
    const minute = Number(parts[4])
    const second = Number(parts[5])
    const nano = parts[6] ? Number(parts[6]) : 0
    const ms = Math.floor(nano / 1_000_000)
    const fromCommaDate = new Date(year, monthIndex, day, hour, minute, second, ms)
    if (!Number.isNaN(fromCommaDate.getTime())) {
      return fromCommaDate.toLocaleString()
    }
  }

  const numeric = Number(trimmed)
  if (!Number.isNaN(numeric)) {
    const asMs = numeric < 1_000_000_000_000 ? numeric * 1000 : numeric
    const fromNumeric = new Date(asMs)
    if (!Number.isNaN(fromNumeric.getTime())) {
      return fromNumeric.toLocaleString()
    }
  }

  const normalized = trimmed.includes('T') ? trimmed : trimmed.replace(' ', 'T')
  const parsed = new Date(normalized)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString()
  }

  return String(value)
}

function formatFileSize(bytes: unknown): string {
  if (bytes === null || bytes === undefined) return '—'
  const n = typeof bytes === 'number' ? bytes : Number(bytes)
  if (!Number.isFinite(n) || n < 0) return '—'
  if (n < 1024) return `${n} B`
  const kb = n / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  if (mb < 1024) return `${mb.toFixed(1)} MB`
  return `${(mb / 1024).toFixed(2)} GB`
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function buildHighlightTerms(raw: string): string[] {
  const trimmed = raw.trim()
  if (!trimmed) return []

  const fromFilters: string[] = []
  const metaRe = /([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.-]+)/g
  let m: RegExpExecArray | null
  while ((m = metaRe.exec(trimmed)) !== null) {
    const key = m[1].toLowerCase()
    if ((key === 'ext' || key === 'extension') && m[2]) fromFilters.push(m[2])
  }

  let rest = trimmed.replace(/[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+/g, ' ')
  rest = rest.replace(/\s+/g, ' ').trim()

  const textTerms: string[] = []
  const tokenRe = /"([^"]*)"|'([^']*)'|(\S+)/g
  while ((m = tokenRe.exec(rest)) !== null) {
    const chunk = (m[1] ?? m[2] ?? m[3] ?? '').trim()
    if (!chunk) continue
    textTerms.push(chunk)
    for (const w of chunk.split(/\s+/)) {
      const stripped = w.replace(/^[^a-zA-Z0-9._-]+|[^a-zA-Z0-9._-]+$/g, '')
      if (stripped.length >= 2) textTerms.push(stripped)
    }
  }

  const combined = [...fromFilters, ...textTerms]
  const seen = new Set<string>()
  const uniq: string[] = []
  for (const t of combined.sort((a, b) => b.length - a.length)) {
    const k = t.toLowerCase()
    if (k.length < 2) continue
    if (seen.has(k)) continue
    seen.add(k)
    uniq.push(t)
  }
  return uniq
}

function highlightText(text: string, terms: string[], keyPrefix: string): ReactNode {
  if (!text || terms.length === 0) return text

  const pattern = terms
    .map((t) => escapeRegExp(t))
    .filter((p) => p.length > 0)
    .sort((a, b) => b.length - a.length)

  const joined = [...new Set(pattern)].join('|')
  if (!joined) return text

  const re = new RegExp(`(${joined})`, 'gi')
  const out: ReactNode[] = []
  let last = 0
  let hit = 0
  const matcher = new RegExp(re.source, 'gi')
  let match: RegExpExecArray | null
  while ((match = matcher.exec(text)) !== null) {
    if (match.index > last) out.push(text.slice(last, match.index))
    out.push(
      <mark key={`${keyPrefix}-hit-${hit++}`} className="search-hit">
        {match[0]}
      </mark>
    )
    last = match.index + match[0].length
    if (match[0].length === 0) matcher.lastIndex++
  }
  if (last < text.length) out.push(text.slice(last))
  return out.length ? <>{out}</> : text
}

function formatIsoDateTime(iso: string | null | undefined): string {
  if (!iso?.trim()) return '—'
  const d = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString()
}

function formatElapsed(value: unknown): string {
  if (value === null || value === undefined) return '—'

  if (typeof value === 'number' || /^-?\d+(\.\d+)?$/.test(String(value).trim())) {
    const totalSeconds = typeof value === 'number' ? value : Number(String(value).trim())
    if (!Number.isFinite(totalSeconds) || totalSeconds < 0) return String(value)

    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60

    if (hours > 0) return `${hours}h ${minutes}m ${seconds.toFixed(1)}s`
    if (minutes > 0) return `${minutes}m ${seconds.toFixed(1)}s`
    return `${seconds.toFixed(1)}s`
  }

  return String(value)
}

function App() {
  const [root, setRoot] = useState('D:\\UTCN\\An3\\Sem2\\SD\\Local-File-Search-System')
  const [ignoreRules, setIgnoreRules] = useState('*.log')
  const [maxFileSizeMb, setMaxFileSizeMb] = useState(10)
  const [previewLines, setPreviewLines] = useState(3)
  const [batchSize, setBatchSize] = useState(250)

  const [activeSection, setActiveSection] = useState<'index' | 'search'>('search')
  const [indexHistory, setIndexHistory] = useState<IndexRunRow[]>([])
  const indexStatusRef = useRef<IndexStatus | null>(null)

  const [indexing, setIndexing] = useState<IndexingSnapshot | null>(null)
  const [indexMessage, setIndexMessage] = useState('')

  const [query, setQuery] = useState('')
  const [limit, setLimit] = useState(20)
  const [searchResults, setSearchResults] = useState<SearchResult[]>([])
  const [searchMessage, setSearchMessage] = useState('')
  const [activeSearchQuery, setActiveSearchQuery] = useState('')

  const highlightTerms = useMemo(() => buildHighlightTerms(activeSearchQuery), [activeSearchQuery])

  const running = indexing?.status === 'RUNNING'

  const latestCompletedRun = useMemo(
    () => indexHistory.find((r) => r.finishedAt) ?? indexHistory[0] ?? null,
    [indexHistory]
  )

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
    void fetchIndexHistory()
    const timer = setInterval(() => {
      void fetchStatus()
    }, 1500)
    void fetchStatus()
    return () => clearInterval(timer)
  }, [])

  async function fetchIndexHistory() {
    try {
      const response = await fetch(`${API_BASE}/index/history?limit=10`)
      const payload = (await response.json()) as IndexRunRow[] | { message: string }
      if (!response.ok) return
      if (!Array.isArray(payload)) return
      setIndexHistory(payload)
    } catch {
      /* keep previous history */
    }
  }

  async function fetchStatus() {
    try {
      const response = await fetch(`${API_BASE}/index/status`)
      const payload = (await response.json()) as IndexingSnapshot | { message: string }
      if (!response.ok) {
        setIndexMessage((payload as { message: string }).message || 'Failed to fetch indexing status.')
        return
      }
      const snap = payload as IndexingSnapshot
      const prev = indexStatusRef.current
      indexStatusRef.current = snap.status
      setIndexing(snap)
      if ((snap.status === 'COMPLETED' || snap.status === 'FAILED') && prev === 'RUNNING') {
        void fetchIndexHistory()
      }
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
      setActiveSearchQuery(query)
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
      <header className="app-header">
        <div className="app-header-top">
          <h1>Local File Search</h1>
          <nav className="app-nav" aria-label="Primary">
            <button
              type="button"
              className={activeSection === 'index' ? 'nav-tab active' : 'nav-tab'}
              onClick={() => setActiveSection('index')}
            >
              Index
            </button>
            <button
              type="button"
              className={activeSection === 'search' ? 'nav-tab active' : 'nav-tab'}
              onClick={() => setActiveSection('search')}
            >
              Search
            </button>
          </nav>
        </div>
      </header>

      {activeSection === 'index' && (
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
            <div>Elapsed: {formatElapsed(indexing.lastReport.elapsed)}</div>
          </div>
        )}

        {indexing?.lastError && <p className="error">{indexing.lastError}</p>}

        {latestCompletedRun?.finishedAt && (
          <div className="last-run-panel">
            <h3 className="last-run-heading">Last saved index run (database)</h3>
            <div className="last-run-grid">
              <span className="last-run-k">Finished</span>
              <span className="last-run-v">{formatIsoDateTime(latestCompletedRun.finishedAt)}</span>
              <span className="last-run-k">Root</span>
              <span className="last-run-v">{latestCompletedRun.rootPath?.trim() || '—'}</span>
              <span className="last-run-k">Totals</span>
              <span className="last-run-v">
                {latestCompletedRun.totalFiles} scanned · {latestCompletedRun.indexed} indexed ·{' '}
                {latestCompletedRun.skipped} skipped · {latestCompletedRun.failed} failed ·{' '}
                {latestCompletedRun.deleted} removed
              </span>
              <span className="last-run-k">Duration</span>
              <span className="last-run-v">{formatElapsed(latestCompletedRun.elapsedSeconds)}</span>
            </div>
          </div>
        )}
      </section>
      )}

      {activeSection === 'search' && (
      <section className="panel">
        <div className="panel-header">
          <h2>Search</h2>
        </div>

        <div className="search-context">
          <p className="search-context-main">
            <span className="search-context-label">Indexed folder</span>{' '}
            <span className="search-context-path">
              {latestCompletedRun?.rootPath?.trim() || 'Run indexing to record a corpus root in the database.'}
            </span>
          </p>
          {latestCompletedRun?.finishedAt && (
            <p className="search-context-sub">
              Last index: {formatIsoDateTime(latestCompletedRun.finishedAt)} ·{' '}
              {latestCompletedRun.indexed} files indexed · {formatElapsed(latestCompletedRun.elapsedSeconds)}
            </p>
          )}
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
              <h3>
                {highlightTerms.length > 0
                  ? highlightText(result.filename, highlightTerms, `${result.path}-fn`)
                  : result.filename}
              </h3>
              <div className="meta-grid">
                <p className="meta-item">
                  <span className="meta-label">Type</span>
                  <span className="meta-value">{getFileTypeLabel(result.extension)}</span>
                </p>
                <p className="meta-item">
                  <span className="meta-label">Size</span>
                  <span className="meta-value">{formatFileSize(result.sizeBytes)}</span>
                </p>
                <p className="meta-item">
                  <span className="meta-label">Date modified</span>
                  <span className="meta-value">{formatModifiedAt(result.modifiedAt)}</span>
                </p>
                <p className="meta-item meta-item-wide">
                  <span className="meta-label">Folder</span>
                  <span className="meta-value">{getFolderPath(result.path)}</span>
                </p>
              </div>
              <pre className="preview-snippet">
                {highlightTerms.length > 0
                  ? highlightText(result.preview, highlightTerms, `${result.path}-pv`)
                  : result.preview}
              </pre>
            </article>
          ))}
        </div>
      </section>
      )}
    </main>
  )
}

export default App
