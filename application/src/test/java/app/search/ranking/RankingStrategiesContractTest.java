package app.search.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingStrategiesContractTest {

    @Test
    void staticStrategyOrderByReferencesPathFeatureColumns() {
        String clause = new StaticRankingStrategy().orderByClause();
        assertTrue(clause.contains("pf.depth"));
        assertTrue(clause.contains("pf.extension_score"));
        assertTrue(clause.contains("pf.directory_score"));
        assertTrue(clause.contains("pf.filename_score"));
    }

    @Test
    void dateStrategyOrderByUsesModifiedAtDescending() {
        String clause = new DateRankingStrategy().orderByClause();
        assertTrue(clause.contains("f.modified_at"));
        assertTrue(clause.contains("DESC"));
    }

    @Test
    void alphabeticalStrategyOrderByUsesCaseInsensitiveFilenameSort() {
        String clause = new AlphabeticalRankingStrategy().orderByClause();
        assertTrue(clause.contains("f.filename"));
        assertTrue(clause.contains("COLLATE NOCASE"));
        assertTrue(clause.contains("ASC"));
    }

    @Test
    void balancedStrategyOrderByReferencesAllPathFeatureColumns() {
        String clause = new BalancedRankingStrategy().orderByClause();
        assertTrue(clause.contains("pf.depth"));
        assertTrue(clause.contains("pf.extension_score"));
        assertTrue(clause.contains("pf.directory_score"));
        assertTrue(clause.contains("pf.filename_score"));
    }

    @Test
    void nonBehaviorStrategiesDoNotProduceInsights() {
        assertFalse(new StaticRankingStrategy().producesInsights());
        assertFalse(new DateRankingStrategy().producesInsights());
        assertFalse(new AlphabeticalRankingStrategy().producesInsights());
        assertFalse(new BalancedRankingStrategy().producesInsights());
    }
}
