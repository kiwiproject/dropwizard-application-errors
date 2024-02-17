package org.kiwiproject.dropwizard.error.job;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.config.CleanupConfig.CleanupStrategy;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@DisplayName("CleanupApplicationErrorsJob")
@Slf4j
class CleanupApplicationErrorsJobTest {

    private ApplicationErrorDao dao;

    @BeforeEach
    void setUp() {
        dao = mock(ApplicationErrorDao.class);
    }

    @Test
    void shouldRequireResolvedExpirationOfAtLeastOneMinute() {
        var config = new CleanupConfig();
        config.setResolvedErrorExpiration(Duration.seconds(59));

        assertThatIllegalStateException()
                .isThrownBy(() -> new CleanupApplicationErrorsJob(config, dao))
                .withMessage("resolvedErrorExpiration must be at least one minute");
    }

    @Test
    void shouldRequireUnresolvedExpirationOfAtLeastOneMinute() {
        var config = new CleanupConfig();
        config.setUnresolvedErrorExpiration(Duration.seconds(59));

        assertThatIllegalStateException()
                .isThrownBy(() -> new CleanupApplicationErrorsJob(config, dao))
                .withMessage("unresolvedErrorExpiration must be at least one minute");
    }

    @Test
    void shouldDeleteAllExpiredErrors_WhenStrategyIsAllErrors() {
        var config = new CleanupConfig();
        config.setCleanupStrategy(CleanupStrategy.ALL_ERRORS);

        var job = new CleanupApplicationErrorsJob(config, dao);
        job.run();

        var now = ZonedDateTime.now(ZoneOffset.UTC);

        var expectedResolvedThreshold = now.minusMinutes(config.getResolvedErrorExpiration().toMinutes());
        LOG.debug("Expecting resolved threshold: {}", expectedResolvedThreshold);
        verify(dao).deleteResolvedErrorsBefore(argThat(time -> time.isBefore(expectedResolvedThreshold)));

        var expectedUnresolvedThreshold = now.minusMinutes(config.getUnresolvedErrorExpiration().toMinutes());
        LOG.debug("Expecting unresolved threshold: {}", expectedUnresolvedThreshold);
        verify(dao).deleteUnresolvedErrorsBefore(argThat(time -> time.isBefore(expectedUnresolvedThreshold)));
    }

    @Test
    void shouldDeleteResolvedExpiredErrors_WhenStrategyIsResolvedErrors() {
        var config = new CleanupConfig();
        config.setCleanupStrategy(CleanupStrategy.RESOLVED_ONLY);

        var job = new CleanupApplicationErrorsJob(config, dao);
        job.run();

        var expectedResolvedThreshold = ZonedDateTime.now(ZoneOffset.UTC).minusMinutes(config.getResolvedErrorExpiration().toMinutes());
        LOG.debug("Expecting resolved threshold: {}", expectedResolvedThreshold);
        verify(dao).deleteResolvedErrorsBefore(argThat(time -> time.isBefore(expectedResolvedThreshold)));

        verifyNoMoreInteractions(dao);
    }
}
