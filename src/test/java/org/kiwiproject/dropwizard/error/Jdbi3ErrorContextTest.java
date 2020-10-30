package org.kiwiproject.dropwizard.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.dropwizard.setup.Environment;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.jdbi3.Jdbi3ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.resource.ApplicationErrorResource;
import org.kiwiproject.dropwizard.error.resource.GotErrorsResource;
import org.kiwiproject.test.dropwizard.mockito.DropwizardMockitoMocks;
import org.kiwiproject.test.h2.H2FileBasedDatabase;
import org.kiwiproject.test.junit.jupiter.H2Database;
import org.kiwiproject.test.junit.jupiter.H2FileBasedDatabaseExtension;

import java.time.temporal.ChronoUnit;

@DisplayName("Jdbi3ErrorContext")
@ExtendWith(H2FileBasedDatabaseExtension.class)
class Jdbi3ErrorContextTest {

    private Environment environment;
    private ServiceDetails serviceDetails;
    private Jdbi jdbi;
    private long timeWindowAmount;
    private ChronoUnit timeWindowUnit;

    @BeforeEach
    void setUp(@H2Database H2FileBasedDatabase database) {
        environment = DropwizardMockitoMocks.mockEnvironment();
        serviceDetails = new ServiceDetails("localhost", "127.0.0.1", 8080);

        jdbi = Jdbi.create(database.getDataSource()).installPlugin(new SqlObjectPlugin());

        timeWindowAmount = 20;
        timeWindowUnit = ChronoUnit.MINUTES;
    }

    @AfterEach
    void tearDown() {
        ApplicationError.clearPersistentHostInformation();
    }

    @Nested
    class Constructor {

        private Jdbi3ErrorContext context;

        @Nested
        class WhenRegisteringHealthCheck {

            @BeforeEach
            void setUp() {
                context = newContextWithHealthCheck(true);
            }

            @Test
            void shouldSetDataStoreType() {
                assertThat(context.dataStoreType()).isEqualTo(DataStoreType.NOT_SHARED);
            }

            @Test
            void shouldSetErrorDao() {
                assertThat(context.errorDao()).isNotNull();
            }

            @Test
            void shouldRegisterHealthCheck() {
                assertThat(context.recentErrorsHealthCheck()).isPresent();

                var healthChecks = environment.healthChecks();
                verify(healthChecks).register(eq("recentApplicationErrors"), isA(RecentErrorsHealthCheck.class));
                verifyNoMoreInteractions(healthChecks);
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
        class WhenNotRegisteringHealthCheck {

            @BeforeEach
            void setUp() {
                context = newContextWithHealthCheck(false);
            }

            @Test
            void shouldSkipRegisteringHealthCheck() {
                assertThat(context.recentErrorsHealthCheck()).isEmpty();

                var healthChecks = environment.healthChecks();
                verifyNoInteractions(healthChecks);
            }
        }

        @Nested
        class WhenJdbiDoesNotHaveSqlObjectPlugin {

            @Test
            void shouldThrowIllegalStateException(@H2Database H2FileBasedDatabase database) {
                // Need to create new Jdbi without SqlObjectPlugin
                jdbi = Jdbi.create(database.getDataSource());

                assertThatIllegalStateException()
                        .isThrownBy(() -> newContextWithHealthCheck(false))
                        .withMessageStartingWith("Error creating on-demand " + Jdbi3ApplicationErrorDao.class.getName())
                        .withMessageEndingWith(" Hint: the SqlObjectPlugin must be registered on the Jdbi instance");
            }
        }

        private Jdbi3ErrorContext newContextWithHealthCheck(boolean addHealthCheck) {
            return new Jdbi3ErrorContext(environment,
                    serviceDetails,
                    jdbi,
                    DataStoreType.NOT_SHARED,
                    addHealthCheck,
                    timeWindowAmount,
                    timeWindowUnit);
        }
    }
}
