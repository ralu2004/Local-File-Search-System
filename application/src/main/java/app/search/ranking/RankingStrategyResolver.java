package app.search.ranking;

public class RankingStrategyResolver {

    public static RankingStrategy getRankingStrategy(String strategy) {
        if (strategy == null) return new StaticRankingStrategy(); //default strategy
        return switch (strategy.toLowerCase()) {
            case "date" -> new DateRankingStrategy();
            case "alpha" -> new AlphabeticalRankingStrategy();
            case "balanced" -> new BalancedRankingStrategy();
            default -> new StaticRankingStrategy();
        };
    }
}
