package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.migrateDatabase;
import static org.kiwiproject.dropwizard.error.util.TestHelpers.newLatestMySQLContainer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.sql.SQLException;

@DisplayName("Jdbi3ApplicationErrorDao (MySQL)")
@Testcontainers
class MySqlJdbi3ApplicationErrorDaoTest extends AbstractJdbi3ApplicationErrorDaoTest {

    @Container
    private static final MySQLContainer<?> MYSQL = newLatestMySQLContainer();

    @RegisterExtension
    final Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> jdbi3DaoExtension =
            Jdbi3DaoExtension.<Jdbi3ApplicationErrorDao>builder()
                    .daoType(Jdbi3ApplicationErrorDao.class)
                    .url(MYSQL.getJdbcUrl())
                    .username(MYSQL.getUsername())
                    .password(MYSQL.getPassword())
                    .build();

    @Override
    Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension() {
        return jdbi3DaoExtension;
    }

    @BeforeAll
    static void beforeAll() {
        migrateDatabase(MYSQL, "dropwizard-app-errors-migrations-mysql.xml");
    }
}
