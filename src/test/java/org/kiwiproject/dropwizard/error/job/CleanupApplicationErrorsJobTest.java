package org.kiwiproject.dropwizard.error.job;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

import java.time.ZonedDateTime;

class CleanupApplicationErrorsJobTest {

    private ApplicationErrorDao dao;

    @BeforeEach
    void setUp() {
        dao = mock(ApplicationErrorDao.class);
    }

    @Test
    void shouldDeleteAllExpiredErrors_WhenStrategyIsAllErrors() {
        var config = new CleanupConfig();
        config.setCleanupStrategy(CleanupConfig.CleanupStrategy.ALL_ERRORS);

        var job = new CleanupApplicationErrorsJob(config, dao);
        job.run();

        verify(dao).deleteAllErrorsBefore(argThat(time -> time.isBefore(ZonedDateTime.now().minusDays(30))));
    }

    @Test
    void shouldDeleteResolvedExpiredErrors_WhenStrategyIsResolvedErrors() {
        var config = new CleanupConfig();
        config.setCleanupStrategy(CleanupConfig.CleanupStrategy.RESOLVED_ONLY);

        var job = new CleanupApplicationErrorsJob(config, dao);
        job.run();

        verify(dao).deleteResolvedErrorsBefore(argThat(time -> time.isBefore(ZonedDateTime.now().minusDays(30))));
    }
}
