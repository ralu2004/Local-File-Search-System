import { Fragment, createElement, type ReactNode } from 'react'

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function buildHighlightTerms(raw: string): string[] {
  const trimmed = raw.trim()
  if (!trimmed) return []

  const fromFilters: string[] = []
  const metaRe = /([a-zA-Z0-9_.-]+):([a-zA-Z0-9_.-]+)/g
  let match: RegExpExecArray | null
  while ((match = metaRe.exec(trimmed)) !== null) {
    const key = match[1].toLowerCase()
    if ((key === 'ext' || key === 'extension') && match[2]) fromFilters.push(match[2])
    if (key === 'content' && match[2]) fromFilters.push(match[2])
    if (key === 'path' && match[2]) fromFilters.push(match[2])
  }

  let rest = trimmed.replace(/[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+/g, ' ')
  rest = rest.replace(/\s+/g, ' ').trim()

  const textTerms: string[] = []
  const tokenRe = /"([^"]*)"|'([^']*)'|(\S+)/g
  while ((match = tokenRe.exec(rest)) !== null) {
    const chunk = (match[1] ?? match[2] ?? match[3] ?? '').trim()
    if (!chunk) continue
    textTerms.push(chunk)
    for (const word of chunk.split(/\s+/)) {
      const stripped = word.replace(/^[^a-zA-Z0-9._-]+|[^a-zA-Z0-9._-]+$/g, '')
      if (stripped.length >= 2) textTerms.push(stripped)
    }
  }

  const combined = [...fromFilters, ...textTerms]
  const seen = new Set<string>()
  const unique: string[] = []
  for (const term of combined.sort((a, b) => b.length - a.length)) {
    const key = term.toLowerCase()
    if (key.length < 2) continue
    if (seen.has(key)) continue
    seen.add(key)
    unique.push(term)
  }
  return unique
}

export function highlightText(text: string, terms: string[], keyPrefix: string): ReactNode {
  if (!text || terms.length === 0) return text

  const pattern = terms
    .map((term) => escapeRegExp(term))
    .filter((part) => part.length > 0)
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
      createElement(
        'mark',
        { key: `${keyPrefix}-hit-${hit++}`, className: 'search-hit' },
        match[0],
      ),
    )
    last = match.index + match[0].length
    if (match[0].length === 0) matcher.lastIndex += 1
  }

  if (last < text.length) out.push(text.slice(last))
  return out.length ? createElement(Fragment, null, ...out) : text
}
