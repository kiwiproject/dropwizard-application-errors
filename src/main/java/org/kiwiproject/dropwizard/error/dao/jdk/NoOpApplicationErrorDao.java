package org.kiwiproject.dropwizard.error.dao.jdk;

import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * An implementation of {@link ApplicationErrorDao} that does nothing, i.e. is a "no-op".
 * <p>
 * This might be useful in unit tests or when another class requires you to provide an {@link ApplicationErrorDao}
 * but you don't care about application errors and want them to be a "no-op" operation.
 * <p>
 * Methods that return primitive types return the default value, e.g. zero for {@code long}, while methods that
 * return reference types return a reasonable, non-null value, e.g. an empty list or optional.
 */
public class NoOpApplicationErrorDao implements ApplicationErrorDao {

    @Override
    public Optional<ApplicationError> getById(long id) {
        return Optional.empty();
    }

    @Override
    public long countResolvedErrors() {
        return 0;
    }

    @Override
    public long countUnresolvedErrors() {
        return 0;
    }

    @Override
    public long countAllErrors() {
        return 0;
    }

    @Override
    public long countUnresolvedErrorsSince(ZonedDateTime since) {
        return 0;
    }

    @Override
    public long countUnresolvedErrorsOnHostSince(ZonedDateTime since, String hostName, String ipAddress) {
        return 0;
    }

    @Override
    public List<ApplicationError> getAllErrors(int pageNumber, int pageSize) {
        return List.of();
    }

    @Override
    public List<ApplicationError> getErrors(ApplicationErrorStatus status, int pageNumber, int pageSize) {
        return List.of();
    }

    @Override
    public List<ApplicationError> getUnresolvedErrorsByDescription(String description) {
        return List.of();
    }

    @Override
    public List<ApplicationError> getUnresolvedErrorsByDescriptionAndHost(String description, String hostName) {
        return List.of();
    }

    @Override
    public long insertError(ApplicationError newError) {
        return 0;
    }

    @Override
    public void incrementCount(long id) {
        // no-op
    }

    @Override
    public long insertOrIncrementCount(ApplicationError error) {
        return 0;
    }

    /**
     * @implNote Returns a fake, non-null {@link ApplicationError}, but whose values may be null.
     */
    @Override
    public ApplicationError resolve(long id) {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        return ApplicationError.builder()
                .id(id)
                .createdAt(now)
                .updatedAt(now)
                .description("Fake error")
                .numTimesOccurred(1)
                .resolved(true)
                .hostName("localhost")
                .ipAddress("127.0.0.1")
                .port(8080)
                .build();
    }

    @Override
    public int resolveAllUnresolvedErrors() {
        return 0;
    }

    @Override
    public int deleteResolvedErrorsBefore(ZonedDateTime expirationDate) {
        return 0;
    }

    @Override
    public int deleteUnresolvedErrorsBefore(ZonedDateTime expirationDate) {
        return 0;
    }
}
