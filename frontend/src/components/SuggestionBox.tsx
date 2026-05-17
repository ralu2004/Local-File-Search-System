type Props = {
  suggestions: string[]
  recents: string[]
  visible: boolean
  isLoadingSuggestions: boolean
  isLoadingRecent: boolean
  hasQuery: boolean
  onSelect: (value: string) => void
}

export default function SuggestionBox({
  suggestions,
  recents,
  visible,
  isLoadingSuggestions,
  isLoadingRecent,
  hasQuery,
  onSelect,
}: Props) {
  if (!visible) return null

  return (
    <div className="suggestion-box" role="listbox" aria-label="Search suggestions and history">
      {hasQuery ? (
        <>
          <p className="suggestion-heading">Suggestions</p>
          {isLoadingSuggestions ? (
            <p className="suggestion-empty">Loading suggestions...</p>
          ) : suggestions.length > 0 ? (
            <ul className="suggestion-list">
              {suggestions.map((suggestion) => (
                <li key={`s-${suggestion}`}>
                  <button
                    type="button"
                    className="suggestion-item"
                    onMouseDown={(event) => {
                      event.preventDefault()
                      onSelect(suggestion)
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
          ) : recents.length > 0 ? (
            <div className="recent-chip-list">
              {recents.map((recent) => (
                <button
                  key={`r-${recent}`}
                  type="button"
                  className="recent-chip"
                  onMouseDown={(event) => {
                    event.preventDefault()
                    onSelect(recent)
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
  )
}
