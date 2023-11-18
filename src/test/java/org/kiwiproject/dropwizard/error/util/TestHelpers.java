package org.kiwiproject.dropwizard.error.util;

import static com.google.common.base.Preconditions.checkState;

import lombok.experimental.UtilityClass;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@UtilityClass
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
}
