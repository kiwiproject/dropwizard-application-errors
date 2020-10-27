package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static com.google.common.base.Verify.verify;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.test.jdbi.Jdbi3GeneratedKeys.executeAndGenerateId;
import static org.kiwiproject.test.util.DateTimeTestHelper.assertTimeDifferenceWithinTolerance;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.ApplicationError.Resolved;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.test.junit.jupiter.Jdbi3DaoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Base test class for testing {@link Jdbi3ApplicationErrorDao}. Used to test against different databases, currently
 * Postgres and an in-memory H2 database.
 */
@ExtendWith(ApplicationErrorExtension.class)
@ExtendWith(SoftAssertionsExtension.class)
@SuppressWarnings({"SqlDialectInspection", "SqlNoDataSourceInspection"})
abstract class AbstractJdbi3ApplicationErrorDaoTest {

    private Jdbi3ApplicationErrorDao errorDao;
    private Handle handle;
    private ThreadLocalRandom random;
    private String description;
    private Throwable throwable;
    private Resolved resolved;
    private String hostName;
    private String ipAddress;
    private int port;

    @BeforeEach
    final void baseSetUp() throws UnknownHostException {
        errorDao = getTestExtension().getDao();
        handle = getTestExtension().getHandle();

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

        var count = countApplicationErrors();
        verify(count == 0, "No application errors should exist but found %s!", count);
    }

    private long countApplicationErrors() {
        return handle.createQuery("select count(*) from application_errors").mapTo(Long.class).one();
    }

    /**
     * This should return the same instance every time is is called.
     */
    abstract Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension();

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
        void shouldInsertNewApplicationErrorRecord(SoftAssertions softly) {
            var beforeInsert = ZonedDateTime.now(ZoneOffset.UTC);
            var id = errorDao.insertError(defaultApplicationError());

            var retrievedError = getErrorOrThrow(id);

            softly.assertThat(retrievedError.getId()).isEqualTo(id);
            assertTimeDifferenceWithinTolerance(softly, "createdAt", beforeInsert, retrievedError.getCreatedAt());
            assertTimeDifferenceWithinTolerance(softly, "updatedAt", beforeInsert, retrievedError.getUpdatedAt());
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
            assertThat(errorsSince).isEqualTo(1);
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
                .resolved(resolved == Resolved.YES)
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

            var desc = description + random.nextInt(100_000);
            var resolvedError = newApplicationError(desc, Resolved.YES);
            var resolvedId = insertApplicationError(resolvedError);

            var errors = errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 10);
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
            var hostl = "my-host-1";
            var host2 = "my-host-2";

            insertErrorsWithResolvedAs(5, Resolved.YES, hostl);
            insertErrorsWithResolvedAs(3, Resolved.YES, host2);
            insertErrorsWithResolvedAs(7, Resolved.NO, hostl);
            insertErrorsWithResolvedAs(6, Resolved.NO, host2);

            var desc = description + random.nextInt(100_000);
            var unresolvedError1 = newApplicationError(desc, Resolved.NO, hostl);
            long unresolvedId1 = insertApplicationError(unresolvedError1);

            var unresolvedError2 = newApplicationError(desc, Resolved.NO, host2);
            long unresolvedId2 = insertApplicationError(unresolvedError2);

            var errors1 = errorDao.getUnresolvedErrorsByDescriptionAndHost(desc, hostl);
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
                    .withMessage("Increment failed. No ApplicationError found with id " + id);
        }
    }

    @Nested
    class InsertOrIncrementCount {

        @Test
        void shouldInsertNew_WhenErrorDoesNotExist(SoftAssertions softly) {
            var error = randomUnresolvedApplicationError();
            var id = errorDao.insertOrIncrementCount(error);

            var result = getErrorOrThrow(id);
            softly.assertThat(result.getNumTimesOccurred()).isEqualTo(1);
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

    private ApplicationError getErrorOrThrow(long id) {
        return handle.createQuery("select * from application_errors where id = ?")
                .bind(0, id)
                .map(new Jdbi3ApplicationErrorRowMapper())
                .one();
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

    private long insertApplicationError(ApplicationError error) {
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
}
