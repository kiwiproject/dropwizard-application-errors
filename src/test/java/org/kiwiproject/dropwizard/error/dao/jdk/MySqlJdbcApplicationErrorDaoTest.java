package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.migrateDatabase;
import static org.kiwiproject.dropwizard.error.util.TestHelpers.newLtsMySQLContainer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@DisplayName("JdbcApplicationErrorDao (MySQL)")
class MySqlJdbcApplicationErrorDaoTest extends AbstractJdbcApplicationErrorDaoTest {

    private static SimpleSingleConnectionDataSource dataSource;

    @Container
    private static final MySQLContainer MYSQL = newLtsMySQLContainer();

    @BeforeAll
    static void beforeAll() {
        migrateDatabase(MYSQL, "dropwizard-app-errors-migrations-mysql.xml");

        dataSource = new SimpleSingleConnectionDataSource(
            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @Override
    protected SimpleSingleConnectionDataSource getDataSource() {
        return dataSource;
    }
}
