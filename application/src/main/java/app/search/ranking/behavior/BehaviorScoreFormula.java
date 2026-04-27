package app.search.ranking.behavior;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * Computes a personalized behavior score for a candidate file given the
 * user's interaction history with the current normalized query.
 * <p>
 * The {@link app.db.sqlite.SqliteBehaviorScoreFunction} class is a thin SQLite
 * adapter that delegates to {@link #compute}.
 *
 * <h2>Score formula</h2>
 * Each component lies in [0, 1]:
 * <ul>
 *   <li><b>Frequency</b>: log-scaled open count, capped at {@link #FREQ_CAP}.</li>
 *   <li><b>Recency</b>: exponential decay with half-life {@link #RECENCY_HALF_LIFE_DAYS}.</li>
 *   <li><b>Position lift</b>: how often the user searched past higher-ranked
 *       results to reach this file, scaled against {@link #POSITION_CAP}.</li>
 * </ul>
 * Weights are tuned so frequency and recency dominate; position lift is the
 * noisiest signal (depends on whichever ranking was active when the open
 * happened) and contributes least.
 */
public final class BehaviorScoreFormula {

    static final double WEIGHT_FREQUENCY = 0.45;
    static final double WEIGHT_RECENCY   = 0.40;
    static final double WEIGHT_POSITION  = 0.15;

    /** Open count at which the frequency component saturates to 1.0. */
    static final int FREQ_CAP = 50;

    /** Half-life for recency decay; opens older than this lose half their weight. */
    static final double RECENCY_HALF_LIFE_DAYS = 7.0;

    /** Average-position-minus-one at which the position component saturates to 1.0. */
    static final int POSITION_CAP = 20;

    private BehaviorScoreFormula() {
    }

    /**
     * Pure computation entry point. Used by both the SQLite UDF adapter
     * and unit tests; takes a {@code now} parameter so tests can pin the
     * recency reference time and avoid wall-clock flakiness.
     *
     * @param openCount      total opens of this file for the current normalized query (>= 0)
     * @param lastOpenedAt   ISO-8601 LocalDateTime string, or null if never opened
     * @param positionSum    sum of {@code result_position} values across all opens (>= 0)
     * @param positionCount  count of opens that had a non-null {@code result_position} (>= 0)
     * @param now            reference time for recency decay
     * @return score in [0, 1]; 0.0 if the file has no history signal at all
     */
    public static double compute(
            int openCount,
            String lastOpenedAt,
            long positionSum,
            int positionCount,
            LocalDateTime now
    ) {
        if (openCount <= 0) {
            return 0.0;
        }

        double frequency = frequencyComponent(openCount);
        double recency   = recencyComponent(lastOpenedAt, now);
        double position  = positionComponent(positionSum, positionCount);

        double total = WEIGHT_FREQUENCY * frequency
                + WEIGHT_RECENCY   * recency
                + WEIGHT_POSITION  * position;
        return clamp01(total);
    }

    static double frequencyComponent(int openCount) {
        if (openCount <= 0) return 0.0;
        double scaled = Math.log1p(openCount) / Math.log1p(FREQ_CAP);
        return clamp01(scaled);
    }

    static double recencyComponent(String lastOpenedAt, LocalDateTime now) {
        if (lastOpenedAt == null || lastOpenedAt.isBlank()) return 0.0;
        LocalDateTime opened;
        try {
            opened = LocalDateTime.parse(lastOpenedAt);
        } catch (DateTimeParseException e) {
            return 0.0;
        }
        // file opens recorded in the future (clock skew) are clamped to "now" — score 1.0, not > 1.0.
        if (opened.isAfter(now)) {
            return 1.0;
        }
        double ageDays = Duration.between(opened, now).toMillis() / (1000.0 * 60 * 60 * 24);
        // exp(-ln(2) * age / halfLife) gives proper half-life behavior:
        // age == halfLife → 0.5, age == 2*halfLife → 0.25, etc.
        return Math.exp(-Math.log(2.0) * ageDays / RECENCY_HALF_LIFE_DAYS);
    }

    static double positionComponent(long positionSum, int positionCount) {
        if (positionCount <= 0) return 0.0;
        double avgPosition = (double) positionSum / positionCount;
        // a file always opened at position 1 has no "lift" — it was ranked correctly every time.
        double liftRaw = avgPosition - 1.0;
        if (liftRaw <= 0.0) return 0.0;
        double scaled = Math.log1p(liftRaw) / Math.log1p(POSITION_CAP);
        return clamp01(scaled);
    }

    private static double clamp01(double value) {
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }
}