import { FormEvent, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
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

type IndexingLiveProgress = {
  totalFiles: number
  indexed: number
  skipped: number
  failed: number
  pendingInBatch: number
  phase: string
}

type IndexingSnapshot = {
  status: IndexStatus
  startedAt: string | null
  finishedAt: string | null
  lastReport: IndexReport | null
  lastError: string | null
  liveProgress: IndexingLiveProgress | null
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
  modifiedAt: unknown
  sizeBytes?: number | null
}

type RankedSearchResult = {
  result: SearchResult
  insights?: string[]
}

type SortMode = 'default' | 'balanced' | 'date' | 'alpha' | 'behavior'

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
    if (key === 'content' && m[2]) fromFilters.push(m[2])
    if (key === 'path' && m[2]) fromFilters.push(m[2])
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

function phaseLabel(phase: string | undefined): string {
  const p = (phase ?? 'crawling').toLowerCase()
  if (p === 'finalizing') return 'Finalizing index (writes, cleanup, optional optimize)…'
  return 'Scanning files…'
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

function buildRequestQuery(rawQuery: string, sortMode: SortMode): string {
  const base = rawQuery.replace(/\bsort:[^\s]+/gi, '').replace(/\s+/g, ' ').trim()
  if (!base) return ''
  if (sortMode === 'default') return base
  return `${base} sort:${sortMode}`
}

function stripSortQualifier(rawQuery: string): string {
  return rawQuery.replace(/\bsort:[^\s]+/gi, '').replace(/\s+/g, ' ').trim()
}

function isSearchResult(value: unknown): value is SearchResult {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.path === 'string' &&
    typeof candidate.filename === 'string' &&
    typeof candidate.extension === 'string' &&
    typeof candidate.preview === 'string'
  )
}

function normalizeRankedResults(payload: unknown): RankedSearchResult[] {
  if (!Array.isArray(payload)) return []
  const normalized: RankedSearchResult[] = []

  for (const item of payload) {
    if (!item || typeof item !== 'object') continue
    const candidate = item as Record<string, unknown>

    if (isSearchResult(item)) {
      normalized.push({ result: item, insights: [] })
      continue
    }

    if (isSearchResult(candidate.result)) {
      const insights = Array.isArray(candidate.insights)
        ? candidate.insights.filter((entry): entry is string => typeof entry === 'string')
        : []
      normalized.push({ result: candidate.result, insights })
    }
  }

  return normalized
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
  const [sortMode, setSortMode] = useState<SortMode>('default')
  const [limit, setLimit] = useState(20)
  const [searchResults, setSearchResults] = useState<RankedSearchResult[]>([])
  const [searchMessage, setSearchMessage] = useState('')
  const [activeSearchQuery, setActiveSearchQuery] = useState('')
  const [searchSuggestions, setSearchSuggestions] = useState<string[]>([])
  const [recentSearches, setRecentSearches] = useState<string[]>([])
  const [showSuggestionBox, setShowSuggestionBox] = useState(false)
  const [isLoadingSuggestions, setIsLoadingSuggestions] = useState(false)
  const [isLoadingRecent, setIsLoadingRecent] = useState(false)
  const [openEventMessage, setOpenEventMessage] = useState('')
  const [openedResultPaths, setOpenedResultPaths] = useState<Set<string>>(new Set())

  const searchDebounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const suggestDebounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const hideSuggestionBoxTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const searchAbortRef = useRef<AbortController | null>(null)
  const searchRequestIdRef = useRef(0)
  const suggestRequestIdRef = useRef(0)
  const [, setIsSearching] = useState(false)

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
  }, [])

  useEffect(() => {
    void fetchStatus()
    const ms = running ? 600 : 2200
    const timer = setInterval(() => {
      void fetchStatus()
    }, ms)
    return () => clearInterval(timer)
  }, [running])

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

  const doSearch = useCallback(
    async (nextQuery: string) => {
    const q = nextQuery.trim()
    const requestQuery = buildRequestQuery(q, sortMode)

    searchAbortRef.current?.abort()
    searchAbortRef.current = null

    if (!requestQuery) {
      searchRequestIdRef.current += 1
      setSearchResults([])
      setActiveSearchQuery('')
      setSearchMessage('')
      setIsSearching(false)
      return
    }

    const requestId = ++searchRequestIdRef.current
    const controller = new AbortController()
    searchAbortRef.current = controller

    setIsSearching(true)
    setSearchMessage('')

    try {
      const params = new URLSearchParams({ q: requestQuery, limit: String(limit) })
      const response = await fetch(`${API_BASE}/search?${params.toString()}`, { signal: controller.signal })
      const payload = await response.json().catch(() => ({} as unknown))

      if (requestId !== searchRequestIdRef.current) return

      if (!response.ok) {
        setSearchMessage((payload as { message?: string }).message || 'Search failed.')
        setSearchResults([])
        return
      }

      const results = normalizeRankedResults(payload)
      setActiveSearchQuery(q)
      setSearchResults(results)
      setSearchMessage(results.length === 0 ? 'No results.' : '')
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      if (requestId !== searchRequestIdRef.current) return
      setSearchMessage('Failed to search. Ensure backend server is running.')
      setSearchResults([])
    } finally {
      if (requestId === searchRequestIdRef.current) setIsSearching(false)
    }
    },
    [limit, sortMode]
  )

  useEffect(() => {
    if (activeSection !== 'search') {
      searchAbortRef.current?.abort()
      if (searchDebounceTimerRef.current) {
        clearTimeout(searchDebounceTimerRef.current)
        searchDebounceTimerRef.current = null
      }
      if (suggestDebounceTimerRef.current) {
        clearTimeout(suggestDebounceTimerRef.current)
        suggestDebounceTimerRef.current = null
      }
      if (hideSuggestionBoxTimerRef.current) {
        clearTimeout(hideSuggestionBoxTimerRef.current)
        hideSuggestionBoxTimerRef.current = null
      }
      setShowSuggestionBox(false)
      return
    }

    if (searchDebounceTimerRef.current) {
      clearTimeout(searchDebounceTimerRef.current)
      searchDebounceTimerRef.current = null
    }

    searchDebounceTimerRef.current = setTimeout(() => {
      void doSearch(query)
    }, 250)

    return () => {
      if (searchDebounceTimerRef.current) {
        clearTimeout(searchDebounceTimerRef.current)
        searchDebounceTimerRef.current = null
      }
    }
  }, [query, activeSection, doSearch])

  useEffect(() => {
    if (activeSection !== 'search') return
    if (suggestDebounceTimerRef.current) {
      clearTimeout(suggestDebounceTimerRef.current)
      suggestDebounceTimerRef.current = null
    }
    const trimmed = query.trim()
    if (!trimmed) {
      setSearchSuggestions([])
      return
    }
    suggestDebounceTimerRef.current = setTimeout(() => {
      void fetchSearchSuggestions(trimmed)
    }, 200)
    return () => {
      if (suggestDebounceTimerRef.current) {
        clearTimeout(suggestDebounceTimerRef.current)
        suggestDebounceTimerRef.current = null
      }
    }
  }, [query, activeSection])

  useEffect(() => {
    if (activeSection !== 'search') return
    void fetchRecentSearches()
  }, [activeSection])

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
    void doSearch(query)
  }

  async function fetchSearchSuggestions(prefix: string) {
    const requestId = ++suggestRequestIdRef.current
    setIsLoadingSuggestions(true)
    try {
      const params = new URLSearchParams({ q: prefix, limit: '8' })
      const response = await fetch(`${API_BASE}/search/suggest?${params.toString()}`)
      const payload = (await response.json()) as string[] | { message?: string }
      if (requestId !== suggestRequestIdRef.current) return
      if (!response.ok || !Array.isArray(payload)) {
        setSearchSuggestions([])
        return
      }
      setSearchSuggestions(
        payload
          .map((item) => stripSortQualifier(item))
          .filter((item) => item.length > 0)
      )
    } catch {
      if (requestId !== suggestRequestIdRef.current) return
      setSearchSuggestions([])
    } finally {
      if (requestId === suggestRequestIdRef.current) setIsLoadingSuggestions(false)
    }
  }

  async function fetchRecentSearches() {
    setIsLoadingRecent(true)
    try {
      const params = new URLSearchParams({ limit: '8' })
      const response = await fetch(`${API_BASE}/search/history?${params.toString()}`)
      const payload = (await response.json()) as string[] | { message?: string }
      if (!response.ok || !Array.isArray(payload)) {
        setRecentSearches([])
        return
      }
      setRecentSearches(
        payload
          .map((item) => stripSortQualifier(item))
          .filter((item) => item.length > 0)
      )
    } catch {
      setRecentSearches([])
    } finally {
      setIsLoadingRecent(false)
    }
  }

  function triggerSearchFromSelection(value: string) {
    const cleaned = stripSortQualifier(value)
    setQuery(cleaned)
    setShowSuggestionBox(false)
    void doSearch(cleaned)
  }

  function handleSearchInputFocus() {
    if (hideSuggestionBoxTimerRef.current) {
      clearTimeout(hideSuggestionBoxTimerRef.current)
      hideSuggestionBoxTimerRef.current = null
    }
    setShowSuggestionBox(true)
    if (query.trim().length === 0) {
      void fetchRecentSearches()
    }
  }

  function handleSearchInputBlur() {
    hideSuggestionBoxTimerRef.current = setTimeout(() => {
      setShowSuggestionBox(false)
    }, 150)
  }

  async function trackOpenEvent(filePath: string, resultPosition: number) {
    if (!activeSearchQuery.trim()) return
    try {
      const response = await fetch(`${API_BASE}/search/open`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          query: activeSearchQuery,
          filePath,
          resultPosition
        })
      })
      if (!response.ok) {
        setOpenEventMessage('Could not record open event.')
        return
      }
      setOpenedResultPaths((prev) => {
        const next = new Set(prev)
        next.add(filePath)
        return next
      })
      setOpenEventMessage('Open event recorded.')
    } catch {
      setOpenEventMessage('Could not record open event.')
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

        {running && indexing?.liveProgress && (
          <div className="index-progress" aria-busy="true">
            <div className="index-progress-track">
              <div className="index-progress-indicator" />
            </div>
            <p className="index-progress-phase">{phaseLabel(indexing.liveProgress.phase)}</p>
            <ul className="index-progress-stats">
              <li>
                <strong>{indexing.liveProgress.totalFiles.toLocaleString()}</strong> scanned
              </li>
              <li>
                <strong>{indexing.liveProgress.indexed.toLocaleString()}</strong> indexed
              </li>
              <li>
                <strong>{indexing.liveProgress.skipped.toLocaleString()}</strong> skipped
              </li>
              <li>
                <strong>{indexing.liveProgress.failed.toLocaleString()}</strong> failed
              </li>
              {indexing.liveProgress.pendingInBatch > 0 && (
                <li>
                  <strong>{indexing.liveProgress.pendingInBatch}</strong> queued in batch
                </li>
              )}
            </ul>
            {indexing.startedAt && (
              <p className="index-progress-elapsed">
                Elapsed so far — {formatElapsed((Date.now() - new Date(indexing.startedAt).getTime()) / 1000)}
              </p>
            )}
          </div>
        )}

        {indexing?.status === 'COMPLETED' && indexing.lastReport ? (
          <p className="message message-success">Indexing finished successfully.</p>
        ) : indexMessage ? (
          <p className="message">{indexMessage}</p>
        ) : null}

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

        {indexing?.status === 'FAILED' && indexing.lastError && <p className="error">{indexing.lastError}</p>}

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

        <details className="search-query-help">
          <summary>Search syntax & filters</summary>
          <ul className="search-query-help-list">
            <li>
              <code>word</code> or <code>&quot;phrase&quot;</code> — full-text search in file names and content (combine with
              filters below).
            </li>
            <li>
              <code>name.ext</code> — if the whole query looks like a file name, it matches filename only.
            </li>
            <li>
              <code>path:A/B</code> — files in that path.
            </li>
            <li>
              <code>content:some text</code> — files containing the specified text.
            </li>
            <li>
              <code>ext:java</code> — files with that extension (also <code>extension:md</code>).
            </li>
            <li>
              <code>modified:2024-01-15</code> — modified after this date/time (use the same format as stored in DB,
              e.g. ISO-like <code>2024-01-15T10:00:00</code> if needed).
            </li>
            <li>
              <code>size:1048576</code> — file larger than this many <strong>bytes</strong> (default unit).
            </li>
            <li>
              <code>size:10kb</code>, <code>size:5mb</code>, <code>size:1gb</code> — size filter with units.
            </li>
            <li>
              Examples: <code>readme ext:md</code>, <code>config ext:json</code>, <code>todo size:500</code>.
            </li>
          </ul>
        </details>

        <form className="search-row" onSubmit={runSearch}>
          <div className="search-input-wrap">
            <span className="search-input-icon" aria-hidden="true">
              #
            </span>
            <input
              className="search-query-input"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onFocus={handleSearchInputFocus}
              onBlur={handleSearchInputBlur}
              placeholder='Try: "readme", "ext:java", "config ext:json"'
            />
            {showSuggestionBox && (
              <div className="suggestion-box" role="listbox" aria-label="Search suggestions and history">
                {query.trim() ? (
                  <>
                    <p className="suggestion-heading">Suggestions</p>
                    {isLoadingSuggestions ? (
                      <p className="suggestion-empty">Loading suggestions...</p>
                    ) : searchSuggestions.length > 0 ? (
                      <ul className="suggestion-list">
                        {searchSuggestions.map((suggestion) => (
                          <li key={`s-${suggestion}`}>
                            <button
                              type="button"
                              className="suggestion-item"
                              onMouseDown={(e) => {
                                e.preventDefault()
                                triggerSearchFromSelection(suggestion)
                              }}
                            >
                              {suggestion}
                            </button>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="suggestion-empty">No suggestions yet.</p>
                    )}
                  </>
                ) : (
                  <>
                    <p className="suggestion-heading">Recent searches</p>
                    {isLoadingRecent ? (
                      <p className="suggestion-empty">Loading recent searches...</p>
                    ) : recentSearches.length > 0 ? (
                      <div className="recent-chip-list">
                        {recentSearches.map((recent) => (
                          <button
                            key={`r-${recent}`}
                            type="button"
                            className="recent-chip"
                            onMouseDown={(e) => {
                              e.preventDefault()
                              triggerSearchFromSelection(recent)
                            }}
                          >
                            {recent}
                          </button>
                        ))}
                      </div>
                    ) : (
                      <p className="suggestion-empty">No recent searches yet.</p>
                    )}
                  </>
                )}
              </div>
            )}
          </div>
          <input
            className="limit"
            type="number"
            min={1}
            value={limit}
            onChange={(e) => setLimit(Number(e.target.value))}
          />
          <div className="sort-selector">
            <select value={sortMode} onChange={(e) => setSortMode(e.target.value as SortMode)}>
              <option value="default">Default (static)</option>
              <option value="balanced">Balanced</option>
              <option value="date">Date (newest first)</option>
              <option value="alpha">Alphabetical</option>
              <option value="behavior">Personalized</option>
            </select>
          </div>
          <button type="submit">Search</button>
        </form>

        {searchMessage && <p className="message">{searchMessage}</p>}
        {openEventMessage && <p className="message">{openEventMessage}</p>}

        <div className="results">
          {searchResults.map((rankedResult, index) => {
            const result = rankedResult.result
            const insights = Array.isArray(rankedResult.insights) ? rankedResult.insights : []
            const showInsights = sortMode === 'behavior' && insights.length > 0
            const isOpened = openedResultPaths.has(result.path)
            return (
            <article key={result.path} className="result-card">
              <div className="result-header">
                <h3>
                  {highlightTerms.length > 0
                    ? highlightText(result.filename, highlightTerms, `${result.path}-fn`)
                    : result.filename}
                </h3>
                <button
                  type="button"
                  className={isOpened ? 'result-open-button opened' : 'result-open-button'}
                  onClick={() => void trackOpenEvent(result.path, index + 1)}
                >
                  {isOpened ? 'Opened ✓' : 'Mark as opened'}
                </button>
              </div>
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
                  <span className="meta-value">
                    {highlightTerms.length > 0
                      ? highlightText(getFolderPath(result.path), highlightTerms, `${result.path}-fp`)
                      : getFolderPath(result.path)}
                  </span>
                </p>
              </div>
              <pre className="preview-snippet">
                {highlightTerms.length > 0
                  ? highlightText(result.preview, highlightTerms, `${result.path}-pv`)
                  : result.preview}
              </pre>
              {showInsights && (
                <ul className="result-insights" aria-label="Behavior ranking insights">
                  {insights.map((insight, insightIndex) => (
                    <li key={`${result.path}-insight-${insightIndex}`}>{insight}</li>
                  ))}
                </ul>
              )}
            </article>
            )
          })}
        </div>
      </section>
      )}
    </main>
  )
}

export default App
