package app.search.ranking.behavior;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BehaviorScoreFormula#compute}.
 */
class BehaviorScoreFormulaTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-04-27T12:00:00");
    private static final double EPS = 1e-9;

    @Test
    void compute_noOpens_returnsZero() {
        double score = BehaviorScoreFormula.compute(0, null, 0L, 0, NOW);
        assertEquals(0.0, score, EPS);
    }

    @Test
    void compute_negativeOpenCount_returnsZero() {
        // Defensive: SQL aggregates should never produce this, but guard anyway.
        double score = BehaviorScoreFormula.compute(-3, NOW.toString(), 0L, 0, NOW);
        assertEquals(0.0, score, EPS);
    }

    @Test
    void frequencyComponent_zeroOpens_isZero() {
        assertEquals(0.0, BehaviorScoreFormula.frequencyComponent(0), EPS);
    }

    @Test
    void frequencyComponent_oneOpen_isPositiveButSmall() {
        double v = BehaviorScoreFormula.frequencyComponent(1);
        assertTrue(v > 0.0 && v < 0.25,
                "One open should be a small but non-zero contribution, got " + v);
    }

    @Test
    void frequencyComponent_atCap_isOne() {
        assertEquals(1.0, BehaviorScoreFormula.frequencyComponent(BehaviorScoreFormula.FREQ_CAP), EPS);
    }

    @Test
    void frequencyComponent_aboveCap_isClampedToOne() {
        assertEquals(1.0, BehaviorScoreFormula.frequencyComponent(BehaviorScoreFormula.FREQ_CAP * 10), EPS);
    }

    @Test
    void frequencyComponent_isMonotonic() {
        for (int i = 1; i < BehaviorScoreFormula.FREQ_CAP; i++) {
            double a = BehaviorScoreFormula.frequencyComponent(i);
            double b = BehaviorScoreFormula.frequencyComponent(i + 1);
            assertTrue(b >= a, "Frequency must be monotonic; failed at " + i + ": " + a + " -> " + b);
        }
    }

    @Test
    void recencyComponent_nullTimestamp_isZero() {
        assertEquals(0.0, BehaviorScoreFormula.recencyComponent(null, NOW), EPS);
    }

    @Test
    void recencyComponent_blankTimestamp_isZero() {
        assertEquals(0.0, BehaviorScoreFormula.recencyComponent("   ", NOW), EPS);
    }

    @Test
    void recencyComponent_malformedTimestamp_isZero() {
        assertEquals(0.0, BehaviorScoreFormula.recencyComponent("not-a-date", NOW), EPS);
    }

    @Test
    void recencyComponent_openedNow_isOne() {
        assertEquals(1.0, BehaviorScoreFormula.recencyComponent(NOW.toString(), NOW), EPS);
    }

    @Test
    void recencyComponent_oneHalfLifeAgo_isAboutHalf() {
        LocalDateTime past = NOW.minusDays((long) BehaviorScoreFormula.RECENCY_HALF_LIFE_DAYS);
        double v = BehaviorScoreFormula.recencyComponent(past.toString(), NOW);
        assertEquals(0.5, v, 0.01, "One half-life ago should decay to ~0.5");
    }

    @Test
    void recencyComponent_twoHalfLivesAgo_isAboutQuarter() {
        LocalDateTime past = NOW.minusDays(2 * (long) BehaviorScoreFormula.RECENCY_HALF_LIFE_DAYS);
        double v = BehaviorScoreFormula.recencyComponent(past.toString(), NOW);
        assertEquals(0.25, v, 0.01, "Two half-lives ago should decay to ~0.25");
    }

    @Test
    void recencyComponent_futureTimestamp_isClampedToOne() {
        LocalDateTime future = NOW.plusHours(2);
        double v = BehaviorScoreFormula.recencyComponent(future.toString(), NOW);
        assertEquals(1.0, v, EPS);
    }

    @Test
    void positionComponent_noPositionData_isZero() {
        assertEquals(0.0, BehaviorScoreFormula.positionComponent(0L, 0), EPS);
    }

    @Test
    void positionComponent_alwaysOpenedAtPositionOne_isZero() {
        assertEquals(0.0, BehaviorScoreFormula.positionComponent(5L, 5), EPS);
    }

    @Test
    void positionComponent_consistentlyOpenedFromMidList_isPositive() {
        // avg position 5: liftRaw = 4, log1p(4)/log1p(20) ≈ 0.529
        double v = BehaviorScoreFormula.positionComponent(15L, 3);
        assertTrue(v > 0.4 && v < 0.7, "Mid-list lift should be moderately positive, got " + v);
    }

    @Test
    void positionComponent_atCap_isOne() {
        // Avg position == POSITION_CAP + 1 → liftRaw == POSITION_CAP → component == 1.0
        long sum = (long) (BehaviorScoreFormula.POSITION_CAP + 1) * 3L;
        assertEquals(1.0, BehaviorScoreFormula.positionComponent(sum, 3), EPS);
    }

    @Test
    void compute_recentFrequentlyOpenedFile_scoresHigh() {
        double score = BehaviorScoreFormula.compute(20, NOW.minusHours(2).toString(), 60L, 20, NOW);
        assertTrue(score > 0.7, "Recent and frequent opens should score high, got " + score);
    }

    @Test
    void compute_oldRarelyOpenedFile_scoresLow() {
        double score = BehaviorScoreFormula.compute(1, NOW.minusDays(60).toString(), 1L, 1, NOW);
        assertTrue(score < 0.15, "Old + rare should score low, got " + score);
    }

    @Test
    void compute_recencyAffectsScoreIndependentlyOfFrequency() {
        double recent = BehaviorScoreFormula.compute(5, NOW.toString(), 5L, 5, NOW);
        double old    = BehaviorScoreFormula.compute(5, NOW.minusDays(30).toString(), 5L, 5, NOW);
        assertTrue(recent > old, "Recent opens should outscore older opens at same frequency");
    }

    @Test
    void compute_positionLiftDifferentiatesOtherwiseEqualFiles() {
        double topPosition = BehaviorScoreFormula.compute(5, NOW.toString(), 5L, 5, NOW);
        double dugFor      = BehaviorScoreFormula.compute(5, NOW.toString(), 50L, 5, NOW);
        assertNotEquals(topPosition, dugFor, "Position lift should produce different scores");
        assertTrue(dugFor > topPosition, "A file the user dug for should score higher than one always at top");
    }

    @Test
    void compute_resultIsAlwaysInUnitInterval() {
        int[] counts = {0, 1, 5, 50, 500};
        long[] sums  = {0, 1, 50, 500, 5000};
        int[] pcs    = {0, 1, 5, 50};
        for (int c : counts) {
            for (long s : sums) {
                for (int pc : pcs) {
                    double v = BehaviorScoreFormula.compute(c, NOW.toString(), s, pc, NOW);
                    assertTrue(v >= 0.0 && v <= 1.0, "Score out of [0,1] for " + c + "/" + s + "/" + pc + ": " + v);
                }
            }
        }
    }
}