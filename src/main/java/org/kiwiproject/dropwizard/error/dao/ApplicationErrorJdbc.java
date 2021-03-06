package org.kiwiproject.dropwizard.error.dao;

import static java.util.Objects.isNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.format;

import io.dropwizard.db.DataSourceFactory;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
    private static final String H2_IN_MEMORY_DB_PASSWORD = "rotten tomatoes";

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
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            var liquibase = new Liquibase(MIGRATIONS_FILENAME, new ClassLoaderResourceAccessor(), database);
            liquibase.update(new Contexts());
            if (originalAutoCommit != conn.getAutoCommit()) {
                LOG.trace("Liquibase changed Connection's autoCommit to: {}. Restoring to original value: {}",
                        conn.getAutoCommit(), originalAutoCommit);
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            var message = format("Error migrating {} database for Rotten Tomato", getDatabaseProductNameOrUnknown(conn));
            throw new ApplicationErrorJdbcException(message, e);
        }
    }

    private static String getDatabaseProductNameOrUnknown(Connection conn) {
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
     * @implNote Currently this uses ONLY the driver class to make this determination and always assumes H2 databases
     * are NOT shared. This simplistic implementation could change in the future.
     * @see #isH2DataStore(DataSourceFactory)
     */
    public static DataStoreType dataStoreTypeOf(DataSourceFactory dataSourceFactory) {
        checkArgumentNotNull(dataSourceFactory);

        if (isH2DataStore(dataSourceFactory)) {
            return DataStoreType.NOT_SHARED;
        }

        return DataStoreType.SHARED;
    }

    /**
     * Is the given {@link DataSourceFactory} configured for an H2 database?
     *
     * @param dataSourceFactory the DataSourceFactory to check
     * @return true if the driver class is the H2 driver, false otherwise (including null argument)
     * @implNote Currently this uses ONLY the driver class to make this determination and always assumes H2 databases
     * are NOT shared. This simplistic implementation could change in the future.
     */
    public static boolean isH2DataStore(DataSourceFactory dataSourceFactory) {
        if (isNull(dataSourceFactory)) {
            return false;
        }

        return H2_DRIVER.equals(dataSourceFactory.getDriverClass());
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
