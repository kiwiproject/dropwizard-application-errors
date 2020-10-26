package org.kiwiproject.dropwizard.error.dao.jdbi3;

import org.jdbi.v3.core.h2.H2DatabasePlugin;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.junit.jupiter.H2FileBasedDatabaseExtension;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;

// TODO First implement ApplicationErrorJdbc helper class, then change to run migrations, then enable this test...

@Disabled
public class H2Jdbi3ApplicationErrorDaoTest extends AbstractJdbi3ApplicationErrorDaoTest {

    @RegisterExtension
    static final H2FileBasedDatabaseExtension DATABASE_EXTENSION = new H2FileBasedDatabaseExtension();

    @RegisterExtension
    final Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> jdbi3DaoExtension =
            Jdbi3DaoExtension.<Jdbi3ApplicationErrorDao>builder()
                    .daoType(Jdbi3ApplicationErrorDao.class)
                    .dataSource(DATABASE_EXTENSION.getDataSource())
                    .plugin(new H2DatabasePlugin())
                    .build();

    @Override
    Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension() {
        return jdbi3DaoExtension;
    }
}
