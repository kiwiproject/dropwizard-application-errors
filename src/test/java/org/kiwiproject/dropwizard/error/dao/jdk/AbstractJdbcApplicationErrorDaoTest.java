package org.kiwiproject.dropwizard.error.dao.jdk;

import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc.nextOrThrow;
import static org.kiwiproject.jdbc.KiwiJdbc.timestampFromZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.AbstractApplicationErrorDaoTest;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.test.jdbc.RuntimeSQLException;
import org.kiwiproject.test.jdbc.SimpleSingleConnectionDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base test class for testing {@link JdbcApplicationErrorDao}. Used to test against different databases, currently
 * Postgres and an in-memory H2 database.
 * <p>
 * This base class requires subclasses to provide a {@link SimpleSingleConnectionDataSource} so that we can
 * set up a transaction before each test, and roll it back after each test. Using {@link SimpleSingleConnectionDataSource}
 * ensures that the {@link JdbcApplicationErrorDao} under test uses the same Connection throughout each test.
 */
@SuppressWarnings({ "SqlDialectInspection", "SqlNoDataSourceInspection" })
@ExtendWith(ApplicationErrorExtension.class)
public abstract class AbstractJdbcApplicationErrorDaoTest extends AbstractApplicationErrorDaoTest<JdbcApplicationErrorDao> {

    protected abstract SimpleSingleConnectionDataSource getDataSource();

    private Connection connection;

    @BeforeEach
    void baseSetUpJdbc() throws SQLException {
        connection = getDataSource().getConnection();
        connection.setAutoCommit(false);

        countAndVerifyNoApplicationErrorsExist();
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.rollback();
    }

    @Override
    protected JdbcApplicationErrorDao getErrorDao() {
        return new JdbcApplicationErrorDao(getDataSource());
    }

    @Override
    protected long insertApplicationError(ApplicationError error) {
        var sql = "INSERT INTO application_errors"
                + " (description, created_at, updated_at, exception_type, exception_message, exception_cause_type,"
                + " exception_cause_message, stack_trace, resolved, host_name, ip_address, port)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        try (var conn = connection(); var ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, error.getDescription());
            ps.setTimestamp(2, timestampFromZonedDateTime(error.getCreatedAt()));
            ps.setTimestamp(3, timestampFromZonedDateTime(error.getUpdatedAt()));
            ps.setString(4, error.getExceptionType());
            ps.setString(5, error.getExceptionMessage());
            ps.setString(6, error.getExceptionCauseType());
            ps.setString(7, error.getExceptionCauseMessage());
            ps.setString(8, error.getStackTrace());
            ps.setBoolean(9, error.isResolved());
            ps.setString(10, error.getHostName());
            ps.setString(11, error.getIpAddress());
            ps.setInt(12, error.getPort());

            var count = ps.executeUpdate();
            checkState(count == 1);

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                generatedKeys.next();
                return generatedKeys.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    protected long countApplicationErrors() {
        try (var conn = connection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("select count(*) from application_errors")) {

            nextOrThrow(rs);
            return rs.getLong(1);

        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    @Override
    protected ApplicationError getErrorOrThrow(long id) {
        try (var conn = connection();
             var ps = conn.prepareStatement("select * from application_errors where id  = ?")) {

            ps.setLong(1, id);

            try (var rs = ps.executeQuery()) {
                nextOrThrow(rs);
                return ApplicationErrorJdbc.mapFrom(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeSQLException(e);
        }
    }

    protected Connection connection() throws SQLException {
        return getDataSource().getConnection();
    }
}
