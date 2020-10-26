package org.kiwiproject.dropwizard.error.dao.jdbi3;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.kiwiproject.collect.KiwiLists.first;
import static org.kiwiproject.test.jdbi.Jdbi3GeneratedKeys.executeAndGenerateId;
import static org.kiwiproject.test.util.DateTimeTestHelper.assertTimeDifferenceWithinTolerance;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.Handle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
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

// TODO Cleanup/restructure using @Nested when & where it makes sense...

/**
 * Base test class for testing {Kink Jdbi3ApplicationErrorDao}. Used to test against different databases, currently
 * Postgres and an in-memory H2 database.
 */
@ExtendWith({ApplicationErrorExtension.class, SoftAssertionsExtension.class})
abstract class AbstractJdbi3ApplicationErrorDaoTest {

    private Jdbi3ApplicationErrorDao errorDao;
    private Handle handle;
    private ThreadLocalRandom random;
    private String description;
    private Throwable throwable;
    private ApplicationError.Resolved resolved;
    private String hostName;
    private String ipAddress;
    private int port;

    @BeforeEach
    final void baseSetUp() throws UnknownHostException {
        errorDao = getTestExtension().getDao();
        handle = getTestExtension().getHandle();

        random = ThreadLocalRandom.current();
        description = "Something terrible has happened...millions of voices crying out in terror, and then silenced.";
        String exceptionMessage = "I/O error occurred";
        String exceptionCauseMessage = "Alderaan seems to be missing...";
        throwable = newThrowable(exceptionMessage, exceptionCauseMessage);
        resolved = ApplicationError.Resolved.NO;
        hostName = InetAddress.getLocalHost().getHostName();
        ipAddress = InetAddress.getLocalHost().getHostAddress();
        port = 8080;
    }

    /**
     * This should return the same instance every time is is called.
     */
    abstract Jdbi3DaoExtension<Jdbi3ApplicationErrorDao> getTestExtension();

    @Test
    void testGetById_WhenNotFound() {
        var errorOptional = errorDao.getById(Long.MIN_VALUE);

        assertThat(errorOptional).isEmpty();
    }

    @Test
    void testGetById_WhenFound() {
        var id = insertApplicationError(defaultApplicationError());

        var errorOptional = errorDao.getById(id);
        var error = errorOptional.orElseThrow();
        assertThat(error.getId()).isEqualTo(id);
    }

    @Test
    void testInsertError_WhenAnIdExists() {
        var error = ApplicationError.builder()
                .id(42L)
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> errorDao.insertError(error))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .withMessage("Cannot insert an ApplicationError that has an id");
    }

    @Test
    void testInsertError(SoftAssertions softly) {
        var beforeInsert = ZonedDateTime.now(ZoneOffset.UTC);
        var id = errorDao.insertError(defaultApplicationError());

        var retrievedError = getErrorWithIdOf(id);

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

    @Test
    void testResolve() {
        var unresolvedError = defaultApplicationError();
        assertThat(unresolvedError.isResolved()).isFalse();

        var id = insertApplicationError(unresolvedError);

        var resolvedError = errorDao.resolve(id);
        assertThat(resolvedError.getId()).isEqualTo(id);
        assertThat(resolvedError.isResolved()).isTrue();

        var result = getErrorWithIdOf(id);
        assertThat(result.isResolved()).isTrue();
    }

    @Test
    void testResolve_WhenApplicationErrorDoesNotExist() {
        long id = Long.MIN_VALUE;

        assertThatIllegalStateException()
                .isThrownBy(() -> errorDao.resolve(id))
                .isExactlyInstanceOf(IllegalStateException.class)
                .withMessage("Unable to resolve. No ApplicationError found with id %d", id);
    }

    @Test
    void testResolveAllUnresolvedErrors() {
        deleteApplicationErrors();  // tODO delete all in setup???? assume it is empty????

        var numUnresolved = 25;
        for (int i = 0; i < numUnresolved; i++) {
            insertApplicationError(randomUnresolvedApplicationError());
        }

        var numUnresolvedErrors = errorDao.countUnresolvedErrors();
        assertThat(numUnresolvedErrors).isEqualTo(numUnresolved);

        var count = errorDao.resolveAllUnresolvedErrors();
        assertThat(count).isEqualTo(numUnresolvedErrors);
        assertThat(errorDao.countUnresolvedErrors()).isZero();
    }

    @Test
    void testCount_AllErrors() {
        deleteApplicationErrors();

        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());

        assertThat(errorDao.count(ApplicationErrorStatus.ALL)).isEqualTo(5);
        assertThat(errorDao.countAllErrors()).isEqualTo(5);
    }

    @Test
    void testCount_UnresolvedErrors() {
        deleteApplicationErrors();

        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());

        assertThat(errorDao.count(ApplicationErrorStatus.UNRESOLVED)).isEqualTo(2);
        assertThat(errorDao.countUnresolvedErrors()).isEqualTo(2);
    }

    @Test
    void testCount_ResolvedErrors() {
        deleteApplicationErrors();

        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomResolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());
        insertApplicationError(randomUnresolvedApplicationError());

        assertThat(errorDao.count(ApplicationErrorStatus.RESOLVED)).isEqualTo(3);
        assertThat(errorDao.countResolvedErrors()).isEqualTo(3);
    }

    @Test
    void testCountUnresolvedErrorsSinceDate_WhenShouldBeNone() {
        deleteApplicationErrors();

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(35), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError);

        var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), ApplicationError.Resolved.YES);
        insertApplicationError(resolvedError);

        var errorsSince = errorDao.countUnresolvedErrorsSince(now.minusMinutes(30));
        assertThat(errorsSince).isZero();
    }

    @Test
    void testCountUnresolvedErrorsSinceDate_WhenShouldBeOne() {
        deleteApplicationErrors();

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError);

        var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), ApplicationError.Resolved.YES);
        insertApplicationError(resolvedError);

        long errorsSince = errorDao.countUnresolvedErrorsSince(now.minusMinutes(30));
        assertThat(errorsSince).isEqualTo(1);
    }

    @Test
    void testCountUnresolvedErrorsSinceDate_WhenShouldBeMultiple() {
        deleteApplicationErrors();

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedError1 = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError1);

        var unresolvedError2 = newApplicationErrorWithUpdatedDate(now.minusMinutes(15), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError2);

        var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), ApplicationError.Resolved.YES);
        insertApplicationError(resolvedError);

        long errorsSince = errorDao.countUnresolvedErrorsSince(now.minusMinutes(30));
        assertThat(errorsSince).isEqualTo(2);
    }

    @Test
    void testCountUnresolvedErrorsOnHostSinceDate_WhenShouldBeNone() {
        deleteApplicationErrors();

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(35), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError);

        var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), ApplicationError.Resolved.YES);
        insertApplicationError(resolvedError);

        long errorsSince = errorDao.countUnresolvedErrorsOnHostSince(now.minusMinutes(30), "another.host", "10.10.1.1");
        assertThat(errorsSince).isZero();
    }

    @Test
    void testCountUnresolvedErrorsOnHostSinceDate_WhenShouldBeOne() {
        deleteApplicationErrors();

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError);

        var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), ApplicationError.Resolved.YES);
        insertApplicationError(resolvedError);

        long errorsSince = errorDao.countUnresolvedErrorsOnHostSince(now.minusMinutes(30), hostName, ipAddress);
        assertThat(errorsSince).isOne();
    }


    @Test
    void testCountUnresolvedErrorsOnHostSinceDate_WhenShouldBeMultiple() {
        deleteApplicationErrors();

        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var unresolvedError1 = newApplicationErrorWithUpdatedDate(now.minusMinutes(25), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError1);

        var unresolvedError2 = newApplicationErrorWithUpdatedDate(now.minusMinutes(15), ApplicationError.Resolved.NO);
        insertApplicationError(unresolvedError2);

        var resolvedError = newApplicationErrorWithUpdatedDate(now.minusMinutes(5), ApplicationError.Resolved.YES);
        insertApplicationError(resolvedError);

        long errorsSince = errorDao.countUnresolvedErrorsOnHostSince(now.minusMinutes(30), hostName, ipAddress);
        assertThat(errorsSince).isEqualTo(2);
    }

    private void deleteApplicationErrors() {
        handle.execute("delete from application_errors");
    }

    private ApplicationError newApplicationErrorWithUpdatedDate(ZonedDateTime dateTime, ApplicationError.Resolved resolved) {
        return ApplicationError.builder()
                .createdAt(dateTime)
                .updatedAt(dateTime)
                .resolved(resolved == ApplicationError.Resolved.YES)
                .description("test error at " + dateTime)
                .hostName(hostName)
                .ipAddress(ipAddress)
                .port(port)
                .build();
    }


    @Test
    void testGetErrors_WithInvalidPageNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 0, 100))
                .withMessage("pageNumber starts at 1");
    }

    @Test
    void testGetErrors_WithInvalidPageSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 0))
                .withMessage("pageSize must be at least 1");
    }

    @Test
    void testGetAllErrors_WithInvalidPageNumber() {
        var thrown = catchThrowable(() -> errorDao.getAllErrors(0, 100));

        assertThat(thrown)
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageNumber starts at 1");
    }

    @Test
    void testGetAllErrors_WithInvalidPageSize() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> errorDao.getAllErrors(1, 0))
                .withMessage("pageSize must be at least 1");
    }

    @Test
    void testGetErrors_WithStatusAll() {
        var resolvedIds = insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES);
        var unresolvedIds = insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO);

        var allErrors = errorDao.getAllErrors(1, 100);
        assertThat(allErrors).extracting("id").containsAll(resolvedIds).containsAll(unresolvedIds);

        var allErrors1 = errorDao.getErrors(ApplicationErrorStatus.ALL, 1, 100);
        assertThat(allErrors1).extracting("id").containsAll(resolvedIds).containsAll(unresolvedIds);
    }


    @Test
    void testGetErrors_WithStatusUnresolved() {
        var resolvedIds = insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES);
        var unresolvedIds = insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO);

        var unresolvedErrors = errorDao.getErrors(ApplicationErrorStatus.UNRESOLVED, 1, 100);
        assertThat(unresolvedErrors)
                .extracting("id")
                .containsAll(unresolvedIds)
                .doesNotContainAnyElementsOf(resolvedIds);
    }

    @Test
    void testGetErrors_WithStatusResolved() {
        var resolvedIds = insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES);
        var unresolvedIds = insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO);

        var unresolvedErrors = errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 100);
        assertThat(unresolvedErrors)
                .extracting("id")
                .containsAll(resolvedIds)
                .doesNotContainAnyElementsOf(unresolvedIds);
    }

    @Test
    void testGetResolvedError() {
        insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES);
        insertErrorsWithResolvedAs(3, ApplicationError.Resolved.NO);

        var desc = description + random.nextInt(100_000);
        var resolvedError = newApplicationError(desc, ApplicationError.Resolved.YES);
        var resolvedId = insertApplicationError(resolvedError);

        var errors = errorDao.getErrors(ApplicationErrorStatus.RESOLVED, 1, 10);
        var mostRecentError = first(errors);
        assertThat(mostRecentError.getId()).isEqualTo(resolvedId);
    }


    @Test
    void testGetUnresolvedErrorsByDescription() {
        insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES);
        insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO);

        var desc = description + random.nextInt(100_000);
        var unresolvedError = newApplicationError(desc, ApplicationError.Resolved.NO);
        long unresolvedId = insertApplicationError(unresolvedError);

        var errors = errorDao.getUnresolvedErrorsByDescription(desc);
        assertThat(errors)
                .hasSize(1)
                .extracting("id")
                .containsOnly(unresolvedId);
    }

    @Test
    void testGetUnresolvedErrorsByDescription_WhenMoreThanOneWithSameDescription() {
        insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES);
        insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO);

        var desc = description + random.nextInt(100_000);
        var unresolvedError1 = newApplicationError(desc, ApplicationError.Resolved.NO);
        var unresolvedId1 = insertApplicationError(unresolvedError1);

        var unresolvedError2 = newApplicationError(desc, ApplicationError.Resolved.NO);
        var unresolvedId2 = insertApplicationError(unresolvedError2);

        var errors = errorDao.getUnresolvedErrorsByDescription(desc);
        assertThat(errors)
                .hasSize(2)
                .extracting("id")
                .containsOnly(unresolvedId1, unresolvedId2);
    }

    @Test
    void testGetUnresolvedErrorsByDescriptionAndHost() {
        var host1 = "my-host-1";
        var host2 = "my-host-2";

        insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES, host1);
        insertErrorsWithResolvedAs(3, ApplicationError.Resolved.YES, host2);
        insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO, host1);
        insertErrorsWithResolvedAs(6, ApplicationError.Resolved.NO, host2);

        var desc = description + random.nextInt(100_000);
        var unresolvedError = newApplicationError(desc, ApplicationError.Resolved.NO, host2);
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

        insertErrorsWithResolvedAs(5, ApplicationError.Resolved.YES, hostl);
        insertErrorsWithResolvedAs(3, ApplicationError.Resolved.YES, host2);
        insertErrorsWithResolvedAs(7, ApplicationError.Resolved.NO, hostl);
        insertErrorsWithResolvedAs(6, ApplicationError.Resolved.NO, host2);

        var desc = description + random.nextInt(100_000);
        var unresolvedError1 = newApplicationError(desc, ApplicationError.Resolved.NO, hostl);
        long unresolvedId1 = insertApplicationError(unresolvedError1);

        var unresolvedError2 = newApplicationError(desc, ApplicationError.Resolved.NO, host2);
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


    @Test
    void testIncrementCount_WhenUpdatesOneRow(SoftAssertions softly) {
        var unresolvedError = newApplicationError(description, ApplicationError.Resolved.NO);
        var id = insertApplicationError(unresolvedError);

        softlyAssertNumTimesOccurred(softly, id, 1);
        errorDao.incrementCount(id);
        softlyAssertNumTimesOccurred(softly, id, 2);
    }

    @Test
    void testIncrementCount_WhenUpdatedZeroRows() {
        var id = Long.MIN_VALUE;
        var thrown = catchThrowable(() -> errorDao.incrementCount(id));
        assertThat(thrown)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Increment failed. No ApplicationError found with id " + id);
    }

    @Test
    void testInsertOrIncrementCount_ForNewError(SoftAssertions softly) {
        var error = randomUnresolvedApplicationError();
        var id = errorDao.insertOrIncrementCount(error);

        var result = getErrorWithIdOf(id);
        softly.assertThat(result.getNumTimesOccurred()).isEqualTo(1);
        softly.assertThat(result.getDescription()).isEqualTo(error.getDescription());
    }

    @Test
    void testInsertOrIncrementCount_ForExistingError(SoftAssertions softly) {
        var id = insertApplicationError(randomUnresolvedApplicationError());
        softlyAssertNumTimesOccurred(softly, id, 1);

        var error = errorDao.getById(id).orElse(null);
        assertThat(error).isNotNull();

        long idOfIncremented = errorDao.insertOrIncrementCount(error);
        assertThat(idOfIncremented).isEqualTo(id);

        var result = getErrorWithIdOf(id);
        softly.assertThat(result.getNumTimesOccurred()).isEqualTo(2);
        softly.assertThat(result.getDescription()).isEqualTo(error.getDescription());
    }

    @Test
    void testInsertOrIncrementCount_ForExistingError_DoesNotChangeOtherFields(SoftAssertions softly) {
        var description = "uh oh uh oh";
        var id = insertApplicationError(ApplicationError.newUnresolvedError(description));
        softlyAssertNumTimesOccurred(softly, id, 1);

        var originalError = errorDao.getById(id).orElseThrow(IllegalStateException::new);
        var idOfIncremented = errorDao.insertOrIncrementCount(originalError);
        assertThat(idOfIncremented).isEqualTo(id);

        var result = getErrorWithIdOf(id);
        softly.assertThat(result).usingRecursiveComparison().ignoringFields("numTimesOccurred", "updatedAt").isEqualTo(originalError);
        softly.assertThat(result.getNumTimesOccurred()).isEqualTo(2);

        // Due to the way the transactions work during tests (i.e. transaction per test auto rolled back), and the
        // fact that Postgres will always return the **same** value from current_timestamp during a single transaction,
        // we cannot actually verify that the updatedAt value changes in this test. Inspect the SQL update in the
        // DAO to verify updated_at is updated.
    }

    private void softlyAssertNumTimesOccurred(SoftAssertions softly, long id, int numTimesOccurred) {
        ApplicationError result = getErrorWithIdOf(id);
        softly.assertThat(result.getNumTimesOccurred()).isEqualTo(numTimesOccurred);
    }

    private ApplicationError getErrorWithIdOf(long id) {
        return handle.createQuery("select * from application_errors where id = ?")
                .bind(0, id)
                .map(new Jdbi3ApplicationErrorRowMapper())
                .one();
    }

    private List<Long> insertErrorsWithResolvedAs(int count, ApplicationError.Resolved resolved) {
        return insertErrorsWithResolvedAs(count, resolved, hostName);
    }

    private List<Long> insertErrorsWithResolvedAs(int count, ApplicationError.Resolved resolved, String host) {
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
        return randomApplicationErrorWithResolvedAndHost(ApplicationError.Resolved.YES, host);
    }

    private ApplicationError randomUnresolvedApplicationError() {
        return randomUnresolvedApplicationErrorOnHost(hostName);
    }

    private ApplicationError randomUnresolvedApplicationErrorOnHost(String host) {
        return randomApplicationErrorWithResolvedAndHost(ApplicationError.Resolved.NO, host);
    }

    private ApplicationError randomApplicationErrorWithResolvedAndHost(ApplicationError.Resolved resolved, String hostName) {
        var number = random.nextInt(100_000);
        var error = newThrowable("message " + number, "cause message " + number);
        return ApplicationError.newError(description + " " + number, resolved, hostName, ipAddress, port, error);
    }

    private ApplicationError newApplicationError(String description, ApplicationError.Resolved resolved) {
        return newApplicationError(description, resolved, hostName);
    }

    private ApplicationError newApplicationError(String description, ApplicationError.Resolved resolved, String host) {
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
