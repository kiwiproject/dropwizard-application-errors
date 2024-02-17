package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.migrateDatabase;
import static org.kiwiproject.dropwizard.error.util.TestHelpers.newInMemorySqliteDataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;

@DisplayName("JdbcApplicationErrorDao (SQLite)")
class SqliteJdbcApplicationErrorDaoTest extends AbstractJdbcApplicationErrorDaoTest {

    private static SimpleSingleConnectionDataSource DATA_SOURCE;

    @BeforeAll
    static void beforeAll() {
        DATA_SOURCE = newInMemorySqliteDataSource();
        migrateDatabase(DATA_SOURCE, "dropwizard-app-errors-migrations-sqlite.xml");
    }

    @Override
    protected SimpleSingleConnectionDataSource getDataSource() {
        return DATA_SOURCE;
    }
}
