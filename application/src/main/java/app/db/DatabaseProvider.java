package app.db;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Abstraction for opening configured database instances.
 */
public interface DatabaseProvider {
    DatabaseSession openDefault() throws SQLException, IOException;
    DatabaseSession open(String dbPath) throws SQLException, IOException;
}
