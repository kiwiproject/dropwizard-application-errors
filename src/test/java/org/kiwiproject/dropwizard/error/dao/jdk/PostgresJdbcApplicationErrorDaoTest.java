package org.kiwiproject.dropwizard.error.dao.jdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;
import org.kiwiproject.test.junit.jupiter.PostgresLiquibaseTestExtension;

@DisplayName("JdbcApplicationErrorDao (Postgres)")
public class PostgresJdbcApplicationErrorDaoTest extends AbstractJdbcApplicationErrorDaoTest {

    @RegisterExtension
    static final PostgresLiquibaseTestExtension POSTGRES =
            new PostgresLiquibaseTestExtension("dropwizard-app-errors-migrations.xml");

    @Override
    protected SimpleSingleConnectionDataSource getDataSource() {
        return POSTGRES.getTestDataSource();
    }
}
