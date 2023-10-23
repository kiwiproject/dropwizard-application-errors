package org.kiwiproject.dropwizard.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.job.CleanupApplicationErrorsJob;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;
import org.kiwiproject.dropwizard.error.resource.ApplicationErrorResource;
import org.kiwiproject.dropwizard.error.resource.GotErrorsResource;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@DisplayName("ApplicationErrorUtilities")
class ErrorContextUtilitiesTest {

    private Environment environment;
    private ServiceDetails serviceDetails;

    @BeforeEach
    void setUp() {
        environment = DropwizardMockitoMocks.mockEnvironment();
        serviceDetails = new ServiceDetails("localhost", "127.0.0.1", 8080);
    }

    @AfterEach
    void tearDown() {
        ApplicationError.clearPersistentHostInformation();
    }

    @Nested
    class CheckCommonArguments {

        @Test
        void shouldNotThrowException_GivenValidArguments() {
            assertThatCode(() ->
                    ErrorContextUtilities.checkCommonArguments(environment, serviceDetails, DataStoreType.SHARED, 10, ChronoUnit.MINUTES))
                    .doesNotThrowAnyException();
        }

        @Test
        void shouldThrowIllegalArgumentException_GivenNullEnvironment() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            ErrorContextUtilities.checkCommonArguments(null, serviceDetails, DataStoreType.SHARED, 15, ChronoUnit.MINUTES));
        }

        @Test
        void shouldThrowIllegalArgumentException_GivenNullServiceDetails() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            ErrorContextUtilities.checkCommonArguments(environment, null, DataStoreType.NOT_SHARED, 15, ChronoUnit.MINUTES));
        }

        @Test
        void shouldThrowIllegalArgumentException_GivenNullDataStoreType() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            ErrorContextUtilities.checkCommonArguments(environment, serviceDetails, null, 15, ChronoUnit.MINUTES));
        }

        @ParameterizedTest
        @ValueSource(longs = {-10, -5, -1, 0})
        void shouldThrowIllegalArgumentException_GivenZeroOrNegativeTimeWindowValue(long value) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            ErrorContextUtilities.checkCommonArguments(environment, serviceDetails, DataStoreType.SHARED, value, ChronoUnit.MINUTES));
        }

        @Test
        void shouldThrowIllegalArgumentException_GivenNullTimeWindowUnit() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            ErrorContextUtilities.checkCommonArguments(environment, serviceDetails, DataStoreType.SHARED, 15, null));
        }
    }

    @Nested
    class SetPersistentHostInformationFrom {

        @Test
        void shouldSetFromServiceDetails() {
            var hostInfo = ErrorContextUtilities.setPersistentHostInformationFrom(serviceDetails);

            assertThat(hostInfo.getHostName()).isEqualTo(serviceDetails.getHostName());
            assertThat(hostInfo.getIpAddress()).isEqualTo(serviceDetails.getIpAddress());
            assertThat(hostInfo.getPort()).isEqualTo(serviceDetails.getApplicationPort());
        }

        @Test
        void shouldThrowIllegalArgumentException_GivenNullArgument() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ErrorContextUtilities.setPersistentHostInformationFrom(null));
        }
    }

    @Nested
    class RegisterResources {

        private ApplicationErrorDao errorDao;
        private JerseyEnvironment jersey;

        @BeforeEach
        void setUp() {
            errorDao = mock(ApplicationErrorDao.class);
            jersey = environment.jersey();
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextUtilities.registerResources(environment, errorDao, DataStoreType.SHARED, true, true);

            verify(jersey).register(isA(ApplicationErrorResource.class));
            verify(jersey).register(isA(GotErrorsResource.class));
            verifyNoMoreInteractions(jersey);
        }

        @Test
        void shouldNotRegisterResources_WhenTheyAreNotWanted() {
            ErrorContextUtilities.registerResources(environment, errorDao, DataStoreType.SHARED, false, false);

            verify(jersey, never()).register(isA(ApplicationErrorResource.class));
            verify(jersey, never()).register(isA(GotErrorsResource.class));
            verifyNoMoreInteractions(jersey);
        }
    }

    @Nested
    class RegisterRecentErrorsHealthCheckOrNull {

        private ApplicationErrorDao errorDao;
        private HealthCheckRegistry healthChecks;
        private long timeWindowAmount;
        private ChronoUnit timeWindowUnit;

        @BeforeEach
        void setUp() {
            errorDao = mock(ApplicationErrorDao.class);
            healthChecks = environment.healthChecks();
            timeWindowAmount = 25;
            timeWindowUnit = ChronoUnit.MINUTES;
        }

        @Test
        void shouldRegisterHealthCheck() {
            var healthCheck = ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull(
                    true, environment, errorDao, serviceDetails, timeWindowAmount, timeWindowUnit);

            assertThat(healthCheck).isNotNull();
            assertThat(healthCheck.getTimeWindow()).isEqualTo(Duration.of(timeWindowAmount, timeWindowUnit));

            verify(healthChecks).register(eq("recentApplicationErrors"), isA(RecentErrorsHealthCheck.class));
            verifyNoMoreInteractions(healthChecks);
        }

        @SuppressWarnings("ConstantValue")
        @Test
        void shouldSkipRegisteringHealthCheck() {
            var healthCheck = ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull(
                    false, environment, errorDao, serviceDetails, timeWindowAmount, timeWindowUnit);

            assertThat(healthCheck).isNull();

            verifyNoInteractions(healthChecks);
        }
    }

    @Nested
    class RegisterCleanupJobOrNull {
        private ApplicationErrorDao errorDao;
        private CleanupConfig config;
        private LifecycleEnvironment lifecycleEnvironment;

        @BeforeEach
        void setUp() {
            errorDao = mock(ApplicationErrorDao.class);
            config = new CleanupConfig();
            lifecycleEnvironment = environment.lifecycle();
        }

        @Test
        void shouldRegisterCleanupJob() {
            var executor = mock(ScheduledExecutorService.class);
            var executorBuilder = mock(ScheduledExecutorServiceBuilder.class);
            when(executorBuilder.build()).thenReturn(executor);
            when(lifecycleEnvironment.scheduledExecutorService(config.getCleanupJobName(), true))
                    .thenReturn(executorBuilder);

            var job = ErrorContextUtilities.registerCleanupJobOrNull(
                    true, environment, errorDao, config);

            assertThat(job).isNotNull();

            verify(lifecycleEnvironment).scheduledExecutorService(config.getCleanupJobName(), true);
            verify(executor).scheduleWithFixedDelay(any(CleanupApplicationErrorsJob.class),
                    eq(config.getInitialJobDelay().toMinutes()), eq(config.getJobInterval().toMinutes()),
                    eq(TimeUnit.MINUTES));
        }

        @SuppressWarnings("ConstantValue")
        @Test
        void shouldSkipRegisteringHealthCheck() {
            var job = ErrorContextUtilities.registerCleanupJobOrNull(
                    false, environment, errorDao, config);

            assertThat(job).isNull();

            verifyNoInteractions(lifecycleEnvironment);
        }
    }
}
