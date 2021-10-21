package org.kiwiproject.dropwizard.error.job;

import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.CatchingRunnable;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

import java.time.ZonedDateTime;

/**
 * Job that can be configured to run on a regular interval that will delete expired application errors.
 *
 * @see CleanupConfig for options on what will be deleted and when
 */
@Slf4j
public class CleanupApplicationErrorsJob implements CatchingRunnable {

    private final CleanupConfig config;
    private final ApplicationErrorDao errorDao;

    public CleanupApplicationErrorsJob(CleanupConfig config, ApplicationErrorDao errorDao) {
        this.config = config;
        this.errorDao = errorDao;
    }

    @Override
    public void runSafely() {
        var resolvedErrorsExpiration = ZonedDateTime.now().minusMinutes(config.getResolvedErrorExpiration().toMinutes());
        var resolvedDeletedCount = errorDao.deleteResolvedErrorsBefore(resolvedErrorsExpiration);
        LOG.debug("Deleted {} expired resolved application errors", resolvedDeletedCount);

        if (config.getCleanupStrategy() == CleanupConfig.CleanupStrategy.ALL_ERRORS) {
            var unresolvedErrorsExpiration = ZonedDateTime.now().minusMinutes(config.getUnresolvedErrorException().toMinutes());
            var unresolvedDeletedCount = errorDao.deleteUnresolvedErrorsBefore(unresolvedErrorsExpiration);
            LOG.debug("Deleted {} expired application errors", unresolvedDeletedCount);
        }
    }

}
