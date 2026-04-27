package app.search.ranking.behavior;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorRankingInsightsTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-04-27T12:00:00");

    @Test
    void describe_noOpens_returnsEmpty() {
        assertEquals(List.of(), BehaviorRankingInsights.describe(0, null, 0L, 0, NOW));
    }

    @Test
    void describe_addsOpenCountAndRecency() {
        List<String> insights = BehaviorRankingInsights.describe(
                5,
                NOW.minusHours(3).toString(),
                5L,
                5,
                NOW
        );

        assertTrue(insights.contains("You've opened this 5 times for similar searches"));
        assertTrue(insights.contains("Last opened 3 hours ago"));
    }

    @Test
    void describe_omitsRecencyForMalformedTimestamp() {
        List<String> insights = BehaviorRankingInsights.describe(2, "invalid-date", 4L, 2, NOW);

        assertEquals(1, insights.size());
        assertEquals("You've opened this 2 times for similar searches", insights.getFirst());
    }

    @Test
    void describe_addsDepthNarrativeOnlyWhenSignalStrong() {
        List<String> strong = BehaviorRankingInsights.describe(6, NOW.minusDays(2).toString(), 18L, 4, NOW);
        List<String> weak = BehaviorRankingInsights.describe(6, NOW.minusDays(2).toString(), 6L, 3, NOW);

        assertTrue(strong.contains("You often select this from deeper in results"));
        assertFalse(weak.contains("You often select this from deeper in results"));
    }

    @Test
    void describe_formatsOlderEventsAsDate() {
        List<String> insights = BehaviorRankingInsights.describe(
                3,
                NOW.minusDays(90).toString(),
                12L,
                3,
                NOW
        );

        assertTrue(
                insights.stream().anyMatch(v -> v.startsWith("Last opened on ")),
                "Expected date-form recency for older events"
        );
    }
}
