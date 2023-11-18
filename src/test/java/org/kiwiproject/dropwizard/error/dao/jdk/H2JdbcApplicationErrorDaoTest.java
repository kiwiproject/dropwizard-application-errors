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

    private static SimpleSingleConnectionDataSource DATA_SOURCE;

    @BeforeAll
    static void beforeAll() {
        var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

        DATA_SOURCE = new SimpleSingleConnectionDataSource(
                dataSourceFactory.getUrl(),
                dataSourceFactory.getUser(),
                dataSourceFactory.getPassword());
    }

    @AfterAll
    static void afterAll() throws SQLException {
        shutdownH2Database(DATA_SOURCE);

        DATA_SOURCE = null;
    }

    @Override
    protected SimpleSingleConnectionDataSource getDataSource() {
        return DATA_SOURCE;
    }
}
