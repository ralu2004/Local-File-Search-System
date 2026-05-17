export type IndexStatus = 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export type IndexReport = {
  totalFiles: number
  indexed: number
  skipped: number
  failed: number
  deleted: number
  elapsed: string
}

export type IndexingLiveProgress = {
  totalFiles: number
  indexed: number
  skipped: number
  failed: number
  pendingInBatch: number
  phase: string
}

export type IndexingSnapshot = {
  status: IndexStatus
  startedAt: string | null
  finishedAt: string | null
  lastReport: IndexReport | null
  lastError: string | null
  liveProgress: IndexingLiveProgress | null
}

export type IndexRunRow = {
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

export type SearchResult = {
  path: string
  filename: string
  extension: string
  preview: string
  modifiedAt: string | number[]
  sizeBytes?: number | null
}

export type RankedSearchResult = {
  result: SearchResult
  insights?: string[]
}

export type Widget = {
  id: string
  label: string
  type: 'action' | 'marker'
}

export type SearchResponse = {
  results: RankedSearchResult[]
  widgets: Widget[]
}

export type SortMode = 'default' | 'balanced' | 'date' | 'alpha' | 'behavior'

export type MessageResponse = {
  message: string
}

export type OptionalMessageResponse = {
  message?: string
}
