package org.kiwiproject.dropwizard.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.lifecycle.setup.ScheduledExecutorServiceBuilder;
import io.dropwizard.util.Duration;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.dropwizard.error.ErrorContextBuilder.DaoType;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.dropwizard.error.dao.jdbi3.Jdbi3ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdk.ConcurrentMapApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdk.JdbcApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdk.NoOpApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.health.TimeWindow;
import org.kiwiproject.dropwizard.error.job.CleanupApplicationErrorsJob;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;
import org.kiwiproject.dropwizard.error.resource.ApplicationErrorResource;
import org.kiwiproject.dropwizard.error.resource.GotErrorsResource;
import org.kiwiproject.dropwizard.jdbi3.Jdbi3Builders;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;
import org.kiwiproject.test.junit.jupiter.PostgresLiquibaseTestExtension;
import org.kiwiproject.validation.KiwiConstraintViolations;
import org.kiwiproject.validation.KiwiValidations;
import org.postgresql.Driver;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The majority of the tests here use an in-memory H2 database, but there are several tests that use
 * Postgres via the {@link PostgresLiquibaseTestExtension}, which is registered at the class level.
 */
@DisplayName("ErrorContextBuilder")
@ExtendWith(SoftAssertionsExtension.class)
class ErrorContextBuilderTest {

    @RegisterExtension
    static final PostgresLiquibaseTestExtension POSTGRES =
            new PostgresLiquibaseTestExtension("dropwizard-app-errors-migrations.xml");

    private Environment environment;
    private ServiceDetails serviceDetails;
    private long timeWindowAmount;
    private ChronoUnit timeWindowUnit;
    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() {
        environment = DropwizardMockitoMocks.mockEnvironment();
        serviceDetails = new ServiceDetails("localhost", "127.0.0.1", 8080);
        timeWindowAmount = 20;
        timeWindowUnit = ChronoUnit.MINUTES;

        executor = mock(ScheduledExecutorService.class);
        var executorBuilder = mock(ScheduledExecutorServiceBuilder.class);
        when(executorBuilder.build()).thenReturn(executor);
        when(environment.lifecycle().scheduledExecutorService(anyString(), anyBoolean()))
                .thenReturn(executorBuilder);
    }

    @AfterEach
    void tearDown() {
        ApplicationError.clearPersistentHostInformation();
    }

    @Nested
    class ShouldThrowIllegalArgumentException {

        @Test
        void whenNoEnvironmentIsProvided(SoftAssertions softly) {
            var builder = ErrorContextBuilder.newInstance()
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .timeWindowValue(timeWindowAmount)
                    .timeWindowUnit(timeWindowUnit);

            assertIllegalArgumentExceptionThrownBuilding(
                    softly, builder, "Dropwizard Environment cannot be null");
        }

        @Test
        void whenNoServiceDetailsIsProvided(SoftAssertions softly) {
            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .dataStoreType(DataStoreType.NOT_SHARED)
                    .timeWindowValue(timeWindowAmount)
                    .timeWindowUnit(timeWindowUnit);

            assertIllegalArgumentExceptionThrownBuilding(
                    softly, builder, "serviceDetails cannot be null");
        }

        @ParameterizedTest
        @ValueSource(longs = { -15, -1, 0 })
        void whenTimeWindowValueIsNotPositive(long amount, SoftAssertions softly) {
            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .timeWindowValue(amount)
                    .timeWindowUnit(timeWindowUnit);

            assertIllegalArgumentExceptionThrownBuilding(softly, builder, "timeWindowValue must be positive");
        }

        @Test
        void whenCleanupConfigIsNotValid(SoftAssertions softly) {
            var cleanupConfig = new CleanupConfig();
            cleanupConfig.setCleanupStrategy(null);
            cleanupConfig.setInitialJobDelay(Duration.seconds(-1));

            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .timeWindowValue(timeWindowAmount)
                    .timeWindowUnit(timeWindowUnit)
                    .cleanupConfig(cleanupConfig);

            var violations = KiwiValidations.validate(cleanupConfig);
            var expectedMessage = KiwiConstraintViolations.simpleCombinedErrorMessage(violations);
            assertIllegalArgumentExceptionThrownBuilding(softly, builder, expectedMessage);
        }

        private void assertIllegalArgumentExceptionThrownBuilding(SoftAssertions softly,
                                                                  ErrorContextBuilder builder,
                                                                  String expectedMessage) {
            softly.assertThatThrownBy(builder::buildInMemoryH2)
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);

            softly.assertThatThrownBy(() ->
                            builder.buildWithJdbc(mock(DataSourceFactory.class)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);

            softly.assertThatThrownBy(() ->
                            builder.buildWithJdbi3(mock(Jdbi.class)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedMessage);
        }
    }

    @Nested
    class CleanupJob {

        @Test
        void shouldHaveCleanupApplicationErrorsJob() {
            var cleanupConfig = new CleanupConfig();
            cleanupConfig.setCleanupJobName("foo-%d");

            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .cleanupConfig(cleanupConfig)
                    .dataStoreType(DataStoreType.NOT_SHARED);

            builder.buildInMemoryH2();

            var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
            builder.buildWithJdbi3(dataSourceFactory);

            var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
            builder.buildWithJdbi3(jdbi);

            // We are checking 3 times here because this test is creating 3 separate ErrorContext objects, and we need
            // to verify that each one sets up the cleanup job.
            verify(environment.lifecycle(), times(3)).scheduledExecutorService(cleanupConfig.getCleanupJobName(), true);
            verify(executor, times(3))
                    .scheduleWithFixedDelay(any(CleanupApplicationErrorsJob.class),
                            eq(cleanupConfig.getInitialJobDelay().toMinutes()),
                            eq(cleanupConfig.getJobInterval().toMinutes()),
                            eq(TimeUnit.MINUTES));
        }

        @Test
        void shouldAllowSkippingCleanupJobRegistration() {
            var builder = ErrorContextBuilder.newInstance()
                    .skipCleanupJob()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED);

            builder.buildInMemoryH2();

            var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
            builder.buildWithJdbc(dataSourceFactory);

            var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
            builder.buildWithJdbi3(jdbi);

            verifyNoInteractions(executor);
        }
    }

    @Nested
    class Resources {

        @Test
        void shouldRegisterResourcesByDefault() {
            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED);

            builder.buildWithConcurrentMapDao();

            var jersey = environment.jersey();
            verify(jersey).register(isA(ApplicationErrorResource.class));
            verify(jersey).register(isA(GotErrorsResource.class));
            verifyNoMoreInteractions(jersey);
        }

        @Test
        void shouldAllowSkippingResourceRegistration() {
            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED)
                    .skipErrorsResource()
                    .skipGotErrorsResource();

            builder.buildWithConcurrentMapDao();

            var jersey = environment.jersey();
            verifyNoInteractions(jersey);
        }
    }

    @Nested
    class RecentApplicationErrorsHealthCheck {

        @Test
        void shouldHaveRecentErrorsHealthCheckWithDefaultTimeWindow(SoftAssertions softly) {
            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED);

            assertDefaultTimeWindow(softly, builder.buildInMemoryH2());

            var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
            assertDefaultTimeWindow(softly, builder.buildWithJdbi3(dataSourceFactory));

            var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
            assertDefaultTimeWindow(softly, builder.buildWithJdbi3(jdbi));
        }

        @Test
        void shouldIgnoreNullTimeWindowUnitArguments(SoftAssertions softly) {
            var builder = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED)
                    .timeWindowUnit(null);

            assertDefaultTimeWindow(softly, builder.buildInMemoryH2());
        }

        @Test
        void shouldAllowSkippingHealthCheckRegistration(SoftAssertions softly) {
            var builder = ErrorContextBuilder.newInstance()
                    .skipHealthCheck()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED);

            assertNoHealthCheck(softly, builder.buildInMemoryH2());

            var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
            assertNoHealthCheck(softly, builder.buildWithJdbc(dataSourceFactory));

            var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
            assertNoHealthCheck(softly, builder.buildWithJdbi3(jdbi));
        }

        private void assertNoHealthCheck(SoftAssertions softly, ErrorContext errorContext) {
            softly.assertThat(errorContext.recentErrorsHealthCheck()).isEmpty();

            var healthChecks = environment.healthChecks();
            verify(healthChecks, never())
                    .register(eq("recentErrorsHealthCheck"), isA(RecentErrorsHealthCheck.class));
        }

        @Nested
        class TimeWindowInBuilder {

            @Test
            void shouldIgnoreNullArgument(SoftAssertions softly) {
                var builder = ErrorContextBuilder.newInstance()
                        .environment(environment)
                        .serviceDetails(serviceDetails)
                        .dataStoreType(DataStoreType.NOT_SHARED)
                        .timeWindow(null);

                assertDefaultTimeWindow(softly, builder.buildInMemoryH2());

                var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
                assertDefaultTimeWindow(softly, builder.buildWithJdbi3(dataSourceFactory));

                var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
                assertDefaultTimeWindow(softly, builder.buildWithJdbi3(jdbi));
            }

            @Test
            void shouldOverrideTimeWindowValueAndUnit(SoftAssertions softly) {
                var builder = ErrorContextBuilder.newInstance()
                        .environment(environment)
                        .serviceDetails(serviceDetails)
                        .dataStoreType(DataStoreType.NOT_SHARED)
                        .timeWindow(new TimeWindow(Duration.hours(1)))
                        .timeWindowValue(45)
                        .timeWindowUnit(ChronoUnit.MINUTES);

                var expectedAmount = 60;
                var expectedUnit = ChronoUnit.MINUTES;

                assertTimeWindow(softly, builder.buildInMemoryH2(), expectedAmount, expectedUnit);

                var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
                assertTimeWindow(softly, builder.buildWithJdbc(dataSourceFactory), expectedAmount, expectedUnit);

                var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
                assertTimeWindow(softly, builder.buildWithJdbi3(jdbi), expectedAmount, expectedUnit);
            }
        }

        private void assertDefaultTimeWindow(SoftAssertions softly, ErrorContext errorContext) {
            assertTimeWindow(softly, errorContext, TimeWindow.DEFAULT_TIME_WINDOW_MINUTES, ChronoUnit.MINUTES);
        }

        private void assertTimeWindow(SoftAssertions softly,
                                      ErrorContext errorContext,
                                      long timeWindowAmount,
                                      TemporalUnit timeWindowUnit) {
            var healthCheck = errorContext.recentErrorsHealthCheck().orElseThrow();
            softly.assertThat(healthCheck.getTimeWindowAmount()).isEqualTo(timeWindowAmount);
            softly.assertThat(healthCheck.getTimeWindowUnit()).isEqualTo(timeWindowUnit);
        }
    }

    @Nested
    class DataStoreTypeInBuilder {

        @Test
        void shouldIgnoreNullArguments() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(null).buildInMemoryH2();

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
        }
    }

    @Nested
    class BuildInMemoryH2 {

        @Test
        void shouldBuildJdbi3Context(SoftAssertions softly) {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildInMemoryH2();

            softly.assertThat(errorContext).isExactlyInstanceOf(Jdbi3ErrorContext.class);
            softly.assertThat(errorContext.errorDao()).isInstanceOf(Jdbi3ApplicationErrorDao.class);
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildInMemoryH2();

            verifyRegistersJerseyResources();
        }

        @Test
        void shouldRegisterHealthCheck() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildInMemoryH2();

            verifyRegistersHealthChecks();
        }

        @Test
        void shouldForce_NOT_SHARED_DataStoreType() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .buildInMemoryH2();

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
        }
    }

    @Nested
    class BuildWithJdbc_UsingDataStoreFactory {

        private DataSourceFactory dataSourceFactory;

        @BeforeEach
        void setUp() {
            dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
        }

        @Test
        void shouldBuildJdbcContext() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbc(dataSourceFactory);

            assertAll(
                    () -> assertThat(errorContext).isExactlyInstanceOf(SimpleErrorContext.class),
                    () -> assertThat(errorContext.errorDao()).isInstanceOf(JdbcApplicationErrorDao.class)
            );
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbc(dataSourceFactory);

            verifyRegistersJerseyResources();
        }

        @Test
        void shouldRegisterHealthCheck() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbc(dataSourceFactory);

            verifyRegistersHealthChecks();
        }

        @Test
        void shouldDetermineDataStoreTypeIfNotSet() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbc(dataSourceFactory);

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
        }

        @Test
        void shouldForce_NOT_SHARED_ForEmbeddedH2Databases() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .buildWithJdbc(dataSourceFactory);

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
        }

        @Test
        void shouldWorkWithPostgres() {
            var postgresDataSourceFactory = newPostgresDataSourceFactory();

            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbc(postgresDataSourceFactory);

            assertAll(
                    () -> assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.SHARED),
                    () -> assertThat(errorContext.errorDao()).isNotNull()
            );
        }
    }

    @Nested
    class BuildWithJdbi3_UsingJdbiObject {

        private Jdbi jdbi;

        @BeforeEach
        void setUp() {
            var dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
            jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);
        }

        @Test
        void shouldBuildJdbi3Context(SoftAssertions softly) {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbi3(jdbi);

            softly.assertThat(errorContext).isExactlyInstanceOf(Jdbi3ErrorContext.class);
            softly.assertThat(errorContext.errorDao()).isInstanceOf(Jdbi3ApplicationErrorDao.class);
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbi3(jdbi);

            verifyRegistersJerseyResources();
        }

        @Test
        void shouldRegisterHealthCheck() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbi3(jdbi);

            verifyRegistersHealthChecks();
        }

        @Test
        void shouldAcceptDataStoreType() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .buildWithJdbi3(jdbi);

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.SHARED);
        }

        @Test
        void shouldSetDataStoreTypeTo_SHARED_IfNotAlreadySet() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbi3(jdbi);

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.SHARED);
        }

        @Test
        void shouldWorkWithPostgres(SoftAssertions softly) {
            var dataSourceFactory = newPostgresDataSourceFactory();
            var postgresJdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory);

            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithJdbi3(postgresJdbi);

            softly.assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.SHARED);
            softly.assertThat(errorContext.errorDao()).isNotNull();
        }
    }

    @Nested
    class BuildWithDataStoreFactoryOfType {

        private DataSourceFactory dataSourceFactory;

        @BeforeEach
        void setUp() {
            dataSourceFactory = ApplicationErrorJdbc.createInMemoryH2Database();
        }

        @Test
        void shouldRequireDaoType() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> ErrorContextBuilder.newInstance()
                            .environment(environment)
                            .serviceDetails(serviceDetails)
                            .buildWithDataStoreFactoryOfType(new DataSourceFactory(), null));
        }

        @Test
        void shouldBuildWithJdbcDaoType() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithDataStoreFactoryOfType(dataSourceFactory, DaoType.JDBC);

            assertAll(
                    () -> assertThat(errorContext).isExactlyInstanceOf(SimpleErrorContext.class),
                    () -> assertThat(errorContext.errorDao()).isInstanceOf(JdbcApplicationErrorDao.class)
            );
        }

        @Test
        void shouldBuildWithJdbi3DaoType() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithDataStoreFactoryOfType(dataSourceFactory, DaoType.JDBI3);

            assertAll(
                    () -> assertThat(errorContext).isExactlyInstanceOf(Jdbi3ErrorContext.class),
                    () -> assertThat(errorContext.errorDao()).isInstanceOf(Jdbi3ApplicationErrorDao.class)
            );
        }
    }

    private static DataSourceFactory newPostgresDataSourceFactory() {
        var dataSourceFactory = new DataSourceFactory();
        dataSourceFactory.setDriverClass(Driver.class.getName());
        dataSourceFactory.setUrl(POSTGRES.getTestDataSource().getUrl());
        dataSourceFactory.setUser(POSTGRES.getTestDataSource().getUsername());
        dataSourceFactory.setPassword(POSTGRES.getTestDataSource().getPassword());
        return dataSourceFactory;
    }

    @Nested
    class BuildWithNoOpDao {

        @Test
        void shouldBuildSimpleContext(SoftAssertions softly) {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithNoOpDao();

            softly.assertThat(errorContext).isExactlyInstanceOf(SimpleErrorContext.class);
            softly.assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
            softly.assertThat(errorContext.errorDao()).isInstanceOf(NoOpApplicationErrorDao.class);
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithNoOpDao();

            verifyRegistersJerseyResources();
        }

        @Test
        void shouldRegisterHealthCheck() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithNoOpDao();

            verifyRegistersHealthChecks();
        }

        @Test
        void shouldForce_NOT_SHARED_DataStoreType() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .buildWithNoOpDao();

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
        }
    }

    @Nested
    class BuildWithConcurrentMapDao {

        @Test
        void shouldBuildSimpleContext(SoftAssertions softly) {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithConcurrentMapDao();

            softly.assertThat(errorContext).isExactlyInstanceOf(SimpleErrorContext.class);
            softly.assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
            softly.assertThat(errorContext.errorDao()).isInstanceOf(ConcurrentMapApplicationErrorDao.class);
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithConcurrentMapDao();

            verifyRegistersJerseyResources();
        }

        @Test
        void shouldRegisterHealthCheck() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .buildWithConcurrentMapDao();

            verifyRegistersHealthChecks();
        }

        @Test
        void shouldForce_NOT_SHARED_DataStoreType() {
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .buildWithConcurrentMapDao();

            assertThat(errorContext.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
        }
    }

    @Nested
    class BuildWithDao {

        @Test
        void shouldBuildSimpleContext(SoftAssertions softly) {
            var errorDao = new NoOpApplicationErrorDao();
            var errorContext = ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.SHARED)
                    .buildWithDao(errorDao);

            softly.assertThat(errorContext).isExactlyInstanceOf(SimpleErrorContext.class);
            softly.assertThat(errorContext.dataStoreType())
                    .describedAs("should not force DataStoreType (even if it's wrong)")
                    .isEqualTo(DataStoreType.SHARED);
            softly.assertThat(errorContext.errorDao()).isSameAs(errorDao);
        }

        @Test
        void shouldRegisterResources() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED)
                    .buildWithDao(new NoOpApplicationErrorDao());

            verifyRegistersJerseyResources();
        }

        @Test
        void shouldRegisterHealthCheck() {
            ErrorContextBuilder.newInstance()
                    .environment(environment)
                    .serviceDetails(serviceDetails)
                    .dataStoreType(DataStoreType.NOT_SHARED)
                    .buildWithDao(new NoOpApplicationErrorDao());

            verifyRegistersHealthChecks();
        }
    }

    private void verifyRegistersJerseyResources() {
        var jersey = environment.jersey();

        verify(jersey).register(isA(ApplicationErrorResource.class));
        verify(jersey).register(isA(GotErrorsResource.class));
        verifyNoMoreInteractions(jersey);
    }

    private void verifyRegistersHealthChecks() {
        var healthChecks = environment.healthChecks();
        verify(healthChecks).register(eq("recentApplicationErrors"), isA(RecentErrorsHealthCheck.class));
    }
}
