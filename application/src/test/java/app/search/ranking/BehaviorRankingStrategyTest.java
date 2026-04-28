package app.search.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorRankingStrategyTest {

    @Test
    void behaviorStrategyProducesInsights() {
        assertTrue(new BehaviorRankingStrategy().producesInsights());
    }

    @Test
    void orderByClause_usesBehaviorScoreAndHistorySignals() {
        String clause = new BehaviorRankingStrategy().orderByClause();
        assertTrue(clause.contains("behavior_score("));
        assertTrue(clause.contains("roh.open_count"));
        assertTrue(clause.contains("roh.last_opened_at"));
        assertTrue(clause.contains("roh.position_sum"));
        assertTrue(clause.contains("roh.position_count"));
        assertTrue(clause.contains("sh.query_count"));
    }
}
