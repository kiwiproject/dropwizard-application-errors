package org.kiwiproject.dropwizard.error.util;

import lombok.experimental.UtilityClass;

import org.h2.jdbcx.JdbcDataSource;

import java.sql.SQLException;

@UtilityClass
public class TestHelpers {

    @SuppressWarnings({ "SqlDialectInspection", "SqlNoDataSourceInspection" })
    public static void shutdownH2Database(JdbcDataSource h2DataSource) throws SQLException {
        try (var conn = h2DataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.executeUpdate("shutdown");
        }
    }
}
