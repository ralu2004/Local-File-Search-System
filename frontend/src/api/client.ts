import type {
  SearchResponse,
  IndexRunRow,
  IndexingSnapshot,
  MessageResponse,
  OptionalMessageResponse,
  RankedSearchResult,
} from '../types'

const API_BASE = 'http://localhost:7070/api'

export type ApiResult<T> = {
  response: Response
  payload: T
}

type JsonValue = string[] | SearchResponse | RankedSearchResult[] | IndexRunRow[] | IndexingSnapshot | MessageResponse | OptionalMessageResponse

async function requestJson<T extends JsonValue>(path: string, init?: RequestInit): Promise<ApiResult<T>> {
  const response = await fetch(`${API_BASE}${path}`, init)
  const payload = (await response.json().catch(() => ({}))) as T
  return { response, payload }
}

export const indexApi = {
  start(input: {
    root: string
    dbPath: string
    ignoreRules: string[]
    maxFileSizeMb: number
    previewLines: number
    batchSize: number
  }) {
    return requestJson<MessageResponse>('/index/start', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  },

  status() {
    return requestJson<IndexingSnapshot | MessageResponse>('/index/status')
  },

  history(limit = 10) {
    const params = new URLSearchParams({ limit: String(limit) })
    return requestJson<IndexRunRow[] | MessageResponse>(`/index/history?${params.toString()}`)
  },
}

export const searchApi = {
  query(input: { q: string; limit: number; signal?: AbortSignal }) {
    const params = new URLSearchParams({ q: input.q, limit: String(input.limit) })
    return requestJson<SearchResponse | MessageResponse>(`/search?${params.toString()}`, {
      signal: input.signal,
    })
  },

  suggest(input: { q: string; limit: number }) {
    const params = new URLSearchParams({ q: input.q, limit: String(input.limit) })
    return requestJson<string[] | OptionalMessageResponse>(`/search/suggest?${params.toString()}`)
  },

  recent(limit = 8) {
    const params = new URLSearchParams({ limit: String(limit) })
    return requestJson<string[] | OptionalMessageResponse>(`/search/history?${params.toString()}`)
  },

  recordOpen(input: { query: string; filePath: string; resultPosition: number }) {
    return requestJson<OptionalMessageResponse>('/search/open', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    })
  },
}
