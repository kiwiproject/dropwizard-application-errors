package org.kiwiproject.dropwizard.error.dao.jdk;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;

import javax.sql.DataSource;
import java.sql.SQLException;

@DisplayName("JdbcApplicationErrorDao (H2)")
public class H2JdbcApplicationErrorDaoTest extends AbstractJdbcApplicationErrorDaoTest {

    private static JdbcDataSource DATA_SOURCE;

    @BeforeAll
    static void beforeAll() {
        var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

        DATA_SOURCE = new JdbcDataSource();
        DATA_SOURCE.setUrl(dataSourceFactory.getUrl());
        DATA_SOURCE.setUser(dataSourceFactory.getUser());
        DATA_SOURCE.setPassword(dataSourceFactory.getPassword());
    }

    @AfterAll
    static void afterAll() throws SQLException {

        ApplicationErrorJdbc.shutdownH2Database(DATA_SOURCE);

        DATA_SOURCE = null;
    }

    @Override
    protected DataSource getDataSource() {
        return DATA_SOURCE;
    }
}
