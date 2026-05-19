package app.search.widget;

import app.model.RankedSearchResult;
import app.search.widget.WidgetActivationRules.AlwaysPresentRule;
import app.search.widget.WidgetActivationRules.ContentQualifierRule;
import app.search.widget.WidgetActivationRules.MajorityDiffRule;
import app.search.widget.WidgetActivationRules.MajorityImageRule;
import app.search.widget.WidgetActivationRules.MajorityLogRule;
import app.search.widget.WidgetActivationRules.MajorityMarkdownRule;
import app.search.widget.WidgetActivationRules.SameDirectoryRule;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory that produces the list of context-aware widgets relevant for a
 * given search result set.
 * <p>
 * The factory holds an ordered registry of {@link WidgetActivationRule}
 * strategies. For each search invocation it iterates the rules and collects
 * those that return a non-empty result. Rules are independent of one another
 * and evaluated in registration order, so widget strip ordering is predictable.
 * <p>
 * New widget types are added by:
 * <ol>
 *   <li>Implementing {@link WidgetActivationRule} (in {@link WidgetActivationRules}).</li>
 *   <li>Registering an instance in {@link #defaultRules()}.</li>
 * </ol>
 * No existing rule or caller needs to change.
 */
public class WidgetActivator {

    private final List<WidgetActivationRule> rules;

    /**
     * Creates an activator with a custom rule set. Useful for testing and
     * for constructing activators with a subset or superset of rules.
     *
     * @param rules the ordered list of activation rules to evaluate
     */
    public WidgetActivator(List<WidgetActivationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Creates an activator with the standard production rule set.
     * Equivalent to {@code new WidgetActivator(WidgetActivator.defaultRules())}.
     */
    public static WidgetActivator withDefaultRules() {
        return new WidgetActivator(defaultRules());
    }

    /**
     * Returns the default ordered list of production rules.
     * <p>
     * {@link AlwaysPresentRule} is registered last so the export widget
     * always appears at the end of the widget strip.
     */
    public static List<WidgetActivationRule> defaultRules() {
        List<WidgetActivationRule> rules = new ArrayList<>();
        rules.add(new MajorityImageRule());
        rules.add(new MajorityLogRule());
        rules.add(new MajorityMarkdownRule());
        rules.add(new MajorityDiffRule());
        rules.add(new SameDirectoryRule());
        rules.add(new ContentQualifierRule());
        rules.add(new AlwaysPresentRule());
        return rules;
    }

    /**
     * Evaluates all registered rules and returns the widgets whose conditions
     * are met for the current query and result set.
     *
     * @param query   the raw user query
     * @param results the current search result set
     * @return list of activated widgets, empty if results are null or empty
     */
    public List<Widget> activate(String query, List<RankedSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<Widget> widgets = new ArrayList<>();
        for (WidgetActivationRule rule : rules) {
            rule.evaluate(query, results).ifPresent(widgets::add);
        }
        return widgets;
    }

}