package org.kiwiproject.dropwizard.error.config;

import io.dropwizard.util.Duration;
import io.dropwizard.validation.MinDuration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.TimeUnit;

/**
 * Configuration class used to set up the {@link org.kiwiproject.dropwizard.error.job.CleanupApplicationErrorsJob}
 */
@Getter
@Setter
public class CleanupConfig {

    /**
     * Strategies for what should be cleaned up.
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
    @NotNull
    private CleanupStrategy cleanupStrategy = CleanupStrategy.ALL_ERRORS;

    /**
     * The duration that a resolved error will live before being deleted. Defaults to 14 days.
     */
    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration resolvedErrorExpiration = Duration.days(14);

    /**
     * The duration that an unresolved error will live before being deleted. Defaults to 60 days.
     */
    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration unresolvedErrorExpiration = Duration.days(60);

    /**
     * The name to give the scheduled job for cleaning up errors. Defaults to {@code Application-Errors-Cleanup-Job-%d}
     * which will result in thread names like {@code Application-Errors-Cleanup-Job-1}.
     */
    @NotBlank
    private String cleanupJobName = "Application-Errors-Cleanup-Job-%d";

    /**
     * Initial delay before the cleanup job runs. Defaults to 1 minute.
     */
    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration initialJobDelay = Duration.minutes(1);

    /**
     * Interval that the cleanup job will run. Defaults to once a day.
     */
    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration jobInterval = Duration.days(1);
}
