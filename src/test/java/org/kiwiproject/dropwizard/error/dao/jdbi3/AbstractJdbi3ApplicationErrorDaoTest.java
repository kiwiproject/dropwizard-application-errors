package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static org.kiwiproject.test.jdbi.Jdbi3GeneratedKeys.executeAndGenerateId;

import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.AbstractApplicationErrorDaoTest;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;

/**
 * Base test class for testing {@link Jdbi3ApplicationErrorDao}. Used to test against different databases, currently
 * Postgres and an in-memory H2 database.
 */
@ExtendWith(ApplicationErrorExtension.class)
@ExtendWith(SoftAssertionsExtension.class)
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
abstract class AbstractJdbi3ApplicationErrorDaoTest extends AbstractApplicationErrorDaoTest<Jdbi3ApplicationErrorDao> {

    private Handle handle;

    @BeforeEach
    final void baseSetUpJdbi3() {
        handle = getTestExtension().getHandle();

        countAndVerifyNoApplicationErrorsExist();
    }

    @Override
    protected Jdbi3ApplicationErrorDao getErrorDao() {
        return getTestExtension().getDao();
    }

    @Override
    protected long insertApplicationError(ApplicationError error) {
        var sql = "INSERT INTO application_errors"
                + " (description, created_at, updated_at, exception_type, exception_message, exception_cause_type,"
                + " exception_cause_message, stack_trace, resolved, host_name, ip_address, port)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        var update = handle.createUpdate(sql)
                .bind(0, error.getDescription())
                .bind(1, error.getCreatedAt())
                .bind(2, error.getUpdatedAt())
                .bind(3, error.getExceptionType())
                .bind(4, error.getExceptionMessage())
                .bind(5, error.getExceptionCauseType())
                .bind(6, error.getExceptionCauseMessage())
                .bind(7, error.getStackTrace())
                .bind(8, error.isResolved())
                .bind(9, error.getHostName())
                .bind(10, error.getIpAddress())
                .bind(11, error.getPort());
        return executeAndGenerateId(update, "id");
    }

    @Override
    protected long countApplicationErrors() {
        return handle.createQuery("select count(*) from application_errors").mapTo(Long.class).one();
    }

    @Override
    protected ApplicationError getErrorOrThrow(long id) {
        return handle.createQuery("select * from application_errors where id = ?")
                .bind(0, id)
                .map(new Jdbi3ApplicationErrorRowMapper())
                .one();
    }

    /**
     * This should return the same instance every time it is called.
     */
    abstract Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension();

}
