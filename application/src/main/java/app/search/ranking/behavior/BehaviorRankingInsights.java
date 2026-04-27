package app.search.ranking.behavior;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Formats user-facing explanations for behavior-based ranking.
 */
public final class BehaviorRankingInsights {

    private BehaviorRankingInsights() {
    }

    public static List<String> describe(int openCount, String lastOpenedAt, long positionSum, int positionCount, LocalDateTime now) {
        List<String> insights = new ArrayList<>();
        if (openCount <= 0) {
            return List.of();
        }

        insights.add("You've opened this " + openCount + " times for similar searches");

        String recency = toRelativeTime(lastOpenedAt, now);
        if (recency != null) {
            insights.add("Last opened " + recency);
        }

        if (positionCount >= 3) {
            double avgPosition = (double) positionSum / positionCount;
            if (avgPosition >= 4.0) {
                insights.add("You often select this from deeper in results");
            }
        }

        return List.copyOf(insights);
    }

    private static String toRelativeTime(String lastOpenedAt, LocalDateTime now) {
        if (lastOpenedAt == null || lastOpenedAt.isBlank()) {
            return null;
        }
        LocalDateTime openedAt;
        try {
            openedAt = LocalDateTime.parse(lastOpenedAt);
        } catch (DateTimeParseException e) {
            return null;
        }
        if (openedAt.isAfter(now)) {
            return "just now";
        }
        Duration age = Duration.between(openedAt, now);
        long minutes = age.toMinutes();
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
        long hours = age.toHours();
        if (hours < 24) return hours == 1 ? "1 hour ago" : hours + " hours ago";
        if (hours < 48) return "yesterday";
        long days = age.toDays();
        if (days < 14) return days + " days ago";
        if (days < 60) {
            long weeks = Math.max(1, days / 7);
            return weeks == 1 ? "1 week ago" : weeks + " weeks ago";
        }
        return "on " + openedAt.toLocalDate();
    }
}
