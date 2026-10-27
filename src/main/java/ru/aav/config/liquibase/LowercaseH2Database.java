package ru.aav.config.liquibase;

import java.sql.SQLException;
import liquibase.database.DatabaseConnection;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;

/**
 * Liquibase 4.24 assumes uppercase H2 identifiers. Our existing local databases
 * use DATABASE_TO_LOWER=TRUE, so metadata lookups must use lowercase as well.
 * This adapter preserves existing files and migration history without renaming
 * tables, changing database settings, or disabling migrations on restart.
 */
public class LowercaseH2Database extends H2Database {

    public LowercaseH2Database() {
        unquotedObjectsAreUppercased = false;
    }

    @Override
    public int getPriority() {
        return super.getPriority() + 1;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(
        DatabaseConnection connection
    ) throws DatabaseException {
        if (
            !super.isCorrectDatabaseImplementation(connection) ||
            !(connection instanceof JdbcConnection jdbc)
        ) {
            return false;
        }
        try {
            return jdbc
                .getWrappedConnection()
                .getMetaData()
                .storesLowerCaseIdentifiers();
        } catch (SQLException exception) {
            throw new DatabaseException(exception);
        }
    }
}
