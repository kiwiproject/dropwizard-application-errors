package org.kiwiproject.dropwizard.error.dao.jdbi3;

import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;
import org.kiwiproject.test.junit.jupiter.PostgresLiquibaseTestExtension;

public class PostgresJdbi3ApplicationErrorDaoTest extends AbstractJdbi3ApplicationErrorDaoTest {

    @RegisterExtension
    static final PostgresLiquibaseTestExtension POSTGRES =
            new PostgresLiquibaseTestExtension("dropwizard-app-errors-migrations.xml");

    @RegisterExtension
    final Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> jdbi3DaoExtension =
            Jdbi3DaoExtension.<Jdbi3ApplicationErrorDao>builder()
                    .daoType(Jdbi3ApplicationErrorDao.class)
                    .dataSource(POSTGRES.getTestDataSource())
                    .plugin(new H2DatabasePlugin())
                    .build();

    @Override
    Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension() {
        return jdbi3DaoExtension;
    }
}
