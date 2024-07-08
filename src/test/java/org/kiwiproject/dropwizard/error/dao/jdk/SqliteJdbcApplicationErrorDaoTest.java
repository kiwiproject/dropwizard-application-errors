package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.migrateDatabase;
import static org.kiwiproject.dropwizard.error.util.TestHelpers.newInMemorySqliteDataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;

@DisplayName("JdbcApplicationErrorDao (SQLite)")
class SqliteJdbcApplicationErrorDaoTest extends AbstractJdbcApplicationErrorDaoTest {

    private static SimpleSingleConnectionDataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        dataSource = newInMemorySqliteDataSource();
        migrateDatabase(dataSource, "dropwizard-app-errors-migrations-sqlite.xml");
    }

    @Override
    protected SimpleSingleConnectionDataSource getDataSource() {
        return dataSource;
    }
}
