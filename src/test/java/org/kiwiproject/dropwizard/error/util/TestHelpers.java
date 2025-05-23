package org.kiwiproject.dropwizard.error.util;

import static com.google.common.base.Preconditions.checkState;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc.ApplicationErrorJdbcException;
import org.kiwiproject.jdbc.UncheckedSQLException;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@UtilityClass
@Slf4j
public class TestHelpers {

    @SuppressWarnings({ "SqlDialectInspection", "SqlNoDataSourceInspection" })
    public static void shutdownH2Database(DataSource h2DataSource) throws SQLException {
        try (var conn = h2DataSource.getConnection(); var stmt = conn.createStatement()) {
            checkIsH2Connection(conn);
            stmt.executeUpdate("shutdown");
        }
    }

    /**
     * @implNote This checks against the Connection instead of the DataSource because the DataSource
     * is proxied, i.e., it is a {@link org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource}
     * so it can't just be cast to an H2 {@link org.h2.jdbcx.JdbcDataSource}.
     */
    private static void checkIsH2Connection(Connection conn) throws SQLException {
        var databaseProductName = conn.getMetaData().getDatabaseProductName();
        checkState("H2".equalsIgnoreCase(databaseProductName),
                "Connection is not to an H2 database. Database product name: %s", databaseProductName);
    }

    public static MySQLContainer<?> newLtsMySQLContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:lts"));
    }

    /**
     * Use the given JDBC testcontainer to run Liquibase migrations.
     *
     * @param container a JDBC testcontainer
     * @param migrationsFilename the Liquibase migrations file
     * @throws ApplicationErrorJdbcException if the migration fails
     */
    public static void migrateDatabase(JdbcDatabaseContainer<?> container, String migrationsFilename) {
        var dockerImageName = container.getDockerImageName();
        var jdbcUrl = container.getJdbcUrl();
        LOG.info("Migrating {} container database with JDBC URL {} using file {}", dockerImageName, jdbcUrl, migrationsFilename);

        try (var conn = DriverManager.getConnection(jdbcUrl, container.getUsername(), container.getPassword())) {
            ApplicationErrorJdbc.migrateDatabase(conn, migrationsFilename);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }

        LOG.info("Completed migrating {} container database using file {}", dockerImageName, migrationsFilename);
    }

    /**
     * Create an in-memory SQLite database and return a single-connection DataSource for testing.
     *
     * @return a new instance of {@link SimpleSingleConnectionDataSource}
     */
    public static SimpleSingleConnectionDataSource newInMemorySqliteDataSource() {
        return new SimpleSingleConnectionDataSource("jdbc:sqlite::memory:", "");
    }

    /**
     * Use the given single-connection DataSource to run Liquibase migrations.
     *
     * @param dataSource the data source from which to obtain a Connection
     * @param migrationsFilename the Liquibase migrations file
     */
    public static void migrateDatabase(SimpleSingleConnectionDataSource dataSource, String migrationsFilename) {
        LOG.info("Migrating database with JDBC URL {} using file {}", dataSource.getUrl(), migrationsFilename);
        try {
            ApplicationErrorJdbc.migrateDatabase(dataSource.getConnection(), migrationsFilename);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
        LOG.info("Completed migrating database using file {}", migrationsFilename);
    }
}
