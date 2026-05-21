package app.search.query.preprocessor;

import app.search.query.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Expands shorthand terms in the free-text part of the query using a configurable synonym map.
 * For example, "img" may expand to "img OR image OR photo".
 * Filter values (e.g. ext:img) are left untouched since they live in query.filters().
 */
public class SynonymDecorator implements QueryDecorator {

    private static final Logger log = LoggerFactory.getLogger(SynonymDecorator.class);
    private final QueryDecorator next;
    private final Map<String, List<String>> synonyms;
    private static final Map<String, List<String>> DEFAULT_SYNONYMS = loadSynonyms();

    public SynonymDecorator(QueryDecorator next) {
        this(next, DEFAULT_SYNONYMS);
    }

    /**
     * @param next     the next decorator in the chain
     * @param synonyms map of term to its expansions
     */
    public SynonymDecorator(QueryDecorator next, Map<String, List<String>> synonyms) {
        this.next = next;
        this.synonyms = synonyms;
    }

    @Override
    public Query decorate(Query query) {
        if (query.value() == null || query.value().isBlank()) {
            return next.decorate(query);
        }
        String[] tokens = query.value().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            if (synonyms.containsKey(token.toLowerCase())) {
                List<String> expanded = synonyms.get(token.toLowerCase());
                result.append("(").append(String.join(" OR ", expanded)).append(")");
            } else {
                result.append(token);
            }
        }
        return next.decorate(new Query(query.type(), result.toString(), query.filters()));
    }

    private static Map<String, List<String>> loadSynonyms() {
        Properties props = new Properties();
        try (InputStream in = SynonymDecorator.class.getResourceAsStream("/synonyms.properties")) {
            if (in == null) {
                log.warn("synonyms.properties not found, synonym expansion disabled");
                return Map.of();
            }
            props.load(in);
        } catch (IOException e) {
            log.warn("Failed to load synonyms.properties: {}", e.getMessage());
            return Map.of();
        }
        Map<String, List<String>> synonyms = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            List<String> values = Arrays.stream(props.getProperty(key).split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            synonyms.put(key.trim(), values);
        }
        return synonyms;
    }
}