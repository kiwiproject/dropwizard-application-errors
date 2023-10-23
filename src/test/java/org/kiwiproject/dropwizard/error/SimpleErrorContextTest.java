package org.kiwiproject.dropwizard.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdk.NoOpApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;
import org.kiwiproject.dropwizard.error.resource.ApplicationErrorResource;
import org.kiwiproject.dropwizard.error.resource.GotErrorsResource;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;
import org.mockito.verification.VerificationMode;

import java.time.temporal.ChronoUnit;

@DisplayName("SimpleErrorContext")
class SimpleErrorContextTest {

    private Environment environment;
    private ServiceDetails serviceDetails;

    private ApplicationErrorDao errorDao;
    private DataStoreType dataStoreType;
    private long timeWindowAmount;
    private ChronoUnit timeWindowUnit;

    private SimpleErrorContext context;

    @BeforeEach
    void setUp() {
        environment = DropwizardMockitoMocks.mockEnvironment();
        serviceDetails = new ServiceDetails("localhost", "127.0.0.1", 8080);
        errorDao = new NoOpApplicationErrorDao();
        dataStoreType = DataStoreType.NOT_SHARED;
        timeWindowAmount = 1;
        timeWindowUnit = ChronoUnit.HOURS;
    }

    @AfterEach
    void tearDown() {
        ApplicationError.clearPersistentHostInformation();
    }

    @Nested
    class ConstructorAddingHealthCheck {

        @BeforeEach
        void setUp() {
            context = newContextWithAddHealthCheckOf(true);
        }

        @Test
        void shouldRegisterHealthCheck() {
            assertThat(context.recentErrorsHealthCheck()).isPresent();

            var healthChecks = environment.healthChecks();
            verify(healthChecks).register(eq("recentApplicationErrors"), isA(RecentErrorsHealthCheck.class));
            verifyNoMoreInteractions(healthChecks);
        }

        @Test
        void shouldSetDataStoreType() {
            assertThat(context.dataStoreType()).isEqualTo(dataStoreType);
        }

        @Test
        void shouldSetErrorDao() {
            assertThat(context.errorDao()).isSameAs(errorDao);
        }

        @Test
        void shouldRegisterResources() {
            var jersey = environment.jersey();

            verify(jersey).register(isA(ApplicationErrorResource.class));
            verify(jersey).register(isA(GotErrorsResource.class));
            verifyNoMoreInteractions(jersey);
        }
    }

    @Nested
    class ConstructorNotAddingHealthCheck {

        @BeforeEach
        void setUp() {
            context = newContextWithAddHealthCheckOf(false);
        }

        @Test
        void shouldNotRegisterHealthCheck() {
            assertThat(context.recentErrorsHealthCheck()).isEmpty();

            var healthChecks = environment.healthChecks();
            verifyNoInteractions(healthChecks);
        }
    }

    private SimpleErrorContext newContextWithAddHealthCheckOf(boolean addHealthCheck) {
        return new SimpleErrorContext(environment,
                serviceDetails,
                errorDao,
                dataStoreType,
                true,
                true,
                addHealthCheck,
                timeWindowAmount,
                timeWindowUnit,
                false,
                new CleanupConfig()
        );
    }

    @Nested
    class Resources {

        @ParameterizedTest
        @CsvSource(textBlock = """
            true, true
            true, false
            false, true,
            false, false
            """)
        void shouldOptionallyRegisterResources(boolean addErrorsResource, boolean addGotErrorsResource) {
            newContextWithAddResourceOptionsOf(addErrorsResource, addGotErrorsResource);

            var jersey = environment.jersey();
            verify(jersey, timesExpected(addErrorsResource)).register(isA(ApplicationErrorResource.class));
            verify(jersey, timesExpected(addGotErrorsResource)).register(isA(GotErrorsResource.class));
            verifyNoMoreInteractions(jersey);
        }

        private VerificationMode timesExpected(boolean addResource) {
            return addResource ? times(1) : never();
        }
    }

    private SimpleErrorContext newContextWithAddResourceOptionsOf(boolean addErrorsResource,
                                                                  boolean addGotErrorsResource) {
        return new SimpleErrorContext(environment,
                serviceDetails,
                errorDao,
                dataStoreType,
                addErrorsResource,
                addGotErrorsResource,
                true,
                timeWindowAmount,
                timeWindowUnit,
                false,
                new CleanupConfig());
    }
}
