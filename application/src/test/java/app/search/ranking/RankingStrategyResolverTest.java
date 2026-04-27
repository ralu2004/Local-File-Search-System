package app.search.ranking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RankingStrategyResolverTest {

    @Test
    void nullSortReturnsStaticStrategy() {
        RankingStrategy strategy = RankingStrategyResolver.getRankingStrategy(null);
        assertInstanceOf(StaticRankingStrategy.class, strategy);
    }

    @Test
    void knownSortValuesReturnExpectedStrategies() {
        assertInstanceOf(DateRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("date"));
        assertInstanceOf(AlphabeticalRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("alpha"));
        assertInstanceOf(BalancedRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("balanced"));
    }

    @Test
    void unknownSortReturnsStaticStrategy() {
        RankingStrategy strategy = RankingStrategyResolver.getRankingStrategy("random");
        assertInstanceOf(StaticRankingStrategy.class, strategy);
    }

    @Test
    void sortIsCaseInsensitive() {
        assertInstanceOf(DateRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("DaTe"));
        assertInstanceOf(AlphabeticalRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("ALPHA"));
        assertInstanceOf(BalancedRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("BaLaNcEd"));
        assertInstanceOf(BehaviorRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("BeHaViOr"));
    }

    @Test
    void behaviorSortReturnsBehaviorStrategy() {
        assertInstanceOf(BehaviorRankingStrategy.class, RankingStrategyResolver.getRankingStrategy("behavior"));
    }
}
