import { useMemo } from 'react'

import type { RankedSearchResult, SortMode } from '../types'
import { buildHighlightTerms, highlightText } from '../utils/highlight'
import { formatFileSize, formatModifiedAt, getFileTypeLabel, getFolderPath } from '../utils/format'

type Props = {
  item: RankedSearchResult
  index: number
  activeQuery: string
  sortMode: SortMode
  isOpened: boolean
  onMarkOpened: (path: string, position: number) => void
}

export default function ResultCard({ item, index, activeQuery, sortMode, isOpened, onMarkOpened }: Props) {
  const result = item.result
  const insights = Array.isArray(item.insights) ? item.insights : []
  const showInsights = sortMode === 'behavior' && insights.length > 0
  const highlightTerms = useMemo(() => buildHighlightTerms(activeQuery), [activeQuery])

  return (
    <article className="result-card">
      <div className="result-header">
        <h3>
          {highlightTerms.length > 0
            ? highlightText(result.filename, highlightTerms, `${result.path}-fn`)
            : result.filename}
        </h3>
        <button
          type="button"
          className={isOpened ? 'result-open-button opened' : 'result-open-button'}
          aria-pressed={isOpened}
          onClick={() => onMarkOpened(result.path, index + 1)}
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
}
