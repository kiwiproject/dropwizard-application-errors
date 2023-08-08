package org.kiwiproject.dropwizard.error.health;

import io.dropwizard.util.Duration;
import io.dropwizard.validation.MinDuration;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * Can be used as a configuration class, e.g. in a Dropwizard service, in order to configure
 * the recent errors health check with a non-default time period.
 *
 * @implNote Assumes instances will be deserialized from a standard Dropwizard YAML configuration
 * file, therefore provides both an all-args constructor and a no-args constructor and setters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeWindow {

    /**
     * Default time window amount in minutes.
     */
    public static final long DEFAULT_TIME_WINDOW_MINUTES = 15;

    /**
     * The duration of the time window.
     */
    @NotNull
    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    private Duration duration = Duration.minutes(DEFAULT_TIME_WINDOW_MINUTES);

}
