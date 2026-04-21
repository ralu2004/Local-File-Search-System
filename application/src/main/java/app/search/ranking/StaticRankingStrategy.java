package app.search.ranking;

/**
 * Default ranking strategy — weighted sum of path feature signals.
 *
 * <ul>
 *   <li>Depth:     30% — shallower paths rank higher</li>
 *   <li>Extension: 30% — source/doc extensions rank higher</li>
 *   <li>Directory: 30% — important directories (src, docs) rank higher</li>
 *   <li>Filename:  10% — shorter, cleaner names rank higher</li>
 * </ul>
 */
public class StaticRankingStrategy implements RankingStrategy {

    private static final double WEIGHT_DEPTH     = 0.3;
    private static final double WEIGHT_EXTENSION = 0.3;
    private static final double WEIGHT_DIRECTORY = 0.3;
    private static final double WEIGHT_FILENAME  = 0.1;

    @Override
    public String orderByClause() {
        return "(" +
                WEIGHT_DEPTH     + " * pf.depth" +
                " + " + WEIGHT_EXTENSION + " * pf.extension_score" +
                " + " + WEIGHT_DIRECTORY + " * pf.directory_score" +
                " + " + WEIGHT_FILENAME  + " * pf.filename_score" +
                ") DESC";
    }

    public double score(PathFeatures features) {
        double total = WEIGHT_DEPTH     * features.depth()
                + WEIGHT_EXTENSION * features.extensionScore()
                + WEIGHT_DIRECTORY * features.directoryScore()
                + WEIGHT_FILENAME  * features.filenameScore();
        return Math.max(0.0, Math.min(1.0, total));
    }
}