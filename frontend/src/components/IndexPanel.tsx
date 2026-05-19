import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'

import { indexApi } from '../api/client'
import type { IndexRunRow, IndexStatus, IndexingSnapshot, MessageResponse } from '../types'
import { formatElapsed, formatIsoDateTime, phaseLabel } from '../utils/format'
import StatusBadge from './StatusBadge'

export default function IndexPanel() {
  const [root, setRoot] = useState('')
  const [ignoreRules, setIgnoreRules] = useState('*.log')
  const [maxFileSizeMb, setMaxFileSizeMb] = useState(10)
  const [previewLines, setPreviewLines] = useState(3)
  const [batchSize, setBatchSize] = useState(250)
  const [indexHistory, setIndexHistory] = useState<IndexRunRow[]>([])
  const [indexing, setIndexing] = useState<IndexingSnapshot | null>(null)
  const [indexMessage, setIndexMessage] = useState('')
  const indexStatusRef = useRef<IndexStatus | null>(null)

  const running = indexing?.status === 'RUNNING'
  const latestCompletedRun = useMemo(
    () => indexHistory.find((run) => run.finishedAt) ?? indexHistory[0] ?? null,
    [indexHistory],
  )

  const fetchIndexHistory = useCallback(async () => {
    try {
      const { response, payload } = await indexApi.history(10)
      if (!response.ok || !Array.isArray(payload)) return
      setIndexHistory(payload)
    } catch {
      /* keep previous history */
    }
  }, [])

  const fetchStatus = useCallback(async () => {
    try {
      const { response, payload } = await indexApi.status()
      if (!response.ok || Array.isArray(payload) || typeof payload !== 'object' || payload === null) {
        setIndexMessage((payload as MessageResponse).message || 'Failed to fetch indexing status.')
        return
      }

      const snapshot = payload as IndexingSnapshot
      const previous = indexStatusRef.current
      indexStatusRef.current = snapshot.status
      setIndexing(snapshot)

      if ((snapshot.status === 'COMPLETED' || snapshot.status === 'FAILED') && previous === 'RUNNING') {
        void fetchIndexHistory()
      }
    } catch {
      setIndexMessage('Cannot reach backend at http://localhost:7070. Start server mode first.')
    }
  }, [fetchIndexHistory])

  useEffect(() => {
    void fetchIndexHistory()
  }, [fetchIndexHistory])

  useEffect(() => {
    void fetchStatus()
    const intervalMs = running ? 600 : 2200
    const timer = window.setInterval(() => {
      void fetchStatus()
    }, intervalMs)
    return () => window.clearInterval(timer)
  }, [fetchStatus, running])

  async function startIndexing(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIndexMessage('')

    const rules = ignoreRules
      .split(',')
      .map((rule) => rule.trim())
      .filter((rule) => rule.length > 0)

    try {
      const { payload } = await indexApi.start({
        root,
        dbPath: '',
        ignoreRules: rules,
        maxFileSizeMb,
        previewLines,
        batchSize,
      })
      setIndexMessage(payload.message)
      await fetchStatus()
    } catch {
      setIndexMessage('Failed to start indexing. Ensure backend server is running.')
    }
  }

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Indexing</h2>
        <StatusBadge status={indexing?.status ?? null} />
      </div>

      <form className="form-grid" onSubmit={startIndexing}>
        <label htmlFor="index-root">
          Root path
          <input id="index-root" 
            value={root} 
            onChange={(event) => setRoot(event.target.value)} 
            placeholder="e.g. /home/user/documents or C:\Users\user\Documents"
          />
        </label>
        <label htmlFor="index-ignore-rules">
          Ignore rules (comma-separated)
          <input
            id="index-ignore-rules"
            value={ignoreRules}
            onChange={(event) => setIgnoreRules(event.target.value)}
            placeholder="*.log, node_modules"
          />
        </label>
        <label htmlFor="index-max-file-size">
          Max file size (MB)
          <input
            id="index-max-file-size"
            type="number"
            min={1}
            value={maxFileSizeMb}
            onChange={(event) => setMaxFileSizeMb(Number(event.target.value))}
          />
        </label>
        <label htmlFor="index-preview-lines">
          Preview lines
          <input
            id="index-preview-lines"
            type="number"
            min={1}
            value={previewLines}
            onChange={(event) => setPreviewLines(Number(event.target.value))}
          />
        </label>
        <label htmlFor="index-batch-size">
          Batch size
          <input
            id="index-batch-size"
            type="number"
            min={1}
            value={batchSize}
            onChange={(event) => setBatchSize(Number(event.target.value))}
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
              Elapsed so far - {formatElapsed((Date.now() - new Date(indexing.startedAt).getTime()) / 1000)}
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
  )
}
