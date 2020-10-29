package org.kiwiproject.dropwizard.error.dao;

import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.search.KiwiSearching;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Defines the contract for finding, creating, updating, and resolving {@link ApplicationError}s.
 */
public interface ApplicationErrorDao {

    /**
     * Find an error by id.
     *
     * @param id the unique error ID
     * @return an Optional that is either empty or contains the found ApplicationError
     */
    Optional<ApplicationError> getById(long id);

    /**
     * Count all errors having the specified status.
     *
     * @param status the status to filter by
     * @return the number of errors having the given status
     */
    long count(ApplicationErrorStatus status);

    /**
     * Count all resolved errors.
     *
     * @return the number of resolved errors
     */
    long countResolvedErrors();

    /**
     * Count all unresolved errors.
     *
     * @return the number of unresolved errors
     */
    long countUnresolvedErrors();

    /**
     * Count all errors.
     *
     * @return the total number of errors (resolved and unresolved)
     */
    long countAllErrors();

    /**
     * Count all unresolved errors since the given date/time.
     *
     * @param since the date after which to find unresolved errors
     * @return the number of unresolved errors since the given date
     */
    long countUnresolvedErrorsSince(ZonedDateTime since);

    /**
     * Count all unresolved errors since the given date/time that occurred on the specified host and IP address.
     *
     * @param since     the date after which to find unresolved errors
     * @param hostName  the host name to restrict errors to
     * @param ipAddress the IP to restrict errors to
     * @return the number of unresolved errors since the given date with the specified host and IP
     * @implNote Both the host name and IP address must match.
     */
    long countUnresolvedErrorsOnHostSince(ZonedDateTime since, String hostName, String ipAddress);

    /**
     * Paginate errors.
     *
     * @param pageNumber the one-based page number
     * @param pageSize   the page size
     * @return a list representing a single page of application errors
     */
    List<ApplicationError> getAllErrors(int pageNumber, int pageSize);

    /**
     * Paginate errors with the given status.
     *
     * @param status     the status to filter by
     * @param pageNumber the one-based page number
     * @param pageSize   the page size
     * @return a list representing a single page of application errors
     */
    List<ApplicationError> getErrors(ApplicationErrorStatus status, int pageNumber, int pageSize);

    /**
     * Find all errors that have the given description.
     *
     * @param description the <em>exact</em> description to find similar errors
     * @return a list of unresolved errors having the given description
     */
    List<ApplicationError> getUnresolvedErrorsByDescription(String description);

    /**
     * Find all errors that have the given description and which occurred on the given host.
     *
     * @param description the <em>exact</em> description to find similar errors
     * @param hostName    the host name to restrict errors to
     * @return a list of unresolved errors having the given description and host
     */
    List<ApplicationError> getUnresolvedErrorsByDescriptionAndHost(String description, String hostName);

    /**
     * Insert a new error, returning the generated ID of the saved error.
     *
     * @param newError the new ApplicationError to save
     * @return the unique ID of the new application error
     * @implNote Do not assume that {@code newError} is updated when this method is called. Changes to the object are
     * implementation-dependent. If you need an updated version, pass the returned long into {@link #getById(long)}.
     */
    long insertError(ApplicationError newError);

    /**
     * Increments the count of the error with the given ID, and updates the timestamp. Leaves ALL OTHER values
     * unchanged.
     *
     * @param id the unique ID of the ApplicationError to update
     */
    void incrementCount(long id);

    /**
     * Inserts a new error if no unresolved errors exist having the same description and host name. Otherwise,
     * increments the count of the existing error having the same description and host. Returns the error ID.
     * <p>
     * If the description and host match an existing error, only the count and timestamp are updated. ALL OTHER values
     * are left unchanged.
     *
     * @param error the ApplicationError to insert or update
     * @return the ID of the new or existing application error
     * @implNote Do not assume that {@code error} is updated when this method is called. Changes to the object are
     * implementation-dependent. If you need an updated version, pass the returned long into {@link #getById(long)}.
     * @see #insertError(ApplicationError)
     * @see #incrementCount(long)
     */
    long insertOrIncrementCount(ApplicationError error);

    /**
     * Resolves the error with the given ID. Returns the updated error instance.
     *
     * @param id the unique error ID to resolve
     * @return the updated ApplicationError instance
     * @apiNote Implementations should ensure that the returned instance has been properly updated with the new
     * resolution status and updated timestamp.
     */
    ApplicationError resolve(long id);

    /**
     * Resolves all unresolved errors.
     *
     * @return the number of errors that were resolved
     */
    int resolveAllUnresolvedErrors();

    /**
     * Check that the given page number (starting at one) and page size are valid.
     * <p>
     * Intended to be used by implementations that paginate based on a zero-based offset.
     *
     * @param pageNumber the page number to check, page number starts at one
     * @param pageSize   the page size
     * @return the zero-based offset to use in a query
     * @throws IllegalArgumentException if either argument is invalid
     * @see KiwiSearching#zeroBasedOffset(int, int)
     */
    static int checkPagingArgumentsAndCalculateZeroBasedOffset(int pageNumber, int pageSize) {
        return KiwiSearching.zeroBasedOffset(pageNumber, pageSize);
    }
}
