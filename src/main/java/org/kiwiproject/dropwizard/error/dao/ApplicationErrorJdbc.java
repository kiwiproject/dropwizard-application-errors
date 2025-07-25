package org.kiwiproject.dropwizard.error.dao;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.format;
import static org.kiwiproject.jdbc.KiwiJdbc.utcZonedDateTimeFromTimestamp;

import com.google.common.annotations.VisibleForTesting;
import io.dropwizard.db.DataSourceFactory;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CommandExecutionException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.jdbc.UncheckedSQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
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
    private static final String H2_IN_MEMORY_DB_PASSWORD = RandomStringUtils.secure().nextAlphanumeric(20);

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
        } catch (SQLException e) {
            throw new UncheckedSQLException("Error getting connection to in-memory H2 database", e);
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
     * <p>
     * This uses {@code dropwizard-app-errors-migrations.xml} as the migrations file name.
     *
     * @param conn the database connection to use for the migrations; it is NOT closed by this method!
     * @see #migrateDatabase(Connection, String)
     */
    public static void migrateDatabase(Connection conn) {
        migrateDatabase(conn, MIGRATIONS_FILENAME);
    }

    /**
     * Runs database migrations using Liquibase.
     *
     * @param conn the database connection to use for the migrations; it is NOT closed by this method!
     * @param migrationsFilename the file name containing the database migrations
     */
    public static void migrateDatabase(Connection conn, String migrationsFilename) {
        checkArgumentNotNull(conn);

        try {
            boolean originalAutoCommit = conn.getAutoCommit();
            var liquibaseDatabase = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            runLiquibaseUpdate(liquibaseDatabase, migrationsFilename);

            if (originalAutoCommit != conn.getAutoCommit()) {
                LOG.trace("Liquibase changed Connection's autoCommit to: {}. Restoring to original value: {}",
                        conn.getAutoCommit(), originalAutoCommit);
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            var message = format("JDBC/SQL error while migrating {} database", getDatabaseProductNameOrUnknown(conn));
            throw new UncheckedSQLException(message, e);
        } catch (Exception e) {
            var message = format("Error migrating {} database", getDatabaseProductNameOrUnknown(conn));
            throw new ApplicationErrorJdbcException(message, e);
        }
    }

    private static void runLiquibaseUpdate(Database liquibaseDatabase, String migrationsFilename)
            throws CommandExecutionException {

        var updateCommand = new CommandScope(UpdateCommandStep.COMMAND_NAME)
                .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, liquibaseDatabase)
                .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, migrationsFilename);
        var updateResults = updateCommand.execute();
        LOG.debug("Liquibase update results: {}", updateResults.getResults());
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
     * @return true if the driver class is the H2 driver and the JDBC URL starts with the H2 prefix "jdbc:h2:",
     * otherwise false (including a null argument)
     */
    public static boolean isH2DataStore(@Nullable DataSourceFactory dataSourceFactory) {
        if (isNull(dataSourceFactory)) {
            return false;
        }

        return H2_DRIVER.equals(dataSourceFactory.getDriverClass()) &&
                isNotBlank(dataSourceFactory.getUrl())
                && dataSourceFactory.getUrl().startsWith("jdbc:h2:");
    }

    /**
     * Is the given {@link DataSourceFactory} configured for an embedded (in-memory or file-based) H2 database?
     * <p>
     * Note that since H2 Automatic Mixed Mode allows both a single embedded connection and remote
     * connections, for example from separate process, this is not considered embedded.
     *
     * @param dataSourceFactory the DataSourceFactory to check
     * @return true if the driver class is the H2 driver, and the database URL is definitely an embedded in-memory
     * or file-based database connection string. If the driver is not H2, or the URL is not definitively known
     * to be for an embedded database, returns false.
     * @see <a href="http://www.h2database.com/html/features.html#connection_modes">H2 Connection Modes</a>
     * @see <a href="http://www.h2database.com/html/features.html#database_url">H2 Database URL Overview</a>
     * @see <a href="http://www.h2database.com/html/features.html#auto_mixed_mode">H2 Automatic Mixed Mode</a>
     */
    public static boolean isH2EmbeddedDataStore(@Nullable DataSourceFactory dataSourceFactory) {
        if (isNotH2DataStore(dataSourceFactory)) {
            return false;
        }

        var url = requireNonNull(dataSourceFactory).getUrl();

        return Strings.CS.startsWithAny(url, H2_EMBEDDED_URL_PREFIXES) && isNotH2AutomaticMixedMode(url);
    }

    private static boolean isNotH2DataStore(@Nullable DataSourceFactory dataSourceFactory) {
        return !isH2DataStore(dataSourceFactory);
    }

    private static boolean isNotH2AutomaticMixedMode(String url) {
        return !url.toUpperCase(Locale.US).contains(H2_AUTOMATIC_MIXED_MODE);
    }

    public static ApplicationError mapFrom(ResultSet rs) throws SQLException {
        return ApplicationError.builder()
                .id(rs.getLong("id"))
                .createdAt(utcZonedDateTimeFromTimestamp(rs, "created_at"))
                .updatedAt(utcZonedDateTimeFromTimestamp(rs, "updated_at"))
                .numTimesOccurred(rs.getInt("num_times_occurred"))
                .description(rs.getString("description"))
                .exceptionType(rs.getString("exception_type"))
                .exceptionMessage(rs.getString("exception_message"))
                .exceptionCauseType(rs.getString("exception_cause_type"))
                .exceptionCauseMessage(rs.getString("exception_cause_message"))
                .stackTrace(rs.getString("stack_trace"))
                .resolved(rs.getBoolean("resolved"))
                .hostName(rs.getString("host_name"))
                .ipAddress(rs.getString("ip_address"))
                .port(rs.getInt("port"))
                .build();
    }

    /**
     * Runtime exception wrapper around generic database- or migration-related exceptions, such as
     * those thrown by Liquibase.
     * <p>
     * This should <em>not</em> wrap {@link SQLException} (use {@link org.kiwiproject.jdbc.UncheckedSQLException}).
     */
    public static class ApplicationErrorJdbcException extends RuntimeException {
        public ApplicationErrorJdbcException(Throwable cause) {
            super(cause);
        }

        ApplicationErrorJdbcException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
