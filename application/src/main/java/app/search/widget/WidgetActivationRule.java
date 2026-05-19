package app.search.widget;

import app.model.RankedSearchResult;

import java.util.List;
import java.util.Optional;

/**
 * Strategy for deciding whether a single context-aware widget should be
 * shown for the current search context.
 * <p>
 * Each implementation encapsulates one activation condition. The {@link WidgetActivator}
 * holds a registry of rules and collects those that return a non-empty {@link Optional}.
 * <p>
 * Implementations must be stateless and thread-safe; the same instance may be called concurrently.
 */
public interface WidgetActivationRule {

    /**
     * Evaluates this rule against the current query and result set.
     *
     * @param query   the raw user query string
     * @param results the current list of ranked search results (never null)
     * @return a non-empty {@link Optional} containing the widget to activate,
     *         or {@link Optional#empty()} if this rule does not apply
     */
    Optional<Widget> evaluate(String query, List<RankedSearchResult> results);
}
