package org.kiwiproject.dropwizard.error.dao;

import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWithAny;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.format;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.db.DataSourceFactory;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CommandExecutionException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Helper utilities when using JDBC for application error persistence.
 *
 * @implNote Though public this is really intended only for internal library use.
 */
@UtilityClass
@Slf4j
public class ApplicationErrorJdbc {

    private static final String MIGRATIONS_FILENAME = "dropwizard-app-errors-migrations.xml";
    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String H2_IN_MEMORY_DB_URL = "jdbc:h2:mem:dw-app-errors;DB_CLOSE_DELAY=-1";
    private static final String H2_IN_MEMORY_DB_USERNAME = "appErrorUser";
    private static final String H2_IN_MEMORY_DB_PASSWORD = RandomStringUtils.randomAlphanumeric(20);

    private static final String H2_EMBEDDED_IN_MEMORY_URL_PREFIX = "jdbc:h2:mem:";
    private static final String H2_EMBEDDED_FILE_EXPLICIT_URL_PREFIX = "jdbc:h2:file:";
    private static final String H2_EMBEDDED_FILE_RELATIVE_URL_PREFIX = "jdbc:h2:~/";
    private static final String H2_EMBEDDED_FILE_POSIX_ABSOLUTE_URL_PREFIX = "jdbc:h2:/";
    private static final String H2_EMBEDDED_FILE_WINDOWS_ABSOLUTE_URL_PREFIX = "jdbc:h2:C:";
    private static final String[] H2_EMBEDDED_URL_PREFIXES = {
        H2_EMBEDDED_IN_MEMORY_URL_PREFIX,
        H2_EMBEDDED_FILE_EXPLICIT_URL_PREFIX,
        H2_EMBEDDED_FILE_RELATIVE_URL_PREFIX,
        H2_EMBEDDED_FILE_POSIX_ABSOLUTE_URL_PREFIX,
        H2_EMBEDDED_FILE_WINDOWS_ABSOLUTE_URL_PREFIX
    };
    private static final String H2_AUTOMATIC_MIXED_MODE = "AUTO_SERVER=TRUE";

    /**
     * Creates an in-memory H2 database that will stay alive as long as the JVM is alive and will use the same database
     * for all connections (it uses {@code DB_CLOSE_DELAY=-1} in the database URL to accomplish this).
     *
     * @return a {@link DataSourceFactory} containing the URL and credentials for connecting to the in-memory database
     */
    public static DataSourceFactory createInMemoryH2Database() {
        try (var conn = getInMemoryH2Connection()) {
            migrateDatabase(conn);
            return newInMemoryH2DataSourceFactory();
        } catch (Exception e) {
            throw new ApplicationErrorJdbcException("Error getting connection to in-memory H2 database", e);
        }
    }

    private static Connection getInMemoryH2Connection() throws SQLException {
        return DriverManager.getConnection(H2_IN_MEMORY_DB_URL, H2_IN_MEMORY_DB_USERNAME, H2_IN_MEMORY_DB_PASSWORD);
    }

    private static DataSourceFactory newInMemoryH2DataSourceFactory() {
        var factory = new DataSourceFactory();
        factory.setDriverClass(H2_DRIVER);
        factory.setUrl(H2_IN_MEMORY_DB_URL);
        factory.setUser(H2_IN_MEMORY_DB_USERNAME);
        factory.setPassword(H2_IN_MEMORY_DB_PASSWORD);
        return factory;
    }

    /**
     * Runs database migrations using Liquibase.
     *
     * @param conn the database connection to use for the migrations; it is NOT closed by this method!
     */
    public static void migrateDatabase(Connection conn) {
        checkArgumentNotNull(conn);

        try {
            boolean originalAutoCommit = conn.getAutoCommit();
            var liquibaseDatabase = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            runLiquibaseUpdate(liquibaseDatabase);

            if (originalAutoCommit != conn.getAutoCommit()) {
                LOG.trace("Liquibase changed Connection's autoCommit to: {}. Restoring to original value: {}",
                        conn.getAutoCommit(), originalAutoCommit);
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            var message = format("Error migrating {} database", getDatabaseProductNameOrUnknown(conn));
            throw new ApplicationErrorJdbcException(message, e);
        }
    }

    private static void runLiquibaseUpdate(Database liquibaseDatabase) throws CommandExecutionException {
        var updateCommand = new CommandScope(UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionCommandStep.DATABASE_ARG, liquibaseDatabase)
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, MIGRATIONS_FILENAME);
        var updateResults = updateCommand.execute();
        LOG.debug("Update results: {}", updateResults.getResults());
    }

    @VisibleForTesting
    static String getDatabaseProductNameOrUnknown(Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            LOG.error("Unable to getDatabaseProductName from MetaData for Connection: {}", conn, e);
            return "[Unknown Error]";
        }
    }

    /**
     * Returns the {@link DataStoreType} for the given {@link DataSourceFactory}.
     *
     * @param dataSourceFactory the DataSourceFactory to check
     * @return the resolved DataStoreType
     * @see #isH2EmbeddedDataStore(DataSourceFactory)
     */
    public static DataStoreType dataStoreTypeOf(DataSourceFactory dataSourceFactory) {
        checkArgumentNotNull(dataSourceFactory);

        if (isH2EmbeddedDataStore(dataSourceFactory)) {
            return DataStoreType.NOT_SHARED;
        }

        return DataStoreType.SHARED;
    }

    /**
     * Is the given {@link DataSourceFactory} configured for an H2 database?
     *
     * @param dataSourceFactory the DataSourceFactory to check
     * @return true if the driver class is the H2 driver, false otherwise (including null argument)
     */
    public static boolean isH2DataStore(@Nullable DataSourceFactory dataSourceFactory) {
        if (isNull(dataSourceFactory)) {
            return false;
        }

        return H2_DRIVER.equals(dataSourceFactory.getDriverClass());
    }

    /**
     *  Is the given {@link DataSourceFactory} configured for an embedded (in-memory or file-based) H2 database?
     *
     * @param dataSourceFactory the DataSourceFactory to check
     * @return true if the driver class is the H2 driver, and the databae URL is definitely an embedded in-memory
     * or file-based database connection string. If the driver is not H2, or the URL is not definitively known
     * to be for an embedded database, returns false.
     * @see <a href="http://www.h2database.com/html/features.html#connection_modes">H2 Connection Modes</a>
     * @see <a href="http://www.h2database.com/html/features.html#database_url">H2 Database URL Overview</a>
     * @see <a href="http://www.h2database.com/html/features.html#auto_mixed_mode">H2 Automatic Mixed Mode</a>
     */
    public static boolean isH2EmbeddedDataStore(@Nullable DataSourceFactory dataSourceFactory) {
        if (!isH2DataStore(dataSourceFactory)) {
            return false;
        }

        var url = dataSourceFactory.getUrl();

        return isNotBlank(url) &&
                startsWithAny(url, H2_EMBEDDED_URL_PREFIXES) &&
                isNotH2AutomaticMixedMode(url);
    }

    private static boolean isNotH2AutomaticMixedMode(String url) {
        return !url.toUpperCase(Locale.US).contains(H2_AUTOMATIC_MIXED_MODE);
    }

    /**
     * Runtime exception wrapper around JDBC-related exceptions, e.g. {@link SQLException}.
     */
    public static class ApplicationErrorJdbcException extends RuntimeException {
        ApplicationErrorJdbcException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
