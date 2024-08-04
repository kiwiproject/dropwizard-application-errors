package org.kiwiproject.dropwizard.error.dao.jdk;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentIsNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao.checkPagingArgumentsAndCalculateZeroBasedOffset;
import static org.kiwiproject.jdbc.KiwiJdbc.nextOrThrow;
import static org.kiwiproject.jdbc.KiwiJdbc.timestampFromZonedDateTime;

import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.jdbc.UncheckedSQLException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

/**
 * Implementation of {@link ApplicationErrorDao} that uses plain JDBC, and therefore does not require
 * any additional dependencies outside the JDK. It might be useful when an application is using a
 * relational database but something other than JDBI, for example Hibernate or another ORM or ORM-like
 * framework such as jOOQ.
 */
@SuppressWarnings({ "SqlDialectInspection", "SqlNoDataSourceInspection" })
public class JdbcApplicationErrorDao implements ApplicationErrorDao {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private final DataSource dataSource;

    public JdbcApplicationErrorDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ApplicationError> getById(long id) {
        try (var conn = connection();
             var ps = conn.prepareStatement("select * from application_errors where id = ?")) {

            ps.setLong(1, id);

            try (var resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    var applicationError = ApplicationErrorJdbc.mapFrom(resultSet);
                    return Optional.of(applicationError);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public long countResolvedErrors() {
        return countUsingQuery("select count(id) from application_errors where resolved = true");
    }

    @Override
    public long countUnresolvedErrors() {
        return countUsingQuery("select count(id) from application_errors where resolved = false");
    }

    @Override
    public long countAllErrors() {
        return countUsingQuery("select count(id) from application_errors");
    }

    private long countUsingQuery(String sql) {
        try (var conn = connection(); var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            nextOrThrow(rs);
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public long countUnresolvedErrorsSince(ZonedDateTime since) {
        try (var conn = connection();
             var ps = conn.prepareStatement("select count(id) from application_errors where resolved = false and updated_at >= ?")) {

            ps.setTimestamp(1, timestampFromZonedDateTime(since));

            try (var rs = ps.executeQuery()) {
                nextOrThrow(rs);
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public long countUnresolvedErrorsOnHostSince(ZonedDateTime since, String hostName, String ipAddress) {
        try (var conn = connection();
             var ps = conn.prepareStatement("select count(id) from application_errors" +
                     " where resolved = false and updated_at >= ? and host_name = ? and ip_address = ?")) {

            ps.setTimestamp(1, timestampFromZonedDateTime(since));
            ps.setString(2, hostName);
            ps.setString(3, ipAddress);

            try (var rs = ps.executeQuery()) {
                nextOrThrow(rs);
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public List<ApplicationError> getAllErrors(int pageNumber, int pageSize) {
        int offset = checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize);
        var sql = "select * from application_errors order by updated_at desc" +
                paginationClause(pageSize, offset);

        try (var conn = connection(); var stmt = conn.createStatement(); var rs = stmt.executeQuery(sql)) {
            return collectErrors(rs, pageSize);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public List<ApplicationError> getErrors(ApplicationErrorStatus status, int pageNumber, int pageSize) {
        checkNotNull(status, "status cannot be null");

        return switch (status) {
            case ALL -> getAllErrors(pageNumber, pageSize);
            case RESOLVED -> getErrors(true, pageNumber, pageSize);
            case UNRESOLVED -> getErrors(false, pageNumber, pageSize);
        };
    }

    private List<ApplicationError> getErrors(boolean resolved, int pageNumber, int pageSize) {
        int offset = checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize);
        var sql = "select * from application_errors where resolved = ? order by updated_at desc"
                + paginationClause(pageSize, offset);

        try (var conn = connection(); var ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, resolved);
            return collectErrors(ps, pageSize);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    private static String paginationClause(int pageSize, int offset) {
        return f(" limit {} offset {}", pageSize, offset);
    }

    @Override
    public List<ApplicationError> getUnresolvedErrorsByDescription(String description) {
        var sql = "select * from application_errors" +
                " where resolved = false and description = ? order by updated_at desc";

        try (var conn = connection(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, description);
            return collectErrors(ps, DEFAULT_PAGE_SIZE);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public List<ApplicationError> getUnresolvedErrorsByDescriptionAndHost(String description, String hostName) {
        var sql = "select * from application_errors" +
                " where resolved = false and description = ? and host_name = ? order by updated_at desc";

        try (var conn = connection(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, description);
            ps.setString(2, hostName);
            return collectErrors(ps, DEFAULT_PAGE_SIZE);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    private static List<ApplicationError> collectErrors(PreparedStatement ps, int pageSize) throws SQLException {
        try (var rs = ps.executeQuery()) {
            return collectErrors(rs, pageSize);
        }
    }

    private static List<ApplicationError> collectErrors(ResultSet rs, int pageSize) throws SQLException {
        var errors = new ArrayList<ApplicationError>(pageSize);
        while (rs.next()) {
            var error = ApplicationErrorJdbc.mapFrom(rs);
            errors.add(error);
        }
        return Collections.unmodifiableList(errors);
    }

    @Override
    public long insertError(ApplicationError newError) {
        checkArgumentIsNull(newError.getId(), "Cannot insert an ApplicationError that has an id");

        var sql = "insert into application_errors" +
                " (description, exception_type, exception_message, exception_cause_type, exception_cause_message," +
                " stack_trace, host_name, ip_address, port)" +
                " values (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (var conn = connection(); var ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, newError.getDescription());
            ps.setString(2, newError.getExceptionType());
            ps.setString(3, newError.getExceptionMessage());
            ps.setString(4, newError.getExceptionCauseType());
            ps.setString(5, newError.getExceptionCauseMessage());
            ps.setString(6, newError.getStackTrace());
            ps.setString(7, newError.getHostName());
            ps.setString(8, newError.getIpAddress());
            ps.setInt(9, newError.getPort());

            var count = ps.executeUpdate();
            checkState(count == 1, "Insert count should be one, but is: %s", count);

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                nextOrThrow(generatedKeys);
                return generatedKeys.getLong(1);
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public void incrementCount(long id) {
        var sql = "update application_errors" +
                " set num_times_occurred = num_times_occurred + 1, updated_at = current_timestamp" +
                " where id = ?";

        try (var conn = connection(); var ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);

            var count = ps.executeUpdate();
            checkState(count == 1, "Unable to increment count. No ApplicationError found with id %s", id);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public long insertOrIncrementCount(ApplicationError error) {
        checkNotNull(error.getDescription(), "Error description cannot be null");

        var errors = getUnresolvedErrorsByDescriptionAndHost(error.getDescription(), error.getHostName());

        if (errors.isEmpty()) {
            return insertError(error);
        }

        var existingError = first(errors);
        incrementCount(existingError.getId());
        return existingError.getId();
    }

    @Override
    public ApplicationError resolve(long id) {
        var sql = "update application_errors set resolved = true, updated_at = current_timestamp where id = ?";

        try (var conn = connection(); var ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);

            var count = ps.executeUpdate();
            checkState(count == 1, "Unable to resolve. No ApplicationError found with id %s", id);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }

        return getById(id).orElseThrow();
    }

    @Override
    public int resolveAllUnresolvedErrors() {
        var sql = "update application_errors set resolved = true, updated_at = current_timestamp where resolved = false";

        try (var conn = connection(); var stmt = conn.createStatement()) {
            return stmt.executeUpdate(sql);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Override
    public int deleteResolvedErrorsBefore(ZonedDateTime expirationDate) {
        var sql = "delete from application_errors where resolved = true and created_at < ?";
        return deleteResolvedErrorsBeforeUsingQuery(sql, expirationDate);
    }

    @Override
    public int deleteUnresolvedErrorsBefore(ZonedDateTime expirationDate) {
        var sql = "delete from application_errors where resolved = false and created_at < ?";
        return deleteResolvedErrorsBeforeUsingQuery(sql, expirationDate);
    }

    private int deleteResolvedErrorsBeforeUsingQuery(String sql, ZonedDateTime expirationDate) {
        try (var conn = connection(); var ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, timestampFromZonedDateTime(expirationDate));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    private Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

}
