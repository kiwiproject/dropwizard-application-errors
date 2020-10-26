package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static io.dropwizard.logback.shaded.guava.base.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao.checkPagingArgumentsAndCalculateZeroBasedOffset;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

// TODO Review code & javadocs...

/**
 * Implementation of {@link ApplicationErrorDao} that uses JDBI 3.
 */
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
public interface Jdbi3ApplicationErrorDao extends ApplicationErrorDao {

    @Override
    @SqlQuery("select * from application_errors where id = :id")
    @RegisterRowMapper(Jdbi3ApplicationErrorRowMapper.class)
    Optional<ApplicationError> getById(@Bind("id") long id);

    @Override
    default long count(ApplicationErrorStatus status) {
        switch (status) {
            case ALL:
                return countAllErrors();

            case RESOLVED:
                return countResolvedErrors();

            case UNRESOLVED:
                return countUnresolvedErrors();

            default:
                throw new IllegalArgumentException("Unknown error status value: " + status.name());
        }
    }

    @Override
    @SqlQuery("select count(id) from application_errors")
    long countAllErrors();

    @Override
    @SqlQuery("select count(id) from application_errors where resolved = true")
    long countResolvedErrors();

    @Override
    @SqlQuery("select count(id) from application_errors where resolved = false")
    long countUnresolvedErrors();

    @Override
    @SqlQuery("select count(id) from application_errors where resolved = false and updated_at >= :since")
    long countUnresolvedErrorsSince(@Bind("since") ZonedDateTime since);

    @Override
    @SqlQuery("select count(id) from application_errors" +
            " where resolved = false and updated_at >= :since and host_name = :host and ip_address = :ip")
    long countUnresolvedErrorsOnHostSince(@Bind("since") ZonedDateTime since,
                                          @Bind("host") String hostName,
                                          @Bind("ip") String ipAddress);

    @Override
    default List<ApplicationError> getAllErrors(int pageNumber, int pageSize) {
        int offset = checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize);
        return getAllErrorsInternal(pageSize, offset);
    }

    @Override
    default List<ApplicationError> getErrors(ApplicationErrorStatus status, int pageNumber, int pageSize) {
        checkNotNull(status, "status cannot be null");

        int offset = checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize);

        switch (status) {
            case ALL:
                return getAllErrorsInternal(pageSize, offset);

            case RESOLVED:
                return getErrorsInternal(true, pageSize, offset);

            case UNRESOLVED:
                return getErrorsInternal(false, pageSize, offset);

            default:
                throw new IllegalArgumentException("Unknown error status value: " + status.name());
        }
    }

    /**
     * @implNote The LIMIT and OFFSET fields must be in this order to work with H2 (as of version 1.4.200). This is
     * because they apparently adjusted their grammar parsing engine to expect limit before offset. Other engines (i.e.
     * Postgres) do not care about the order, but the LIMIT keyword is apparently just a non-standard keyword.
     * <p>
     * The SQL 2008 standard way to do this would be: OFFSET {@code offset} ROWS FETCH FIRST {@code limit} ROWS ONLY
     */
    @SqlQuery("select * from application_errors order by updated_at desc limit :pageSize offset :offset")
    @RegisterRowMapper(Jdbi3ApplicationErrorRowMapper.class)
    List<ApplicationError> getAllErrorsInternal(@Bind("pageSize") int pageSize, @Bind("offset") int offset);

    /**
     * @implNote The LIMIT and OFFSET fields must be in this order. For
     * further information, read the implementation
     * note on {@link #getAllErrorsInternal(int, int)}.
     * @see #getAllErrorsInternal(int, int)
     */
    @SqlQuery("select * from application_errors" +
            " where resolved = :resolved order by updated_at desc limit :pageSize offset :offset")
    @RegisterRowMapper(Jdbi3ApplicationErrorRowMapper.class)
    List<ApplicationError> getErrorsInternal(@Bind("resolved") boolean resolved,
                                             @Bind("pageSize") int pageSize,
                                             @Bind("offset") int offset);

    @Override
    @SqlQuery("select * from application_errors" +
            " where resolved = false and description = :desc order by updated_at desc")
    @RegisterRowMapper(Jdbi3ApplicationErrorRowMapper.class)
    List<ApplicationError> getUnresolvedErrorsByDescription(@Bind("desc") String description);

    @Override
    @SqlQuery("select * from application_errors" +
            " where resolved = false and description = :desc and host_name = :host order by updated_at desc")
    @RegisterRowMapper(Jdbi3ApplicationErrorRowMapper.class)
    List<ApplicationError> getUnresolvedErrorsByDescriptionAndHost(@Bind("desc") String description,
                                                                   @Bind("host") String hostName);

    @Override
    default long insertOrIncrementCount(ApplicationError error) {
        checkNotNull(error.getDescription(), "Error description cannot be null");

        var errors = getUnresolvedErrorsByDescriptionAndHost(error.getDescription(), error.getHostName());

        if (errors.isEmpty()) {
            return insertError(error);
        }

        var existingError = first(errors);
        incrementCount(existingError.getId());
        return existingError.getId();
    }

    /**
     * Inserts a new {@link ApplicationError}.
     * <p>
     * Notes:
     * <ul>
     * <li>Non-null values in {@code createdAt} and {@code updatedAt} will be
     * overridden with the current timestamp.</li>
     * <li>Resolved will always be set to false regardless of what the value
     * in 1@code newError} is.</li>
     * </ul>
     *
     * @param newError the new ApplicationError
     * @return the generated ID of the inserted error
     * @throws IllegalArgumentException if the error has an id
     */
    @Override
    default long insertError(ApplicationError newError) {
        checkArgument(isNull(newError.getId()), "Cannot insert an ApplicationError that has an id");
        return insertErrorInternal(newError);
    }

    @SqlUpdate("insert into application_errors" +
            " (description, exception_type, exception_message, exception_cause_type, exception_cause_message," +
            " stack_trace, host_name, ip_address, port)" +
            " values (:description, :exceptionType, :exceptionMessage, :exceptionCauseType, :exceptionCauseMessage," +
            " :stackTrace, :hostName, :ipAddress, :port)")
    @GetGeneratedKeys
    long insertErrorInternal(@BindBean ApplicationError newError);

    @Override
    default void incrementCount(long id) {
        int count = incrementCountInternal(id);
        checkState(count == 1, "Increment failed. No ApplicationError found with id %s", id);
    }

    @SqlUpdate("update application_errors" +
            " set num_times_occurred = num_times_occurred + 1, updated_at = current_timestamp" +
            " where id = :id")
    int incrementCountInternal(@Bind("id") long id);

    @Override
    default ApplicationError resolve(long id) {
        int count = resolveInternal(id);

        checkState(count == 1, "Unable to resolve. No ApplicationError found with id %s", id);

        return getById(id).orElseThrow();
    }

    @SqlUpdate("update application_errors set resolved = true, updated_at = current_timestamp where id = :id")
    int resolveInternal(@Bind("id") long id);

    @Override
    @SqlUpdate("update application_errors set resolved = true, updated_at = current_timestamp where resolved = false")
    int resolveAllUnresolvedErrors();

}
