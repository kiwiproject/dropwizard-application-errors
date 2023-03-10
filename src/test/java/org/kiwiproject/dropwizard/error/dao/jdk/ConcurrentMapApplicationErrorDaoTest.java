package org.kiwiproject.dropwizard.error.dao.jdk;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.dropwizard.error.dao.AbstractApplicationErrorDaoTest;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;

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
}
