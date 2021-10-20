package org.kiwiproject.dropwizard.error.job;

import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

import java.time.ZonedDateTime;

/**
 * Job that can be configured to run on a regular interval that will delete expired application errors.
 *
 * @see CleanupConfig for options on what will be deleted and when
 */
@Slf4j
public class CleanupApplicationErrorsJob implements Runnable {

    private final CleanupConfig config;
    private final ApplicationErrorDao errorDao;

    public CleanupApplicationErrorsJob(CleanupConfig config, ApplicationErrorDao errorDao) {
        this.config = config;
        this.errorDao = errorDao;
    }

    @Override
    public void run() {
        var expirationDate = ZonedDateTime.now().minusMinutes(config.getApplicationErrorExpiration().toMinutes());

        if (config.getCleanupStrategy() == CleanupConfig.CleanupStrategy.ALL_ERRORS) {
            var deletedCount = errorDao.deleteAllErrorsBefore(expirationDate);
            LOG.debug("Deleted {} expired application errors", deletedCount);
        } else {
            var deletedCount = errorDao.deleteResolvedErrorsBefore(expirationDate);
            LOG.debug("Deleted {} expired resolved application errors", deletedCount);
        }
    }

}
