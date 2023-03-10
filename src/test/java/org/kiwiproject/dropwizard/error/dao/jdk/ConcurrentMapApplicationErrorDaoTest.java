package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.dropwizard.error.dao.AbstractApplicationErrorDaoTest;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.test.junit.jupiter.ClearBoxTest;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@DisplayName("ConcurrentMapApplicationErrorDao")
class ConcurrentMapApplicationErrorDaoTest extends AbstractApplicationErrorDaoTest<ConcurrentMapApplicationErrorDao> {

    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final ConcurrentMapApplicationErrorDao concurrentMapErrorDao = new ConcurrentMapApplicationErrorDao();

    @BeforeEach
    void setUp() {
        countAndVerifyNoApplicationErrorsExist();
    }

    @Override
    protected ConcurrentMapApplicationErrorDao getErrorDao() {
        return concurrentMapErrorDao;
    }

    @Override
    protected long insertApplicationError(ApplicationError error) {
        var id = ID_GENERATOR.incrementAndGet();
        concurrentMapErrorDao.errors.put(id, error.withId(id));
        return id;
    }

    @Override
    protected long countApplicationErrors() {
        return concurrentMapErrorDao.errors.size();
    }

    @Override
    protected ApplicationError getErrorOrThrow(long id) {
        var error = concurrentMapErrorDao.errors.get(id);
        return Optional.ofNullable(error)
                .orElseThrow(() -> new IllegalStateException("No ApplicationError found with id " + id));
    }

    @Nested
    class IsResolvedOrUnresolved {

        @ParameterizedTest
        @CsvSource({
            "ALL, false",
            "RESOLVED, true",
            "UNRESOLVED, true"
        })
        void shouldReturnExpectedValue(ApplicationErrorStatus status, boolean expectedResult) {
            assertThat(ConcurrentMapApplicationErrorDao.isResolvedOrUnresolved(status)).isEqualTo(expectedResult);
        }
    }

    @Nested
    class UpdateWith {

        @ClearBoxTest
        void shouldSetCreatedAtIfNull_AndModifyUpdatedAt_ToNow() {
            var originalError = ApplicationError.builder().build();

            assertThat(originalError.getCreatedAt())
                    .describedAs("precondition violated: createdAt must be null")
                    .isNull();
            assertThat(originalError.getUpdatedAt())
                    .describedAs("precondition violated: updatedAt must be null")
                    .isNull();

            var updatedError = ConcurrentMapApplicationErrorDao.updateWith(originalError, 42, true);

            var now = ZonedDateTime.now(ZoneOffset.UTC);
            assertThat(updatedError.getCreatedAt()).isCloseTo(now, within(250, ChronoUnit.MILLIS));
            assertThat(updatedError.getUpdatedAt()).isCloseTo(now, within(250, ChronoUnit.MILLIS));
        }

        @ClearBoxTest
        void shouldUpdateResolved() {
            var originalError = ApplicationError.builder()
                    .numTimesOccurred(4)
                    .build();

            var updatedError = ConcurrentMapApplicationErrorDao.updateWith(originalError, 5, true);

            assertThat(updatedError.getNumTimesOccurred()).isEqualTo(5);
        }

        @ClearBoxTest
        void shouldUpdateNumTimesOccurred() {
            var originalError = ApplicationError.builder()
                    .resolved(false)
                    .build();

            var updatedError = ConcurrentMapApplicationErrorDao.updateWith(originalError, 5, true);

            assertThat(updatedError.isResolved()).isTrue();
        }
    }
}
