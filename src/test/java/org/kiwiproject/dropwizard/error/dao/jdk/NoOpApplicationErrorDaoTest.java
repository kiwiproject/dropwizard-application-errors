package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.test.junit.jupiter.params.provider.RandomLongSource;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@DisplayName("NoOpApplicationErrorDao")
class NoOpApplicationErrorDaoTest {

    private NoOpApplicationErrorDao errorDao;

    @BeforeEach
    void setUp() {
        errorDao = new NoOpApplicationErrorDao();
    }


    @ParameterizedTest
    @RandomLongSource
    void shouldGetById(long id) {
        assertThat(errorDao.getById(id)).isEmpty();
    }

    @RepeatedTest(5)
    void shouldCountResolvedErrors() {
        assertThat(errorDao.countResolvedErrors()).isZero();
    }

    @RepeatedTest(5)
    void countUnresolvedErrors() {
        assertThat(errorDao.countUnresolvedErrors()).isZero();
    }

    @RepeatedTest(5)
    void countAllErrors() {
        assertThat(errorDao.countAllErrors()).isZero();
    }

    @RepeatedTest(5)
    void countUnresolvedErrorsSince() {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        assertThat(errorDao.countUnresolvedErrorsSince(now)).isZero();
    }

    @RepeatedTest(5)
    void countUnresolvedErrorsOnHostSince() {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        assertThat(errorDao.countUnresolvedErrorsOnHostSince(now, "localhost", "127.0.0.1")).isZero();
    }

    @RepeatedTest(5)
    void shouldGetAllErrors() {
        assertThat(errorDao.getAllErrors(1, 25)).isEmpty();
    }

    @RepeatedTest(5)
    void shouldGetErrors() {
        assertThat(errorDao.getErrors(ApplicationErrorStatus.UNRESOLVED, 1, 15)).isEmpty();
    }

    @RepeatedTest(5)
    void shouldGetUnresolvedErrorsByDescription() {
        assertThat(errorDao.getUnresolvedErrorsByDescription("some error")).isEmpty();
    }

    @RepeatedTest(5)
    void shouldGetUnresolvedErrorsByDescriptionAndHost() {
        assertThat(errorDao.getUnresolvedErrorsByDescriptionAndHost("some error", "localhost")).isEmpty();
    }

    @RepeatedTest(5)
    void shouldInsertError() {
        assertThat(errorDao.insertError(ApplicationError.builder().build())).isZero();
    }

    @ParameterizedTest
    @RandomLongSource
    void shouldIncrementCount(long id) {
        assertThatCode(() -> errorDao.incrementCount(id)).doesNotThrowAnyException();
    }

    @RepeatedTest(5)
    void shouldInsertOrIncrementCount() {
        assertThat(errorDao.insertOrIncrementCount(ApplicationError.builder().build())).isZero();
    }

    @ParameterizedTest
    @RandomLongSource
    void shouldResolve(long id) {
        var resolvedError = errorDao.resolve(id);
        assertThat(resolvedError).isNotNull();
        assertThat(resolvedError.getId()).isEqualTo(id);
    }

    @RepeatedTest(5)
    void shouldResolveAllUnresolvedErrors() {
        assertThat(errorDao.resolveAllUnresolvedErrors()).isZero();
    }

    @RepeatedTest(5)
    void shouldDeleteResolvedErrorsBefore() {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        assertThat(errorDao.deleteResolvedErrorsBefore(now)).isZero();
    }

    @RepeatedTest(5)
    void shouldDeleteUnresolvedErrorsBefore() {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        assertThat(errorDao.deleteUnresolvedErrorsBefore(now)).isZero();
    }
}
