package org.kiwiproject.dropwizard.error.dao.jdk;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentIsNull;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao.checkPagingArgumentsAndCalculateZeroBasedOffset;

import com.google.common.annotations.VisibleForTesting;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Implementation of {@link ApplicationErrorDao} that uses a {@link ConcurrentMap} to store
 * application errors in-memory.
 */
public class ConcurrentMapApplicationErrorDao implements ApplicationErrorDao {

    private static final Comparator<ApplicationError> UPDATED_AT_DESCENDING =
            Comparator.comparing(ApplicationError::getUpdatedAt).reversed();

    private final AtomicLong currentId;

    @VisibleForTesting final ConcurrentMap<Long, ApplicationError> errors;

    public ConcurrentMapApplicationErrorDao() {
        currentId = new AtomicLong();
        errors = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<ApplicationError> getById(long id) {
        return Optional.ofNullable(errors.get(id));
    }

    @Override
    public long countResolvedErrors() {
        return errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.RESOLVED))
                .count();
    }

    @Override
    public long countUnresolvedErrors() {
        return errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.UNRESOLVED))
                .count();
    }

    @Override
    public long countAllErrors() {
        return errors.size();
    }

    @Override
    public long countUnresolvedErrorsSince(ZonedDateTime since) {
        return errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.UNRESOLVED))
                .filter(error -> error.getUpdatedAt().isAfter(since))
                .count();
    }

    @Override
    public long countUnresolvedErrorsOnHostSince(ZonedDateTime since, String hostName, String ipAddress) {
        return errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.UNRESOLVED))
                .filter(error -> error.getHostName().equals(hostName))
                .filter(error -> error.getIpAddress().equals(ipAddress))
                .filter(error -> error.getUpdatedAt().isAfter(since))
                .count();
    }

    @Override
    public List<ApplicationError> getAllErrors(int pageNumber, int pageSize) {
        return getErrors(ApplicationErrorStatus.ALL, pageNumber, pageSize);
    }

    @Override
    public List<ApplicationError> getErrors(ApplicationErrorStatus status, int pageNumber, int pageSize) {
        int offset = checkPagingArgumentsAndCalculateZeroBasedOffset(pageNumber, pageSize);

        return errors.values()
                .stream()
                .filter(byStatus(status))
                .sorted(UPDATED_AT_DESCENDING)
                .skip(offset)
                .limit(pageSize)
                .collect(toList());
    }

    @Override
    public List<ApplicationError> getUnresolvedErrorsByDescription(String description) {
        return errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.UNRESOLVED))
                .filter(error -> error.getDescription().equals(description))
                .collect(toList());
    }

    @Override
    public List<ApplicationError> getUnresolvedErrorsByDescriptionAndHost(String description, String hostName) {
        return errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.UNRESOLVED))
                .filter(error -> error.getHostName().equals(hostName))
                .filter(error -> error.getDescription().equals(description))
                .collect(toList());
    }

    @Override
    public long insertError(ApplicationError newError) {
        checkArgumentIsNull(newError.getId(), "Cannot insert an ApplicationError that has an id");

        var newId = currentId.incrementAndGet();
        var errorWithId = newError.withId(newId);

        // Ensure it is unresolved
        var unresolvedError = errorWithId.isResolved() ?
                updateWith(errorWithId, errorWithId.getNumTimesOccurred(), false) : errorWithId;
        errors.put(newId, unresolvedError);

        return newId;
    }

    @Override
    public void incrementCount(long id) {
        var error = errors.get(id);
        checkState(nonNull(error), "Unable to increment count. No ApplicationError found with id %s", id);

        var updatedError = incrementNumTimesOccurred(error);
        errors.put(id, updatedError);
    }

    @Override
    public long insertOrIncrementCount(ApplicationError error) {
        var id = error.getId();
        if (isNull(id)) {
            return insertError(error);
        }

        incrementCount(id);
        return id;
    }

    @Override
    public ApplicationError resolve(long id) {
        var error = errors.get(id);

        checkState(nonNull(error), "Unable to resolve. No ApplicationError found with id %s", id);

        var resolvedError = resolve(error);
        errors.put(id, resolvedError);

        return resolvedError;
    }

    @Override
    public int resolveAllUnresolvedErrors() {
        var count = errors.values()
                .stream()
                .filter(byStatus(ApplicationErrorStatus.UNRESOLVED))
                .map(applicationError -> resolve(applicationError.getId()))
                .count();

        return Math.toIntExact(count);
    }

    private static Predicate<ApplicationError> byStatus(ApplicationErrorStatus status) {
        switch (status) {
            case ALL:
                return applicationError -> true;

            case RESOLVED:
                return ApplicationError::isResolved;

            case UNRESOLVED:
                return not(ApplicationError::isResolved);

            default:
                throw new IllegalStateException("unknown status: " + status);
        }
    }

    @Override
    public int deleteResolvedErrorsBefore(ZonedDateTime expirationDate) {
        return deleteErrorsWithStatusAndBefore(ApplicationErrorStatus.RESOLVED, expirationDate);
    }

    @Override
    public int deleteUnresolvedErrorsBefore(ZonedDateTime expirationDate) {
        return deleteErrorsWithStatusAndBefore(ApplicationErrorStatus.UNRESOLVED, expirationDate);
    }

    private int deleteErrorsWithStatusAndBefore(ApplicationErrorStatus status, ZonedDateTime referenceDate) {
        checkArgument(isResolvedOrUnresolved(status));

        // TODO Is there a better way to count than this? (removeIf returns a boolean, which isn't useful here)
        var removeCount = new AtomicInteger();
        errors.entrySet().removeIf(entry -> matchesStatusAndBeforeDate(entry, status, referenceDate, removeCount));

        return removeCount.get();
    }

    private static boolean isResolvedOrUnresolved(ApplicationErrorStatus status) {
        return status == ApplicationErrorStatus.RESOLVED || status == ApplicationErrorStatus.UNRESOLVED;
    }

    private static boolean matchesStatusAndBeforeDate(Map.Entry<Long, ApplicationError> entry,
                                                      ApplicationErrorStatus status,
                                                      ZonedDateTime referenceDate,
                                                      AtomicInteger removeCount) {
        var error = entry.getValue();
        var shouldRemove = matchesStatus(error, status) && error.getCreatedAt().isBefore(referenceDate);
        if (shouldRemove) {
            removeCount.incrementAndGet();
        }
        return shouldRemove;
    }

    private static boolean matchesStatus(ApplicationError error, ApplicationErrorStatus status) {
        return (status == ApplicationErrorStatus.RESOLVED) == error.isResolved();
    }

    private static ApplicationError resolve(ApplicationError original) {
        return updateWith(original, original.getNumTimesOccurred(), true);
    }

    private static ApplicationError incrementNumTimesOccurred(ApplicationError original) {
        var newNumTimesOccurred = 1 + original.getNumTimesOccurred();
        return updateWith(original, newNumTimesOccurred, original.isResolved());
    }

    private static ApplicationError updateWith(ApplicationError original, int numTimesOccurred, boolean resolved) {
        checkArgumentNotNull(original, "ApplicationError to update must not be null");

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var createdAt = isNull(original.getCreatedAt()) ? now : original.getCreatedAt();

        return ApplicationError.builder()
                .id(original.getId())
                .createdAt(createdAt)
                .updatedAt(now)
                .numTimesOccurred(numTimesOccurred)
                .description(original.getDescription())
                .exceptionType(original.getExceptionType())
                .exceptionMessage(original.getExceptionMessage())
                .exceptionCauseType(original.getExceptionCauseType())
                .exceptionCauseMessage(original.getExceptionCauseMessage())
                .stackTrace(original.getStackTrace())
                .resolved(resolved)
                .hostName(original.getHostName())
                .ipAddress(original.getIpAddress())
                .port(original.getPort())
                .build();
    }
}
