import type { IndexStatus } from '../types'

type Props = {
  status: IndexStatus | null
}

export default function StatusBadge({ status }: Props) {
  const value = status ?? 'IDLE'
  const tone =
    value === 'RUNNING'
      ? 'running'
      : value === 'COMPLETED'
        ? 'completed'
        : value === 'FAILED'
          ? 'failed'
          : 'idle'

  return <span className={`status-badge ${tone}`}>{value}</span>
}
