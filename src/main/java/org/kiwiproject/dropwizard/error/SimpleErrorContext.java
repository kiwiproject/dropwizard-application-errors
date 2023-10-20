package org.kiwiproject.dropwizard.error;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.checkCommonArguments;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerCleanupJobOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerResources;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.setPersistentHostInformationFrom;

import io.dropwizard.core.setup.Environment;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;

import java.time.temporal.TemporalUnit;
import java.util.Optional;

/**
 * A "simple" implementation of {@link ErrorContext} that accepts an {@link ApplicationErrorDao} instance.
 *
 * @implNote This class is not public and is subject to change.
 */
class SimpleErrorContext implements ErrorContext {

    private final ApplicationErrorDao errorDao;
    private final DataStoreType dataStoreType;
    private final RecentErrorsHealthCheck healthCheck;

    public SimpleErrorContext(Environment environment,
                              ServiceDetails serviceDetails,
                              ApplicationErrorDao errorDao,
                              DataStoreType dataStoreType,
                              boolean addErrorsResource,
                              boolean addGotErrorsResource,
                              boolean addHealthCheck,
                              long timeWindowValue,
                              TemporalUnit timeWindowUnit,
                              boolean addCleanupJob,
                              CleanupConfig cleanupConfig) {

        checkCommonArguments(environment, serviceDetails, dataStoreType, timeWindowValue, timeWindowUnit);
        checkArgumentNotNull(errorDao, "ApplicationErrorDao must not be null");
        setPersistentHostInformationFrom(serviceDetails);

        this.errorDao = errorDao;
        this.dataStoreType = dataStoreType;
        this.healthCheck = registerRecentErrorsHealthCheckOrNull(
                addHealthCheck, environment, errorDao, serviceDetails, timeWindowValue, timeWindowUnit);

        registerCleanupJobOrNull(addCleanupJob, environment, errorDao, cleanupConfig);
        registerResources(environment, errorDao, dataStoreType, addErrorsResource, addGotErrorsResource);
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
