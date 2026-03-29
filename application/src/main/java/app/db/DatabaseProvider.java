package app.db;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Abstraction for opening configured database instances.
 */
public interface DatabaseProvider {
    Database openDefault() throws SQLException, IOException;
    Database open(String dbPath) throws SQLException, IOException;
}
