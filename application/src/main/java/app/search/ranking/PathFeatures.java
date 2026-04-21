package app.search.ranking;

/**
 * Normalized feature signals extracted from a file path at index time.
 * <p>
 * Each field is in the range [0.0, 1.0]. Higher values indicate a stronger
 * signal for that dimension. Features are stored in the {@code path_features}
 * table and combined by a {@link RankingStrategy} at query time.
 */
public record PathFeatures(
        double depth,
        double extensionScore,
        double directoryScore,
        double filenameScore
) {}