package org.kiwiproject.dropwizard.error.dao;

import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.test.util.DateTimeTestHelper.assertTimeDifferenceWithinTolerance;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.base.DefaultEnvironment;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.ApplicationError.Resolved;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Base test class for testing {@link ApplicationErrorDao} implementations.
 */
@ExtendWith(ApplicationErrorExtension.class)
@ExtendWith(SoftAssertionsExtension.class)
@Slf4j
public abstract class AbstractApplicationErrorDaoTest<T extends ApplicationErrorDao> {

    private T errorDao;
    private ThreadLocalRandom random;
    private String description;
    private Throwable throwable;
    private Resolved resolved;
    private String hostName;
    private String ipAddress;
    private int port;

    @BeforeEach
    final void baseSetUp() throws UnknownHostException {
        errorDao = getErrorDao();

        random = ThreadLocalRandom.current();
        description = "Something terrible has happened...millions of voices crying out in terror, and then silenced.";
        var exceptionMessage = "I/O error occurred";
        var exceptionCauseMessage = "Alderaan seems to be missing...";
        throwable = newThrowable(exceptionMessage, exceptionCauseMessage);
        resolved = Resolved.NO;
        var localHost = InetAddress.getLocalHost();
        hostName = localHost.getHostName();
        ipAddress = localHost.getHostAddress();
        port = 8080;
    }

    /**
     * Implementations should call this in their own {@link BeforeEach} method after they have completed
     * their own initialization to verify no application errors exist before each test. If implementations
     * do not call this method in a {@link BeforeEach} method, the tests may still work if the test
     * implementation clears out any application errors created during each test.
     * <p>
     * Ideally this would be called in the {@link #baseSetUp()} method above, but there is no way for this
     * base class to determine whether {@link #countApplicationErrors()} will succeed, i.e. subclasses may
     * need to perform additional setup which this class does not - and cannot - know about.
     */
    protected void countAndVerifyNoApplicationErrorsExist() {
        var count = countApplicationErrors();
        verify(count == 0, "No application errors should exist but found %s!", count);
    }

    /**
     * This should return the same instance every time it is called.
     */
    protected abstract T getErrorDao();

    /**
     * Insert the given {@link ApplicationError} into the test data store.
     * <p>
     * Depending on the implementation, this may need to use the {@link ApplicationErrorDao}
     * directly to perform the insert.
     */
    protected abstract long insertApplicationError(ApplicationError error);

    /**
     * Count the number of application errors in the test data store.
     * <p>
     * Depending on the implementation, this may need to use the {@link ApplicationErrorDao}
     * directly to perform the count.
     */
    protected abstract long countApplicationErrors();

    /**
     * Get the {@link ApplicationError} with the given identifier.
     * <p>
     * Depending on the implementation, this may need to use the {@link ApplicationErrorDao}
     * directly to perform the retrieval.
     *
     * @throws IllegalStateException if no {@link ApplicationError} was found
     */
    protected abstract ApplicationError getErrorOrThrow(long id);

    @Nested
    class GetById {

        @Test
        void shouldReturnEmptyOptional_WhenDoesNotExist() {
            var errorOptional = errorDao.getById(Long.MIN_VALUE);

            assertThat(errorOptional).isEmpty();
        }

        @Test
        void shouldReturnOptionalContainingApplicationError_WhenExists() {
            var id = insertApplicationError(defaultApplicationError());

            var errorOptional = errorDao.getById(id);
            var error = errorOptional.orElseThrow();
            assertThat(error.getId()).isEqualTo(id);
        }
    }

    @Nested
    class InsertError {

        @Test
        void shouldThrowIllegalArgumentException_WhenErrorContainsAnId() {
            var error = ApplicationError.builder()
                    .id(42L)
                    .build();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> errorDao.insertError(error))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .withMessage("Cannot insert an ApplicationError that has an id");
        }

        @Test
        void shouldAlwaysSetResolveToFalse() {
            var error = ApplicationError.newError("A test error", Resolved.YES, null);
            assertThat(error.isResolved()).isTrue();

            var id = errorDao.insertError(error);

            var retrievedError = getErrorOrThrow(id);
            assertThat(retrievedError.isResolved()).isFalse();
        }

        @Test
        void shouldInsertNewApplicationErrorRecord(SoftAssertions softly) {
            var beforeInsert = ZonedDateTime.now(ZoneOffset.UTC);
            var id = errorDao.insertError(defaultApplicationError());

            var retrievedError = getErrorOrThrow(id);

            softly.assertThat(retrievedError.getId()).isEqualTo(id);
            var oneSecondInMillis = 1_000;
            assertTimeDifferenceWithinTolerance(softly, "createdAt", beforeInsert, retrievedError.getCreatedAt(), oneSecondInMillis);
            assertTimeDifferenceWithinTolerance(softly, "updatedAt", beforeInsert, retrievedError.getUpdatedAt(), oneSecondInMillis);
            if (!softly.wasSuccess()) {
                LOG.warn("When the time difference assertions fail, it is probably due to a time zone difference." +
                                " This library currently requires UTC for the JVM and database." +
                                " The JVM system default time zone is {}." +
                                " The database time zone is most likely using the same default time zone.",
                        ZoneId.systemDefault());
            }
            softly.assertThat(retrievedError.getDescription()).isEqualTo(description);
            softly.assertThat(retrievedError.getExceptionType()).isEqualTo(throwable.getClass().getName());
            softly.assertThat(retrievedError.getExceptionMessage()).isEqualTo(throwable.getMessage());
            softly.assertThat(retrievedError.getExceptionCauseType()).isEqualTo(throwable.getCause().getClass().getName());
            softly.assertThat(retrievedError.getExceptionCauseMessage()).isEqualTo(throwable.getCause().getMessage());
            softly.assertThat(retrievedError.getStackTrace()).isEqualTo(ExceptionUtils.getStackTrace(throwable));
            softly.assertThat(retrievedError.isResolved()).isFalse();
            softly.assertThat(retrievedError.getHostName()).isEqualTo(hostName);
            softly.assertThat(retrievedError.getIpAddress()).isEqualTo(ipAddress);
            softly.assertThat(retrievedError.getPort()).isEqualTo(port);
        }
    }

    @Nested
    class Resolve {

        @Test
        void shouldSetResolvedToTrue() {
            var unresolvedError = defaultApplicationError();
            assertThat(unresolvedError.isResolved()).isFalse();

            var id = insertApplicationError(unresolvedError);

            var resolvedError = errorDao.resolve(id);
            assertThat(resolvedError.getId()).isEqualTo(id);
            assertThat(resolvedError.isResolved()).isTrue();

            var result = getErrorOrThrow(id);
            assertThat(result.isResolved()).isTrue();
        }

        @Test
        void shouldThrowIllegalStateException_WhenApplicationErrorDoesNotExist() {
            long id = Long.MIN_VALUE;

            assertThatIllegalStateException()
                    .isThrownBy(() -> errorDao.resolve(id))
                    .isExactlyInstanceOf(IllegalStateException.class)
                    .withMessage("Unable to resolve. No ApplicationError found with id %d", id);
        }
    }

    @Nested
    class ResolveAllUnresolvedErrors {

        @Test
        void shouldResolveUnresolvedErrors() {
            var numUnresolved = 25;
            IntStream.rangeClosed(1, numUnresolved)
                    .forEach(ignored -> insertApplicationError(randomUnresolvedApplicationError()));

            var numUnresolvedErrors = errorDao.countUnresolvedErrors();
            assertThat(numUnresolvedErrors).isEqualTo(numUnresolved);

            var count = errorDao.resolveAllUnresolvedErrors();
            assertThat(count).isEqualTo(numUnresolvedErrors);
            assertThat(errorDao.countUnresolvedErrors()).isZero();
        }

        @Test
        void shouldChangeOnlyUnresolvedErrors() {
            var resolvedErrorId1 = insertApplicationError(randomResolvedApplicationError());
            var resolvedError1OriginalUpdatedAt = getErrorOrThrow(resolvedErrorId1).getUpdatedAtMillis();
            var resolvedErrorId2 = insertApplicationError(randomResolvedApplicationError());
            var resolvedError2OriginalUpdatedAt = getErrorOrThrow(resolvedErrorId2).getUpdatedAtMillis();

            var unresolvedErrorId1 = insertApplicationError(randomUnresolvedApplicationError());
            var unresolvedErrorId2 = insertApplicationError(randomUnresolvedApplicationError());

            var count = errorDao.resolveAllUnresolvedErrors();
            assertThat(count).isEqualTo(2);

            assertThat(getErrorOrThrow(unresolvedErrorId1).isResolved()).isTrue();
            assertThat(getErrorOrThrow(unresolvedErrorId2).isResolved()).isTrue();

            var resolvedError1NewUpdatedAt = getErrorOrThrow(resolvedErrorId1).getUpdatedAtMillis();
            var resolvedError2NewUpdatedAt = getErrorOrThrow(resolvedErrorId2).getUpdatedAtMillis();

            assertThat(resolvedError1NewUpdatedAt).isEqualTo(resolvedError1OriginalUpdatedAt);
            assertThat(resolvedError2NewUpdatedAt).isEqualTo(resolvedError2OriginalUpdatedAt);
        }
    }

    @Test
    void shouldDeleteResolvedErrors_BeforeReferenceDate() {
        var resolvedId1 = insertApplicationError(randomResolvedApplicationError());
        var unresolvedId1 = insertApplicationError(randomUnresolvedApplicationError());

        // Ensure the next createdAt is AFTER previous ones as long as
        // the database timestamp precision is at least milliseconds
        sleep5ms();

        var timeInBetween = ZonedDateTime.now(ZoneOffset.UTC);
        var resolvedId2 = insertApplicationError(randomResolvedApplicationError());

        var count = errorDao.deleteResolvedErrorsBefore(timeInBetween);

        assertThat(count).isOne();
        assertThat(errorDao.getById(resolvedId1)).isEmpty();
        assertThat(errorDao.getById(unresolvedId1)).isPresent();
        assertThat(errorDao.getById(resolvedId2)).isPresent();
    }

    @Test
    void shouldDeleteUnresolvedErrors_BeforeReferenceDate() {
        var resolvedId1 = insertApplicationError(randomResolvedApplicationError());
        var unresolvedId1 = insertApplicationError(randomUnresolvedApplicationError());

        // Ensure the createdAt is AFTER previous ones as long as
        // the database timestamp precision is at least milliseconds
        sleep5ms();

        var timeInBetween = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedId2 = insertApplicationError(randomUnresolvedApplicationError());

        var count = errorDao.deleteUnresolvedErrorsBefore(timeInBetween);

        assertThat(count).isOne();
        assertThat(errorDao.getById(resolvedId1)).isPresent();
        assertThat(errorDao.getById(unresolvedId1)).isEmpty();
        assertThat(errorDao.getById(unresolvedId2)).isPresent();
    }

    private static void sleep5ms() {
        new DefaultEnvironment().sleepQuietly(5);
    }

    @Test
    void shouldCount_AllErrors() {
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());

        assertThat(errorDao.count(ApplicationErrorStatus.ALL)).isEqualTo(5);
        assertThat(errorDao.countAllErrors()).isEqualTo(5);
    }

    @Test
    void shouldCount_UnresolvedErrors() {
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());

        assertThat(errorDao.count(ApplicationErrorStatus.UNRESOLVED)).isEqualTo(2);
        assertThat(errorDao.countUnresolvedErrors()).isEqualTo(2);
    }

    @Test
    void shouldCount_ResolvedErrors() {
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());

        assertThat(errorDao.count(ApplicationErrorStatus.RESOLVED)).isEqualTo(3);
        assertThat(errorDao.countResolvedErrors()).isEqualTo(3);
    }

    @Nested
    class CountUnresolvedErrorsSince {

        @Test
        void shouldReturnZero_WhenNoneSinceGivenDate() {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(35), Resolved.NO);
            insertApplicationError(unresolvedError);

            var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), Resolved.YES);
            insertApplicationError(resolvedError);

            var errorsSince = errorDao.countUnresolvedErrorsSince(now.minusMinutes(30));
            assertThat(errorsSince).isZero();
        }

        @Test
        void shouldReturnOne_WhenOneSinceGivenDate() {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), Resolved.NO);
            insertApplicationError(unresolvedError);

            var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), Resolved.YES);
            insertApplicationError(resolvedError);

            long errorsSince = errorDao.countUnresolvedErrorsSince(now.minusMinutes(30));
            assertThat(errorsSince).isOne();
        }

        @Test
        void shouldReturnTwo_WhenTwoSinceGivenDate() {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var unresolvedError1 = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), Resolved.NO);
            insertApplicationError(unresolvedError1);

            var unresolvedError2 = newApplicationErrorWithUpdatedDate(now.minusMinutes(15), Resolved.NO);
            insertApplicationError(unresolvedError2);

            var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), Resolved.YES);
            insertApplicationError(resolvedError);

            long errorsSince = errorDao.countUnresolvedErrorsSince(now.minusMinutes(30));
            assertThat(errorsSince).isEqualTo(2);
        }
    }

    @Nested
    class CountUnresolvedErrorsOnHostSince {

        @Test
        void shouldReturnZero_WhenNoneSinceGivenDate_OnGivenHost() {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(35), Resolved.NO);
            insertApplicationError(unresolvedError);

            var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), Resolved.YES);
            insertApplicationError(resolvedError);

            long errorsSince = errorDao.countUnresolvedErrorsOnHostSince(now.minusMinutes(30), "another.host", "10.10.1.1");
            assertThat(errorsSince).isZero();
        }

        @Test
        void shouldReturnOne_WhenOneSinceGivenDate_OnGiveHost() {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), Resolved.NO);
            insertApplicationError(unresolvedError);

            var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), Resolved.YES);
            insertApplicationError(resolvedError);

            long errorsSince = errorDao.countUnresolvedErrorsOnHostSince(now.minusMinutes(30), hostName, ipAddress);
            assertThat(errorsSince).isOne();
        }

        @Test
        void shouldReturnTwo_WhenTwoSinceGivenDate_OnGiveHost() {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            var unresolvedError1 = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), Resolved.NO);
            insertApplicationError(unresolvedError1);

            var unresolvedError2 = newApplicationErrorWithUpdatedDate(now.minusMinutes(15), Resolved.NO);
            insertApplicationError(unresolvedError2);

            var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), Resolved.YES);
            insertApplicationError(resolvedError);

            long errorsSince = errorDao.countUnresolvedErrorsOnHostSince(now.minusMinutes(30), hostName, ipAddress);
            assertThat(errorsSince).isEqualTo(2);
        }
    }

    private ApplicationError newApplicationErrorWithUpdatedDate(ZonedDateTime dateTime, Resolved resolved) {
        return ApplicationError.builder()
                .createdAt(dateTime)
                .updatedAt(dateTime)
                .resolved(resolved.toBoolean())
                .description("test error at " + dateTime)
                .hostName(hostName)
                .ipAddress(ipAddress)
                .port(port)
                .build();
    }

    @Nested
    class GetErrors {

        @Test
        void shouldThrowIllegalArgumentException_WhenGivenInvalidPageNumber() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 0, 100))
                    .withMessage("pageNumber starts at 1");
        }

        @Test
        void shouldThrowIllegalArgumentException_WhenGivenInvalidPageSize() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 0))
                    .withMessage("pageSize must be at least 1");
        }

        @Test
        void shouldGetUnresolvedErrors() {
            var resolvedIds = insertErrorsWithResolvedAs(5, Resolved.YES);
            var unresolvedIds = insertErrorsWithResolvedAs(7, Resolved.NO);

            var unresolvedErrors = errorDao.getErrors(ApplicationErrorStatus.UNRESOLVED, 1, 100);
            assertThat(unresolvedErrors)
                    .extracting("id")
                    .containsAll(unresolvedIds)
                    .doesNotContainAnyElementsOf(resolvedIds);
        }

        @Test
        void shouldGetResolvedErrors() {
            var resolvedIds = insertErrorsWithResolvedAs(5, Resolved.YES);
            var unresolvedIds = insertErrorsWithResolvedAs(7, Resolved.NO);

            var unresolvedErrors = errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 100);
            assertThat(unresolvedErrors)
                    .extracting("id")
                    .containsAll(resolvedIds)
                    .doesNotContainAnyElementsOf(unresolvedIds);
        }

        @Test
        void shouldGetResolvedError() {
            insertErrorsWithResolvedAs(5, Resolved.YES);
            insertErrorsWithResolvedAs(3, Resolved.NO);

            // Ensure the next createdAt is AFTER previous ones as long as
            // the database timestamp precision is at least milliseconds
            // (this ensures the most recently resolved error is after the others)
            sleep5ms();

            var desc = description + random.nextInt(100_000);
            var resolvedError = newApplicationError(desc, Resolved.YES);
            var resolvedId = insertApplicationError(resolvedError);

            var errors = errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 10);
            assertThat(errors).hasSize(6);

            var mostRecentError = first(errors);
            assertThat(mostRecentError.getId()).isEqualTo(resolvedId);
        }

        @Test
        void shouldGetAllErrors() {
            var resolvedIds = insertErrorsWithResolvedAs(5, Resolved.YES);
            var unresolvedIds = insertErrorsWithResolvedAs(7, Resolved.NO);
            var expectedSize = resolvedIds.size() + unresolvedIds.size();

            var allErrors = errorDao.getErrors(ApplicationErrorStatus.ALL, 1, 100);
            assertThat(allErrors).extracting("id")
                    .hasSize(expectedSize)
                    .containsAll(resolvedIds)
                    .containsAll(unresolvedIds);
        }
    }

    @Nested
    class GetAllErrors {

        @Test
        void shouldThrowIllegalArgumentException_WhenGivenInvalidPageNumber() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> errorDao.getAllErrors(0, 100))
                    .withMessage("pageNumber starts at 1");
        }

        @Test
        void shouldThrowIllegalArgumentException_WhenGivenInvalidPageSize() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> errorDao.getAllErrors(1, 0))
                    .withMessage("pageSize must be at least 1");
        }

        @Test
        void shouldGetAllErrors() {
            var resolvedIds = insertErrorsWithResolvedAs(5, Resolved.YES);
            var unresolvedIds = insertErrorsWithResolvedAs(7, Resolved.NO);
            var expectedSize = resolvedIds.size() + unresolvedIds.size();

            var allErrors = errorDao.getAllErrors(1, 100);
            assertThat(allErrors).extracting("id")
                    .hasSize(expectedSize)
                    .containsAll(resolvedIds)
                    .containsAll(unresolvedIds);
        }
    }

    @Nested
    class GetUnresolvedErrorsByDescription {

        @Test
        void shouldFindOneError_WhenOnlyOneWithGivenDescription() {
            insertErrorsWithResolvedAs(5, Resolved.YES);
            insertErrorsWithResolvedAs(7, Resolved.NO);

            var desc = description + random.nextInt(100_000);
            var unresolvedError = newApplicationError(desc, Resolved.NO);
            long unresolvedId = insertApplicationError(unresolvedError);

            var errors = errorDao.getUnresolvedErrorsByDescription(desc);
            assertThat(errors)
                    .hasSize(1)
                    .extracting("id")
                    .containsOnly(unresolvedId);
        }

        @Test
        void shouldFindAllErrors_WhenMoreThanOneWithSameDescription() {
            insertErrorsWithResolvedAs(5, Resolved.YES);
            insertErrorsWithResolvedAs(7, Resolved.NO);

            var desc = description + random.nextInt(100_000);
            var unresolvedError1 = newApplicationError(desc, Resolved.NO);
            var unresolvedId1 = insertApplicationError(unresolvedError1);

            var unresolvedError2 = newApplicationError(desc, Resolved.NO);
            var unresolvedId2 = insertApplicationError(unresolvedError2);

            var errors = errorDao.getUnresolvedErrorsByDescription(desc);
            assertThat(errors)
                    .hasSize(2)
                    .extracting("id")
                    .containsOnly(unresolvedId1, unresolvedId2);
        }
    }

    @Nested
    class GetUnresolvedErrorsByDescriptionAndHost {

        @Test
        void testGetUnresolvedErrorsByDescriptionAndHost() {
            var host1 = "my-host-1";
            var host2 = "my-host-2";

            insertErrorsWithResolvedAs(5, Resolved.YES, host1);
            insertErrorsWithResolvedAs(3, Resolved.YES, host2);
            insertErrorsWithResolvedAs(7, Resolved.NO, host1);
            insertErrorsWithResolvedAs(6, Resolved.NO, host2);

            var desc = description + random.nextInt(100_000);
            var unresolvedError = newApplicationError(desc, Resolved.NO, host2);
            var unresolvedId = insertApplicationError(unresolvedError);

            var errors = errorDao.getUnresolvedErrorsByDescriptionAndHost(desc, host2);
            assertThat(errors)
                    .hasSize(1)
                    .extracting("id")
                    .containsOnly(unresolvedId);

            assertThat(errorDao.getUnresolvedErrorsByDescriptionAndHost(desc, host1)).isEmpty();
        }

        @Test
        void testGetUnresolvedErrorsByDescriptionAndHost_WhenMoreThanOneWithSameDescription_OnDifferentHosts() {
            var host1 = "my-host-1";
            var host2 = "my-host-2";

            insertErrorsWithResolvedAs(5, Resolved.YES, host1);
            insertErrorsWithResolvedAs(3, Resolved.YES, host2);
            insertErrorsWithResolvedAs(7, Resolved.NO, host1);
            insertErrorsWithResolvedAs(6, Resolved.NO, host2);

            var desc = description + random.nextInt(100_000);
            var unresolvedError1 = newApplicationError(desc, Resolved.NO, host1);
            long unresolvedId1 = insertApplicationError(unresolvedError1);

            var unresolvedError2 = newApplicationError(desc, Resolved.NO, host2);
            long unresolvedId2 = insertApplicationError(unresolvedError2);

            var errors1 = errorDao.getUnresolvedErrorsByDescriptionAndHost(desc, host1);
            assertThat(errors1)
                    .extracting("id")
                    .containsExactly(unresolvedId1);

            var errors2 = errorDao.getUnresolvedErrorsByDescriptionAndHost(desc, host2);
            assertThat(errors2)
                    .extracting("id")
                    .containsExactly(unresolvedId2);
        }
    }

    @Nested
    class IncrementCount {

        @Test
        void shouldIncrementCountByOne_WhenUpdatesOneRow(SoftAssertions softly) {
            var unresolvedError = newApplicationError(description, Resolved.NO);
            var id = insertApplicationError(unresolvedError);

            softlyAssertNumTimesOccurred(softly, id, 1);
            errorDao.incrementCount(id);
            softlyAssertNumTimesOccurred(softly, id, 2);
        }

        @Test
        void shouldThrowIllegalStateException_WhenErrorWithIdDoesNotExist() {
            var id = Long.MIN_VALUE;
            assertThatIllegalStateException()
                    .isThrownBy(() -> errorDao.incrementCount(id))
                    .withMessage("Unable to increment count. No ApplicationError found with id " + id);
        }
    }

    @Nested
    class InsertOrIncrementCount {

        @Test
        void shouldInsertNew_WhenErrorDoesNotExist(SoftAssertions softly) {
            var error = randomUnresolvedApplicationError();
            var id = errorDao.insertOrIncrementCount(error);

            var result = getErrorOrThrow(id);
            softly.assertThat(result.getNumTimesOccurred()).isOne();
            softly.assertThat(result.getDescription()).isEqualTo(error.getDescription());
        }

        @Test
        void shouldIncrementCount_WhenErrorExists(SoftAssertions softly) {
            var id = insertApplicationError(randomUnresolvedApplicationError());
            softlyAssertNumTimesOccurred(softly, id, 1);

            var error = getErrorOrThrow(id);

            long idOfIncremented = errorDao.insertOrIncrementCount(error);
            assertThat(idOfIncremented).isEqualTo(id);

            var result = getErrorOrThrow(id);
            softly.assertThat(result.getNumTimesOccurred()).isEqualTo(2);
            softly.assertThat(result.getDescription()).isEqualTo(error.getDescription());
        }

        @Test
        void shouldNotChangeOtherFields_OnExistingError(SoftAssertions softly) {
            var description = "uh oh uh oh";
            var id = insertApplicationError(ApplicationError.newUnresolvedError(description));
            softlyAssertNumTimesOccurred(softly, id, 1);

            var originalError = getErrorOrThrow(id);
            var idOfIncremented = errorDao.insertOrIncrementCount(originalError);
            assertThat(idOfIncremented).isEqualTo(id);

            // NOTE:
            // Due to the way the transactions work during tests (i.e. transaction per test auto rolled back), and the
            // fact that Postgres in particular always returns the **same** value from current_timestamp during a single
            // transaction, we cannot verify that the updatedAt value changes in this test. Inspect the SQL update in
            // the DAO to verify updated_at is updated.

            var result = getErrorOrThrow(id);
            softly.assertThat(result).usingRecursiveComparison().ignoringFields("numTimesOccurred", "updatedAt").isEqualTo(originalError);
            softly.assertThat(result.getNumTimesOccurred()).isEqualTo(2);
        }
    }

    private void softlyAssertNumTimesOccurred(SoftAssertions softly, long id, int numTimesOccurred) {
        var applicationError = getErrorOrThrow(id);
        softly.assertThat(applicationError.getNumTimesOccurred()).isEqualTo(numTimesOccurred);
    }

    private List<Long> insertErrorsWithResolvedAs(int count, Resolved resolved) {
        return insertErrorsWithResolvedAs(count, resolved, hostName);
    }

    private List<Long> insertErrorsWithResolvedAs(int count, Resolved resolved, String host) {
        return IntStream.rangeClosed(1, count).mapToObj(value ->
                        insertApplicationError(randomApplicationErrorWithResolvedAndHost(resolved, host)))
                .collect(toList());
    }

    private ApplicationError defaultApplicationError() {
        return ApplicationError.newError(description, resolved, hostName, ipAddress, port, throwable);
    }

    private ApplicationError randomResolvedApplicationError() {
        return randomResolvedApplicationErrorOnHost(hostName);
    }

    private ApplicationError randomResolvedApplicationErrorOnHost(String host) {
        return randomApplicationErrorWithResolvedAndHost(Resolved.YES, host);
    }

    private ApplicationError randomUnresolvedApplicationError() {
        return randomUnresolvedApplicationErrorOnHost(hostName);
    }

    private ApplicationError randomUnresolvedApplicationErrorOnHost(String host) {
        return randomApplicationErrorWithResolvedAndHost(Resolved.NO, host);
    }

    private ApplicationError randomApplicationErrorWithResolvedAndHost(Resolved resolved, String hostName) {
        var number = random.nextInt(100_000);
        var throwable = newThrowable("message " + number, "cause message " + number);
        return ApplicationError.newError(description + " " + number, resolved, hostName, ipAddress, port, throwable);
    }

    private ApplicationError newApplicationError(String description, Resolved resolved) {
        return newApplicationError(description, resolved, hostName);
    }

    private ApplicationError newApplicationError(String description, Resolved resolved, String host) {
        return ApplicationError.newError(description, resolved, host, ipAddress, port, null);
    }

    private Throwable newThrowable(String message, String causeMessage) {
        return new UncheckedIOException(message, new IOException(causeMessage));
    }
}
