package org.kiwiproject.dropwizard.error.job;

import static org.kiwiproject.base.KiwiPreconditions.checkPositive;

import lombok.extern.slf4j.Slf4j;
import org.kiwiproject.base.CatchingRunnable;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.config.CleanupConfig.CleanupStrategy;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Job that can be configured to run on a regular interval that will delete expired application errors.
 *
 * @see CleanupConfig configuration options on what will be deleted and when
 */
@Slf4j
public class CleanupApplicationErrorsJob implements CatchingRunnable {

    private final CleanupConfig config;
    private final long resolvedErrorExpirationMinutes;
    private final long unresolvedErrorExpirationMinutes;
    private final ApplicationErrorDao errorDao;

    public CleanupApplicationErrorsJob(CleanupConfig config, ApplicationErrorDao errorDao) {
        this.config = config;
        this.resolvedErrorExpirationMinutes = config.getResolvedErrorExpiration().toMinutes();
        this.unresolvedErrorExpirationMinutes = config.getUnresolvedErrorExpiration().toMinutes();
        this.errorDao = errorDao;

        checkPositive(resolvedErrorExpirationMinutes, "resolvedErrorExpiration must be at least one minute");
        checkPositive(unresolvedErrorExpirationMinutes, "unresolvedErrorExpiration must be at least one minute");
    }

    @Override
    public void runSafely() {
        var now = ZonedDateTime.now(ZoneOffset.UTC);

        var resolvedErrorsExpiration = now.minusMinutes(resolvedErrorExpirationMinutes);
        var resolvedDeletedCount = errorDao.deleteResolvedErrorsBefore(resolvedErrorsExpiration);
        LOG.debug("Deleted {} expired resolved application errors before {}",
                resolvedDeletedCount, resolvedErrorsExpiration);

        if (config.getCleanupStrategy() == CleanupStrategy.ALL_ERRORS) {
            var unresolvedErrorsExpiration = now.minusMinutes(unresolvedErrorExpirationMinutes);
            var unresolvedDeletedCount = errorDao.deleteUnresolvedErrorsBefore(unresolvedErrorsExpiration);
            LOG.debug("Deleted {} expired but unresolved application errors before {}",
                    unresolvedDeletedCount, unresolvedErrorsExpiration);
        }
    }

}
