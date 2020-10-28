package org.kiwiproject.dropwizard.error.health;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.kiwiproject.metrics.health.HealthCheckResults.newHealthyResult;
import static org.kiwiproject.metrics.health.HealthCheckResults.newUnhealthyResult;

import com.codahale.metrics.health.HealthCheck;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.kiwiproject.base.KiwiStrings;
import org.kiwiproject.dropwizard.error.ServiceDetails;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

/**
 * Checks if there have been <em>any</em> application errors created or updated in a time window ending at the current
 * date/time and extending backward for a configurable period of time. The default time window is 15 minutes, so the
 * check will report healthy if no application errors have been created or updated in the last 15 minutes, otherwise
 * it will report unhealthy.
 */
@Slf4j
public class RecentErrorsHealthCheck extends HealthCheck {

    private static final boolean SUPPRESS_LEADING_ZERO_ELEMENTS = true;
    private static final boolean SUPPRESS_TRAILING_ZERO_ELEMENTS = true;

    private final ApplicationErrorDao errorDao;

    @Getter
    private final long timeWindowAmount;

    @Getter
    private final TemporalUnit timeWindowUnit;

    @Getter
    private final String humanReadableTimeWindow;

    private final String messageSuffix;
    private final ServiceDetails serviceDetails;

    /**
     * Create with default time window.
     *
     * @param errorDao       the application error DAO
     * @param serviceDetails the service/application information
     * @see TimeWindow#DEFAULT_TIME_WINDOW_MINUTES
     */
    public RecentErrorsHealthCheck(ApplicationErrorDao errorDao, ServiceDetails serviceDetails) {
        this(errorDao, serviceDetails, TimeWindow.DEFAULT_TIME_WINDOW_MINUTES, ChronoUnit.MINUTES);
    }

    /**
     * Create with specified time window.
     *
     * @param errorDao       the application error DAO
     * @param serviceDetails the service/application information
     * @param timeWindow     the time window (a {@link TimeWindow})
     */
    public RecentErrorsHealthCheck(ApplicationErrorDao errorDao,
                                   ServiceDetails serviceDetails,
                                   TimeWindow timeWindow) {
        this(errorDao, serviceDetails, timeWindow.getDuration());
    }

    /**
     * Create with specified time window.
     *
     * @param errorDao       the application error DAO
     * @param serviceDetails the service/application information
     * @param timeWindow     the time window (a Dropwizard {@link io.dropwizard.util.Duration})
     * @implNote Currently converts the given Dropwizard Duration object into milliseconds, such that the
     * {@link #getTimeWindowUnit()} will return {@link ChronoUnit#MILLIS}.
     */
    public RecentErrorsHealthCheck(ApplicationErrorDao errorDao,
                                   ServiceDetails serviceDetails,
                                   io.dropwizard.util.Duration timeWindow) {
        this(errorDao, serviceDetails, timeWindow.toMilliseconds(), ChronoUnit.MILLIS);
    }

    /**
     * Create with specified time window amount and unit.
     *
     * @param errorDao         the application error DAO
     * @param serviceDetails   the service/application information
     * @param timeWindowAmount the time window amount
     * @param timeWindowUnit   the time window unit
     */
    public RecentErrorsHealthCheck(ApplicationErrorDao errorDao,
                                   ServiceDetails serviceDetails,
                                   long timeWindowAmount, TemporalUnit timeWindowUnit) {
        this.errorDao = errorDao;
        this.serviceDetails = serviceDetails;
        this.timeWindowAmount = timeWindowAmount;
        this.timeWindowUnit = timeWindowUnit;
        this.humanReadableTimeWindow = humanReadableOf(timeWindowAmount, timeWindowUnit);
        this.messageSuffix = KiwiStrings.format(" error(s) created or updated in last %s on host %s (%s:%s)",
                humanReadableTimeWindow, serviceDetails.getHostName(),
                serviceDetails.getIpAddress(),
                serviceDetails.getApplicationPort());
        LOG.debug("Recent errors health check is using time window {}", humanReadableTimeWindow);
    }

    private static String humanReadableOf(long timeWindowAmount, TemporalUnit timeWindowUnit) {
        var millis = Duration.of(timeWindowAmount, timeWindowUnit).toMillis();

        return DurationFormatUtils
                .formatDurationWords(millis, SUPPRESS_LEADING_ZERO_ELEMENTS, SUPPRESS_TRAILING_ZERO_ELEMENTS);
    }

    /**
     * @return the time window as a {@link Duration}
     */
    public Duration getTimeWindow() {
        return Duration.of(timeWindowAmount, timeWindowUnit);
    }

    /**
     * Performs the health check.
     *
     * @return the healthy or unhealthy Result
     */
    @Override
    protected Result check() {
        var now = ZonedDateTime.now(ZoneOffset.UTC);
        var referenceDate = now.minus(timeWindowAmount, timeWindowUnit);
        var countResult = getRecentErrorCount(referenceDate);

        var exception = countResult.getRight();
        if (nonNull(exception)) {
            return newUnhealthyResult(exception, "Error executing recent error count database query");
        }

        var recentErrorCount = requireNonNull(countResult.getLeft(), "Count cannot be null if there is no exception!");
        LOG.trace("Counted {}{}", recentErrorCount, messageSuffix);

        if (recentErrorCount > 0) {
            return newUnhealthyResult(recentErrorCount + messageSuffix);
        }

        return newHealthyResult("No" + messageSuffix);
    }

    private Pair<Long, Exception> getRecentErrorCount(ZonedDateTime referenceDate) {
        try {
            var count = errorDao.countUnresolvedErrorsOnHostSince(
                    referenceDate,
                    serviceDetails.getHostName(),
                    serviceDetails.getIpAddress());
            return Pair.of(count, null);
        } catch (Exception e) {
            return Pair.of(null, e);
        }
    }
}
