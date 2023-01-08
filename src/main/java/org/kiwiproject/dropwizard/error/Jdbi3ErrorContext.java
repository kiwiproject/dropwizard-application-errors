package org.kiwiproject.dropwizard.error;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.checkCommonArguments;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerCleanupJobOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerResources;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.setPersistentHostInformationFrom;

import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.NoSuchExtensionException;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdbi3.Jdbi3ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;

import java.time.temporal.TemporalUnit;
import java.util.Optional;

/**
 * JDBI 3 implementation of {@link ErrorContext}.
 * <p>
 * Note that the {@code SqlObjectPlugin} <em>must</em> be registered on the {@link Jdbi} instance. Otherwise, we will
 * throw an {@link IllegalStateException} wrapping the {@link NoSuchExtensionException} that JDBI throws. The error
 * message provided by JDBI ({@code "Extension not found"}) is not all that helpful, so our message is more informative.
 *
 * @implNote This class is not public and is subject to change.
 */
@Slf4j
class Jdbi3ErrorContext implements ErrorContext {

    private static final String NO_SUCH_EXTENSION_ERROR_MESSAGE = f(
            "Error creating on-demand {}. Hint: the SqlObjectPlugin must be registered on the Jdbi instance",
            Jdbi3ApplicationErrorDao.class.getName());

    private final DataStoreType dataStoreType;
    private final ApplicationErrorDao errorDao;
    private final RecentErrorsHealthCheck healthCheck;

    Jdbi3ErrorContext(Environment environment,
                      ServiceDetails serviceDetails,
                      Jdbi jdbi,
                      DataStoreType dataStoreType,
                      boolean addHealthCheck,
                      long timeWindowValue,
                      TemporalUnit timeWindowUnit,
                      boolean addCleanupJob,
                      CleanupConfig cleanupConfig) {

        checkCommonArguments(environment, serviceDetails, dataStoreType, timeWindowValue, timeWindowUnit);
        checkArgumentNotNull(jdbi, "Jdbi (version 3) instance cannot be null");
        setPersistentHostInformationFrom(serviceDetails);

        this.dataStoreType = dataStoreType;
        this.errorDao = getOnDemandErrorDao(jdbi);
        this.healthCheck = registerRecentErrorsHealthCheckOrNull(
                addHealthCheck, environment, errorDao, serviceDetails, timeWindowValue, timeWindowUnit);

        registerCleanupJobOrNull(addCleanupJob, environment, errorDao, cleanupConfig);
        registerResources(environment, errorDao, dataStoreType);
    }

    private static Jdbi3ApplicationErrorDao getOnDemandErrorDao(Jdbi jdbi) {
        try {
            return jdbi.onDemand(Jdbi3ApplicationErrorDao.class);
        } catch (NoSuchExtensionException e) {
            throw new IllegalStateException(NO_SUCH_EXTENSION_ERROR_MESSAGE, e);
        }
    }

    @Override
    public DataStoreType dataStoreType() {
        return dataStoreType;
    }

    @Override
    public ApplicationErrorDao errorDao() {
        return errorDao;
    }

    @Override
    public Optional<RecentErrorsHealthCheck> recentErrorsHealthCheck() {
        return Optional.ofNullable(healthCheck);
    }
}
