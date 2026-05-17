export function getFileTypeLabel(extension: string): string {
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
    pdf: 'PDF Document',
  }

  return knownTypes[ext] ?? `${ext.toUpperCase()} File`
}

export function getFolderPath(pathValue: string): string {
  if (!pathValue) return ''
  const withoutScheme = pathValue.replace(/^file:\/+/, '')
  const normalized = withoutScheme.replace(/\\/g, '/')
  const lastSlash = normalized.lastIndexOf('/')
  return lastSlash > 0 ? normalized.slice(0, lastSlash) : withoutScheme
}

export function formatModifiedAt(value: string | number[] | null | undefined): string {
  if (value === null || value === undefined) return 'Unknown date'

  if (Array.isArray(value)) {
    if (value.length >= 6) {
      const [year, month, day, hour, minute, second, nano = 0] = value
      const ms = Math.floor(nano / 1_000_000)
      const fromParts = new Date(year, month - 1, day, hour, minute, second, ms)
      if (!Number.isNaN(fromParts.getTime())) {
        return fromParts.toLocaleString()
      }
    }
    return value.join(',')
  }

  const trimmed = value.trim()
  if (!trimmed) return 'Unknown date'

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

  return value
}

export function formatFileSize(bytes: unknown): string {
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

export function phaseLabel(phase: string | undefined): string {
  const p = (phase ?? 'crawling').toLowerCase()
  if (p === 'finalizing') return 'Finalizing index (writes, cleanup, optional optimize)…'
  return 'Scanning files…'
}

export function formatIsoDateTime(iso: string | null | undefined): string {
  if (!iso?.trim()) return '—'
  const d = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString()
}

export function formatElapsed(value: unknown): string {
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
