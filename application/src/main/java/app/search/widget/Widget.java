package app.search.widget;

/**
 * Represents a context-aware widget activated based on search result analysis.
 *
 * @param id    unique widget identifier (e.g. "gallery", "analyze-logs")
 * @param label human-readable label shown in the UI
 * @param type  either "action" (clickable) or "marker" (informational)
 */
public record Widget(String id, String label, String type) {}
