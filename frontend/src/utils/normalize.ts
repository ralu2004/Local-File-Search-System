import type { RankedSearchResult, SearchResult, SortMode } from '../types'

function isModifiedAt(value: unknown): value is string | number[] {
  return typeof value === 'string' || Array.isArray(value)
}

function isSearchResult(value: unknown): value is SearchResult {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return (
    typeof candidate.path === 'string' &&
    typeof candidate.filename === 'string' &&
    typeof candidate.extension === 'string' &&
    (typeof candidate.preview === 'string' || candidate.preview === null) &&
    isModifiedAt(candidate.modifiedAt)
  )
}

export function buildRequestQuery(rawQuery: string, sortMode: SortMode): string {
  const base = rawQuery.replace(/\bsort:[^\s]+/gi, '').replace(/\s+/g, ' ').trim()
  if (!base) return ''
  if (sortMode === 'default') return base
  return `${base} sort:${sortMode}`
}

export function stripSortFilter(rawQuery: string): string {
  return rawQuery.replace(/\bsort:[^\s]+/gi, '').replace(/\s+/g, ' ').trim()
}

export function normalizeRankedResults(payload: unknown): RankedSearchResult[] {
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
