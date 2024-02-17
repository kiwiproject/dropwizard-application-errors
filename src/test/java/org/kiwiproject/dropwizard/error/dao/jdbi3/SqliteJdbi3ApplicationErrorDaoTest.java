package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.migrateDatabase;
import static org.kiwiproject.dropwizard.error.util.TestHelpers.newInMemorySqliteDataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;

@DisplayName("Jdbi3ApplicationErrorDao (SQLite)")
class SqliteJdbi3ApplicationErrorDaoTest extends AbstractJdbi3ApplicationErrorDaoTest {

    private static SimpleSingleConnectionDataSource DATA_SOURCE;

    @BeforeAll
    static void beforeAll() {
        DATA_SOURCE = newInMemorySqliteDataSource();
        migrateDatabase(DATA_SOURCE, "dropwizard-app-errors-migrations-sqlite.xml");
    }

    @RegisterExtension
    final Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> jdbi3DaoExtension =
            Jdbi3DaoExtension.<Jdbi3ApplicationErrorDao>builder()
                    .daoType(Jdbi3ApplicationErrorDao.class)
                    .dataSource(DATA_SOURCE)
                    .build();

    @Override
    Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension() {
        return jdbi3DaoExtension;
    }
}
