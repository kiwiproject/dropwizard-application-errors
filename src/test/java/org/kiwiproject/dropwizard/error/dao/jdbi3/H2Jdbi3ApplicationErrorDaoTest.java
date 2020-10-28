package org.kiwiproject.dropwizard.error.dao.jdbi3;

import org.h2.jdbcx.JdbcDataSource;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;

@DisplayName("Jdbi3ApplicationErrorDao (H2)")
public class H2Jdbi3ApplicationErrorDaoTest extends AbstractJdbi3ApplicationErrorDaoTest {

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
    static void afterAll() {
        DATA_SOURCE = null;
    }

    @RegisterExtension final Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> jdbi3DaoExtension =
            Jdbi3DaoExtension.<Jdbi3ApplicationErrorDao>builder()
                    .daoType(Jdbi3ApplicationErrorDao.class)
                    .dataSource(DATA_SOURCE)
                    .plugin(new H2DatabasePlugin())
                    .build();

    @Override
    Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension() {
        return jdbi3DaoExtension;
    }
}
