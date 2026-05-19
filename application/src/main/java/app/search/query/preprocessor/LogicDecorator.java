package app.search.query.preprocessor;

import app.search.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes the free-text part of a query into a valid FTS5 expression.
 * <p>
 * Handles quote normalization, FTS operator passthrough, special character
 * quoting, and prefix wildcard expansion on the last eligible token.
 * Replaces the inline FTS normalization previously done in QueryBuilder.
 */
public class LogicDecorator implements QueryDecorator {

    private record Part(String text, boolean isQuoteMarker, boolean inQuotes) {}

    private final QueryDecorator next;

    /**
     * @param next the next decorator in the chain
     */
    public LogicDecorator(QueryDecorator next) {
        this.next = next;
    }

    @Override
    public Query decorate(Query query) {
        if (query.value() == null || query.value().isBlank()) {
            return next.decorate(query);
        }
        String normalized = toFtsExpression(query.value());
        return next.decorate(new Query(query.type(), normalized, query.filters()));
    }

    private String toFtsExpression(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        List<Part> parts = splitIntoParts(trimmed);
        int lastPrefixCandidateIndex = findLastPrefixCandidateIndex(parts);
        return rebuildFtsQuery(parts, lastPrefixCandidateIndex);
    }

    private List<Part> splitIntoParts(String query) {
        List<Part> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '"') {
                flushToken(parts, token, false);
                inQuotes = !inQuotes;
                parts.add(new Part("\"", true, false));
                continue;
            }
            if (!inQuotes && Character.isWhitespace(c)) {
                flushToken(parts, token, false);
                continue;
            }
            token.append(c);
        }
        flushToken(parts, token, inQuotes);
        return parts;
    }

    private void flushToken(List<Part> parts, StringBuilder token, boolean inQuotes) {
        if (token.length() == 0) {
            return;
        }
        parts.add(new Part(token.toString(), false, inQuotes));
        token.setLength(0);
    }

    private int findLastPrefixCandidateIndex(List<Part> parts) {
        int index = -1;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.isQuoteMarker || part.inQuotes) {
                continue;
            }
            if (isPrefixCandidate(part.text)) {
                index = i;
            }
        }
        return index;
    }

    private String rebuildFtsQuery(List<Part> parts, int lastPrefixCandidateIndex) {
        StringBuilder rebuilt = new StringBuilder();
        boolean openQuoteJustAppended = false;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.isQuoteMarker) {
                openQuoteJustAppended = appendQuoteMarker(rebuilt, openQuoteJustAppended);
                continue;
            }
            String transformed = transformToken(part.text, part.inQuotes, i == lastPrefixCandidateIndex);
            if (shouldInsertTokenSeparator(rebuilt)) {
                rebuilt.append(' ');
            }
            rebuilt.append(transformed);
        }
        return rebuilt.toString().trim();
    }

    private boolean appendQuoteMarker(StringBuilder rebuilt, boolean openQuoteJustAppended) {
        if (openQuoteJustAppended) {
            rebuilt.append("\"");
            return false;
        }
        if (!rebuilt.isEmpty() && rebuilt.charAt(rebuilt.length() - 1) != ' ') {
            rebuilt.append(' ');
        }
        rebuilt.append("\"");
        return true;
    }

    private boolean shouldInsertTokenSeparator(StringBuilder rebuilt) {
        if (rebuilt.isEmpty()) {
            return false;
        }
        char last = rebuilt.charAt(rebuilt.length() - 1);
        return last != '"' && last != ' ';
    }

    private boolean isPrefixCandidate(String rawToken) {
        if (rawToken == null) {
            return false;
        }
        String token = rawToken.trim();
        if (token.isEmpty() || token.endsWith("*")) {
            return false;
        }
        String upper = token.toUpperCase();
        if (upper.equals("AND") || upper.equals("OR")
                || upper.equals("NOT") || upper.equals("NEAR")) {
            return false;
        }
        return token.matches("[A-Za-z0-9_]+");
    }

    private String transformToken(String rawToken, boolean inQuotes, boolean shouldPrefix) {
        if (rawToken == null) {
            return "";
        }
        String token = rawToken.trim();
        if (token.isEmpty()) {
            return "";
        }
        if (inQuotes) {
            return token.replace("\"", "\"\"");
        }
        String upper = token.toUpperCase();
        if (upper.equals("AND") || upper.equals("OR")
                || upper.equals("NOT") || upper.equals("NEAR")) {
            return upper;
        }
        if (token.endsWith("*")) {
            return token;
        }
        if (token.matches("[A-Za-z0-9_]+")) {
            return shouldPrefix ? token + "*" : token;
        }
        return "\"" + token.replace("\"", "\"\"") + "\"";
    }
}