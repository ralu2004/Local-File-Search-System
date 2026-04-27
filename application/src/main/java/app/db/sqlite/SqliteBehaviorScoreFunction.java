package app.db.sqlite;

import app.search.ranking.behavior.BehaviorScoreFormula;
import org.sqlite.Function;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * SQLite user-defined function that exposes {@link BehaviorScoreFormula#compute}
 * to SQL queries so a behavior-based ranking strategy can invoke it from an
 * {@code ORDER BY} clause.
 * <p>
 * This class is purely an <i>adapter</i>: it lives in the SQLite-binding
 * package because its sole purpose is to bridge xerial's {@link Function}
 * abstraction to the formula, and it has no ranking semantics of its own.
 * The formula constants ({@code WEIGHT_*}, {@code FREQ_CAP}, etc.) intentionally
 * stay in {@link BehaviorScoreFormula}, which knows nothing about SQLite.
 * <p>
 * Registered on every connection by {@link SqliteConnectionProvider}.
 *
 * <h2>SQL signature</h2>
 * <pre>{@code
 *   behavior_score(open_count, last_opened_at, position_sum, position_count, query_count)
 *   -> REAL in [0, 1]
 * }</pre>
 * The fifth argument ({@code query_count}) is reserved for future use: the
 * formula does not consume it today, but the registered arity is fixed so
 * callers can pass it without forcing a coordinated UDF-and-callsite change
 * later.
 */
public class SqliteBehaviorScoreFunction extends Function {

    /** Name under which this function is registered with SQLite. */
    public static final String NAME = "behavior_score";

    /** Number of arguments SQLite passes: open_count, last_opened_at, position_sum, position_count, query_count. */
    public static final int ARG_COUNT = 5;

    @Override
    protected void xFunc() throws SQLException {
        int openCount       = value_int(0);
        String lastOpenedAt = value_text(1);
        long positionSum    = value_long(2);
        int positionCount   = value_int(3);

        result(BehaviorScoreFormula.compute(openCount, lastOpenedAt, positionSum, positionCount, LocalDateTime.now()));
    }
}