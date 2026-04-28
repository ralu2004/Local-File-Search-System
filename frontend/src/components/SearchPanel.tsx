import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'

import { indexApi, searchApi } from '../api/client'
import type { IndexRunRow, RankedSearchResult, SortMode } from '../types'
import { formatElapsed, formatIsoDateTime } from '../utils/format'
import { buildRequestQuery, normalizeRankedResults, stripSortFilter } from '../utils/normalize'
import ResultCard from './ResultCard'
import SuggestionBox from './SuggestionBox'

function isIndexHistoryPayload(payload: unknown): payload is IndexRunRow[] {
  return Array.isArray(payload)
}

function isStringList(payload: unknown): payload is string[] {
  return Array.isArray(payload) && payload.every((item) => typeof item === 'string')
}

export default function SearchPanel() {
  const pageSize = 5
  const [indexHistory, setIndexHistory] = useState<IndexRunRow[]>([])
  const [query, setQuery] = useState('')
  const [sortMode, setSortMode] = useState<SortMode>('default')
  const [limitInput, setLimitInput] = useState('20')
  const [searchResults, setSearchResults] = useState<RankedSearchResult[]>([])
  const [currentPage, setCurrentPage] = useState(1)
  const [searchMessage, setSearchMessage] = useState('')
  const [activeSearchQuery, setActiveSearchQuery] = useState('')
  const [searchSuggestions, setSearchSuggestions] = useState<string[]>([])
  const [recentSearches, setRecentSearches] = useState<string[]>([])
  const [showSuggestionBox, setShowSuggestionBox] = useState(false)
  const [isLoadingSuggestions, setIsLoadingSuggestions] = useState(false)
  const [isLoadingRecent, setIsLoadingRecent] = useState(false)
  const [openEventMessage, setOpenEventMessage] = useState('')
  const [openedResultPaths, setOpenedResultPaths] = useState<Set<string>>(new Set())
  const searchDebounceTimerRef = useRef<number | null>(null)
  const suggestDebounceTimerRef = useRef<number | null>(null)
  const hideSuggestionBoxTimerRef = useRef<number | null>(null)
  const searchAbortRef = useRef<AbortController | null>(null)
  const searchRequestIdRef = useRef(0)
  const suggestRequestIdRef = useRef(0)

  const latestCompletedRun = useMemo(
    () => indexHistory.find((run) => run.finishedAt) ?? indexHistory[0] ?? null,
    [indexHistory],
  )
  const limit = useMemo(() => {
    const parsed = Number(limitInput)
    return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 20
  }, [limitInput])
  const totalPages = Math.max(1, Math.ceil(searchResults.length / pageSize))
  const pagedResults = useMemo(() => {
    const start = (currentPage - 1) * pageSize
    return searchResults.slice(start, start + pageSize)
  }, [currentPage, searchResults])

  function clearPendingSearchDebounce() {
    if (searchDebounceTimerRef.current !== null) {
      window.clearTimeout(searchDebounceTimerRef.current)
      searchDebounceTimerRef.current = null
    }
  }

  useEffect(() => {
    void fetchIndexHistory()
    void fetchRecentSearches()
    return () => {
      searchAbortRef.current?.abort()
      if (searchDebounceTimerRef.current !== null) window.clearTimeout(searchDebounceTimerRef.current)
      if (suggestDebounceTimerRef.current !== null) window.clearTimeout(suggestDebounceTimerRef.current)
      if (hideSuggestionBoxTimerRef.current !== null) window.clearTimeout(hideSuggestionBoxTimerRef.current)
    }
  }, [])

  async function fetchIndexHistory() {
    try {
      const { response, payload } = await indexApi.history(10)
      if (!response.ok || !isIndexHistoryPayload(payload)) return
      setIndexHistory(payload)
    } catch {
      /* leave stale context */
    }
  }

  const doSearch = useCallback(
    async (nextQuery: string) => {
      const cleanQuery = nextQuery.trim()
      const requestQuery = buildRequestQuery(cleanQuery, sortMode)

      searchAbortRef.current?.abort()
      searchAbortRef.current = null

      if (!requestQuery) {
        searchRequestIdRef.current += 1
        setSearchResults([])
        setCurrentPage(1)
        setActiveSearchQuery('')
        setOpenedResultPaths(new Set())
        setSearchMessage('')
        return
      }

      const requestId = ++searchRequestIdRef.current
      const controller = new AbortController()
      searchAbortRef.current = controller
      setSearchMessage('')
      setOpenEventMessage('')
      // Treat each executed search as a fresh result session.
      setOpenedResultPaths(new Set())

      try {
        const { response, payload } = await searchApi.query({
          q: requestQuery,
          limit,
          signal: controller.signal,
        })
        if (requestId !== searchRequestIdRef.current) return

        if (!response.ok) {
          const message =
            payload && typeof payload === 'object' && !Array.isArray(payload) && 'message' in payload
              ? String(payload.message || 'Search failed.')
              : 'Search failed.'
          setSearchMessage(message)
          setSearchResults([])
          return
        }

        const results = normalizeRankedResults(payload)
        setActiveSearchQuery(cleanQuery)
        setSearchResults(results)
        setCurrentPage(1)
        setSearchMessage(results.length === 0 ? 'No results.' : '')
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') return
        if (requestId !== searchRequestIdRef.current) return
        setSearchMessage('Failed to search. Ensure backend server is running.')
        setSearchResults([])
        setCurrentPage(1)
      }
    },
    [limit, sortMode],
  )

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(totalPages)
    }
  }, [currentPage, totalPages])

  useEffect(() => {
    if (searchDebounceTimerRef.current !== null) {
      window.clearTimeout(searchDebounceTimerRef.current)
      searchDebounceTimerRef.current = null
    }

    searchDebounceTimerRef.current = window.setTimeout(() => {
      void doSearch(query)
    }, 250)

    return () => {
      if (searchDebounceTimerRef.current !== null) {
        window.clearTimeout(searchDebounceTimerRef.current)
        searchDebounceTimerRef.current = null
      }
    }
  }, [doSearch, query])

  useEffect(() => {
    if (suggestDebounceTimerRef.current !== null) {
      window.clearTimeout(suggestDebounceTimerRef.current)
      suggestDebounceTimerRef.current = null
    }

    const trimmed = query.trim()
    if (!trimmed) {
      setSearchSuggestions([])
      return
    }

    suggestDebounceTimerRef.current = window.setTimeout(() => {
      void fetchSearchSuggestions(trimmed)
    }, 200)

    return () => {
      if (suggestDebounceTimerRef.current !== null) {
        window.clearTimeout(suggestDebounceTimerRef.current)
        suggestDebounceTimerRef.current = null
      }
    }
  }, [query])

  async function fetchSearchSuggestions(prefix: string) {
    const requestId = ++suggestRequestIdRef.current
    setIsLoadingSuggestions(true)

    try {
      const { response, payload } = await searchApi.suggest({ q: prefix, limit: 8 })
      if (requestId !== suggestRequestIdRef.current) return
      if (!response.ok || !isStringList(payload)) {
        setSearchSuggestions([])
        return
      }
      setSearchSuggestions(payload.map(stripSortFilter).filter((item) => item.length > 0))
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
      const { response, payload } = await searchApi.recent(8)
      if (!response.ok || !isStringList(payload)) {
        setRecentSearches([])
        return
      }
      setRecentSearches(payload.map(stripSortFilter).filter((item) => item.length > 0))
    } catch {
      setRecentSearches([])
    } finally {
      setIsLoadingRecent(false)
    }
  }

  function triggerSearchFromSelection(value: string) {
    const cleaned = stripSortFilter(value)
    clearPendingSearchDebounce()
    setQuery(cleaned)
    setShowSuggestionBox(false)
    void doSearch(cleaned)
  }

  function handleSearchInputFocus() {
    if (hideSuggestionBoxTimerRef.current !== null) {
      window.clearTimeout(hideSuggestionBoxTimerRef.current)
      hideSuggestionBoxTimerRef.current = null
    }
    setShowSuggestionBox(true)
    if (!query.trim()) {
      void fetchRecentSearches()
    }
  }

  function handleSearchInputBlur() {
    hideSuggestionBoxTimerRef.current = window.setTimeout(() => {
      setShowSuggestionBox(false)
    }, 150)
  }

  async function trackOpenEvent(filePath: string, resultPosition: number) {
    if (!activeSearchQuery.trim()) return

    try {
      const { response } = await searchApi.recordOpen({
        query: activeSearchQuery,
        filePath,
        resultPosition,
      })
      if (!response.ok) {
        setOpenEventMessage('Could not record open event.')
        return
      }

      setOpenedResultPaths((previous) => {
        const next = new Set(previous)
        next.add(filePath)
        return next
      })
      setOpenEventMessage('')
    } catch {
      setOpenEventMessage('Could not record open event.')
    }
  }

  function runSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    clearPendingSearchDebounce()
    if (!limitInput.trim()) {
      setLimitInput('20')
    }
    void doSearch(query)
  }

  return (
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
            Last index: {formatIsoDateTime(latestCompletedRun.finishedAt)} · {latestCompletedRun.indexed} files
            indexed · {formatElapsed(latestCompletedRun.elapsedSeconds)}
          </p>
        )}
      </div>

      <details className="search-query-help">
        <summary>Search syntax & filters</summary>
        <ul className="search-query-help-list">
          <li>
            <code>word</code> or <code>&quot;phrase&quot;</code> - full-text search in file names and content
            (combine with filters below).
          </li>
          <li>
            <code>name.ext</code> - if the whole query looks like a file name, it matches filename only.
          </li>
          <li>
            <code>path:A/B</code> - files in that path.
          </li>
          <li>
            <code>content:some text</code> - files containing the specified text.
          </li>
          <li>
            <code>ext:java</code> - files with that extension (also <code>extension:md</code>).
          </li>
          <li>
            <code>modified:2024-01-15</code> - modified after this date/time (use the same format as stored in DB,
            e.g. ISO-like <code>2024-01-15T10:00:00</code> if needed).
          </li>
          <li>
            <code>size:1048576</code> - file larger than this many <strong>bytes</strong> (default unit).
          </li>
          <li>
            <code>size:10kb</code>, <code>size:5mb</code>, <code>size:1gb</code> - size filter with units.
          </li>
          <li>
            Examples: <code>readme ext:md</code>, <code>config ext:json</code>, <code>todo size:500</code>.
          </li>
        </ul>
      </details>

      <form className="search-row" onSubmit={runSearch}>
        <div className="search-input-wrap">
          <label htmlFor="search-query" className="sr-only">
            Search query
          </label>
          <span className="search-input-icon" aria-hidden="true">
            #
          </span>
          <input
            id="search-query"
            className="search-query-input"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onFocus={handleSearchInputFocus}
            onBlur={handleSearchInputBlur}
            placeholder='Try: "readme", "ext:java", "config ext:json"'
          />
          <SuggestionBox
            suggestions={searchSuggestions}
            recents={recentSearches}
            visible={showSuggestionBox}
            isLoadingSuggestions={isLoadingSuggestions}
            isLoadingRecent={isLoadingRecent}
            hasQuery={query.trim().length > 0}
            onSelect={triggerSearchFromSelection}
          />
        </div>

        <div className="search-limit-wrap">
          <label htmlFor="search-limit" className="sr-only">
            Number of returned results
          </label>
          <input
            id="search-limit"
            className="search-limit-input"
            type="text"
            inputMode="numeric"
            value={limitInput}
            onChange={(event) => {
              const raw = event.target.value.replace(/[^\d]/g, '')
              setLimitInput(raw)
            }}
            onBlur={() => {
              if (!limitInput.trim()) {
                setLimitInput('20')
              }
            }}
            placeholder="20"
            aria-label="Number of returned results"
          />
        </div>

        <div className="sort-selector">
          <label htmlFor="search-sort" className="sr-only">
            Ranking strategy
          </label>
          <select id="search-sort" value={sortMode} onChange={(event) => setSortMode(event.target.value as SortMode)}>
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
        {pagedResults.map((item, index) => (
          <ResultCard
            key={item.result.path}
            item={item}
            index={(currentPage - 1) * pageSize + index}
            activeQuery={activeSearchQuery}
            sortMode={sortMode}
            isOpened={openedResultPaths.has(item.result.path)}
            onMarkOpened={(path, position) => void trackOpenEvent(path, position)}
          />
        ))}
      </div>

      {searchResults.length > pageSize && (
        <div className="pagination" aria-label="Search results pages">
          <button type="button" onClick={() => setCurrentPage((page) => Math.max(1, page - 1))} disabled={currentPage === 1}>
            Previous
          </button>
          <span className="pagination-status">
            Page {currentPage} of {totalPages}
          </span>
          <button
            type="button"
            onClick={() => setCurrentPage((page) => Math.min(totalPages, page + 1))}
            disabled={currentPage === totalPages}
          >
            Next
          </button>
        </div>
      )}
    </section>
  )
}
