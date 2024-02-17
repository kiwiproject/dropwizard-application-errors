package org.kiwiproject.dropwizard.error.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.metrics.health.HealthCheckResults.SEVERITY_DETAIL;
import static org.kiwiproject.test.assertj.dropwizard.metrics.HealthCheckResultAssertions.assertThatHealthCheck;
import static org.kiwiproject.test.util.DateTimeTestHelper.assertTimeDifferenceWithinTolerance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension.HostInfo;
import org.kiwiproject.metrics.health.HealthStatus;
import org.mockito.ArgumentMatcher;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

@DisplayName("RecentErrorsHealthCheck")
@ExtendWith({ApplicationErrorExtension.class, SoftAssertionsExtension.class})
@Slf4j
class RecentErrorsHealthCheckTest {

    private RecentErrorsHealthCheck healthCheck;
    private ApplicationErrorDao errorDao;
    private ServiceDetails serviceDetails;
    private String hostName;
    private String ipAddress;
    private int port;

    @BeforeEach
    void setUp(@HostInfo PersistentHostInformation hostInformation) {
        errorDao = mock(ApplicationErrorDao.class);
        hostName = hostInformation.getHostName();
        ipAddress = hostInformation.getIpAddress();
        port = hostInformation.getPort();
        serviceDetails = ServiceDetails.builder()
                .hostName(hostName)
                .ipAddress(ipAddress)
                .applicationPort(port)
                .build();
        healthCheck = new RecentErrorsHealthCheck(errorDao, serviceDetails);
    }

    @Nested
    class ShouldConstruct {

        @ParameterizedTest
        @CsvSource({
                "15, MINUTES, 15 minutes",
                "60, MINUTES, 1 hour",
                "120, MINUTES, 2 hours",
                "270, MINUTES, 4 hours 30 minutes",
                "720, MINUTES, 12 hours",
                "72, HOURS, 3 days",
        })
        void fromAmountAndChronoUnit(long windowAmount, ChronoUnit windowUnit, String expectedReadableWindow) {
            var healthCheck = newHealthCheck(windowAmount, windowUnit);

            assertThat(healthCheck.getTimeWindowAmount()).isEqualTo(windowAmount);
            assertThat(healthCheck.getTimeWindowUnit()).isEqualTo(windowUnit);
            assertThat(healthCheck.getTimeWindow()).isEqualTo(java.time.Duration.of(windowAmount, windowUnit));
            assertThat(healthCheck.getHumanReadableTimeWindow()).isEqualTo(expectedReadableWindow);
        }

        private RecentErrorsHealthCheck newHealthCheck(long timeWindowAmount, TemporalUnit timeWindowUnit) {
            return new RecentErrorsHealthCheck(errorDao, serviceDetails, timeWindowAmount, timeWindowUnit);
        }

        @ParameterizedTest
        @CsvSource({
                "15 minutes, 15 minutes",
                "60m, 1 hour",
                "120 minutes, 2 hours",
                "270m, 4 hours 30 minutes",
                "720m, 12 hours",
                "72 hours, 3 days",
        })
        void fromTimeWindowObject(String durationString, String expectedReadableWindow) {
            var duration = Duration.parse(durationString);
            var healthCheck = newHealthCheck(new TimeWindow(duration));

            var javaTimeDuration = duration.toJavaDuration();
            assertThat(healthCheck.getTimeWindowAmount()).isEqualTo(javaTimeDuration.toMillis());
            assertThat(healthCheck.getTimeWindowUnit()).isEqualTo(ChronoUnit.MILLIS);
            assertThat(healthCheck.getTimeWindow()).isEqualTo(javaTimeDuration);
            assertThat(healthCheck.getHumanReadableTimeWindow()).isEqualTo(expectedReadableWindow);
        }

        private RecentErrorsHealthCheck newHealthCheck(TimeWindow timeWindow) {
            return new RecentErrorsHealthCheck(errorDao, serviceDetails, timeWindow);
        }

        @ParameterizedTest
        @CsvSource({
                "15 minutes, 15 minutes",
                "60m, 1 hour",
                "120 minutes, 2 hours",
                "270m, 4 hours 30 minutes",
                "720m, 12 hours",
                "72 hours, 3 days",
        })
        void fromDropwizardDuration(String durationString, String expectedReadableWindow) {
            var duration = Duration.parse(durationString);
            var healthCheck = newHealthCheck(duration);

            assertThat(healthCheck.getTimeWindow()).isEqualTo(duration.toJavaDuration());
            assertThat(healthCheck.getHumanReadableTimeWindow()).isEqualTo(expectedReadableWindow);
        }

        private RecentErrorsHealthCheck newHealthCheck(Duration timeWindow) {
            return new RecentErrorsHealthCheck(errorDao, serviceDetails, timeWindow);
        }
    }

    @Nested
    class ShouldBeHealthy {

        @Test
        void whenNoApplicationErrors_WithinTimeWindow() {
            when(errorDao.countUnresolvedErrorsOnHostSince(any(ZonedDateTime.class), anyString(), anyString()))
                    .thenReturn(0L);

            var now = ZonedDateTime.now(ZoneOffset.UTC);
            assertThatHealthCheck(healthCheck)
                    .isHealthy()
                    .hasMessage("No error(s) created or updated in last 15 minutes on host {} ({}:{})",
                            hostName, ipAddress, port)
                    .hasNoError()
                    .hasDetail(SEVERITY_DETAIL, HealthStatus.OK.name());

            verify(errorDao).countUnresolvedErrorsOnHostSince(
                    argThat(isAboutFifteenMinutesBefore(now)), eq(hostName), eq(ipAddress));
        }
    }

    @Nested
    class ShouldBeUnhealthy {

        @ParameterizedTest
        @ValueSource(longs = {1, 2, 5, 10, 75})
        void whenThereAreApplicationErrors_WithinTimeWindow(long errorCount) {
            when(errorDao.countUnresolvedErrorsOnHostSince(any(ZonedDateTime.class), anyString(), anyString()))
                    .thenReturn(errorCount);

            var now = ZonedDateTime.now(ZoneOffset.UTC);
            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasMessage("{} error(s) created or updated in last 15 minutes on host {} ({}:{})",
                            errorCount, hostName, ipAddress, port)
                    .hasNoError()
                    .hasDetail(SEVERITY_DETAIL, HealthStatus.WARN.name());

            verify(errorDao).countUnresolvedErrorsOnHostSince(
                    argThat(isAboutFifteenMinutesBefore(now)), eq(hostName), eq(ipAddress));
        }

        @Test
        void whenApplicationErrorDao_ThrowsException() {
            when(errorDao.countUnresolvedErrorsOnHostSince(any(ZonedDateTime.class), anyString(), anyString()))
                    .thenThrow(new UnableToExecuteStatementException("Error executing SQL"));

            assertThatHealthCheck(healthCheck)
                    .isUnhealthy()
                    .hasErrorExactlyInstanceOf(UnableToExecuteStatementException.class)
                    .hasMessage("Error executing recent error count database query")
                    .hasDetail(SEVERITY_DETAIL, HealthStatus.CRITICAL.name());
        }
    }

    private ArgumentMatcher<ZonedDateTime> isAboutFifteenMinutesBefore(ZonedDateTime dateTime) {
        return referenceDate -> {
            var approximateWindowEndDateTime = dateTime.minusMinutes(15);
            var diffMillis = ChronoUnit.MILLIS.between(approximateWindowEndDateTime, referenceDate);
            LOG.debug("referenceDate: {} ; approximateWindowEndDateTime: {} -> diff: {}ms",
                    referenceDate, approximateWindowEndDateTime, diffMillis);
            assertTimeDifferenceWithinTolerance("since", approximateWindowEndDateTime, referenceDate);
            return true;
        };
    }
}
