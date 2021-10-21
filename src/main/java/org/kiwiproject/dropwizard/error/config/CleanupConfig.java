package org.kiwiproject.dropwizard.error.config;

import io.dropwizard.util.Duration;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration class used to setup the {@link org.kiwiproject.dropwizard.error.job.CleanupApplicationErrorsJob}
 */
@Getter
@Setter
public class CleanupConfig {

    /**
     * Strategies for what should be cleaned up.
     * <p>
     * <ul>
     *     <li>ALL_ERRORS - Will remove resolved AND unresolved errors older than {@code applicationErrorExpiration}</li>
     *     <li>RESOLVED_ONLY - Will remove resolved errors older than {@code applicationErrorExpiration}</li>
     * </ul>
     */
    public enum CleanupStrategy {
        ALL_ERRORS, RESOLVED_ONLY
    }

    /**
     * The strategy to use for what to clean up. Defaults to {@link CleanupStrategy#ALL_ERRORS}.
     */
    private CleanupStrategy cleanupStrategy = CleanupStrategy.ALL_ERRORS;

    /**
     * The duration that a resolved error will live before being deleted.
     */
    private Duration resolvedErrorExpiration = Duration.days(14);

    /**
     * The duration of time that an unresolved error will live before being deleted.
     */
    private Duration unresolvedErrorException = Duration.days(60);

    /**
     * The name to give the scheduled job for cleaning up errors. Defaults to {@code Cleanup-Job-%d}
     */
    private String cleanupJobName = "Cleanup-Job-%d";

    /**
     * Initial delay before the cleanup job runs. Defaults to 1 minute.
     */
    private Duration initialJobDelay = Duration.minutes(1);

    /**
     * Interval that the cleanup job will run. Defaults to once a day.
     */
    private Duration jobInterval = Duration.days(1);
}