package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static org.kiwiproject.dropwizard.error.util.TestHelpers.shutdownH2Database;

import org.h2.jdbcx.JdbcDataSource;
import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;

import java.sql.SQLException;

@DisplayName("Jdbi3ApplicationErrorDao (H2)")
class H2Jdbi3ApplicationErrorDaoTest extends AbstractJdbi3ApplicationErrorDaoTest {

    private static JdbcDataSource dataSource;

    @BeforeAll
    static void beforeAll() {
        var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();

        dataSource = new JdbcDataSource();
        dataSource.setUrl(dataSourceFactory.getUrl());
        dataSource.setUser(dataSourceFactory.getUser());
        dataSource.setPassword(dataSourceFactory.getPassword());
    }

    @AfterAll
    static void afterAll() throws SQLException {
       shutdownH2Database(dataSource);

        dataSource = null;
    }

    @RegisterExtension final Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> jdbi3DaoExtension =
            Jdbi3DaoExtension.<Jdbi3ApplicationErrorDao>builder()
                    .daoType(Jdbi3ApplicationErrorDao.class)
                    .dataSource(dataSource)
                    .plugin(new H2DatabasePlugin())
                    .build();

    @Override
    Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension() {
        return jdbi3DaoExtension;
    }
}
