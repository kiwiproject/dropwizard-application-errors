package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.shutdownH2Database;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;

import java.sql.SQLException;

@DisplayName("JdbcApplicationErrorDao (H2)")
public class H2JdbcApplicationErrorDaoTest extends AbstractJdbcApplicationErrorDaoTest {

    private static SimpleSingleConnectionDataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

        dataSource = new SimpleSingleConnectionDataSource(
                dataSourceFactory.getUrl(),
                dataSourceFactory.getUser(),
                dataSourceFactory.getPassword());
    }

    @AfterAll
    static void afterAll() throws SQLException {
        shutdownH2Database(dataSource);

        dataSource = null;
    }

    @Override
    protected SimpleSingleConnectionDataSource getDataSource() {
        return dataSource;
    }
}
