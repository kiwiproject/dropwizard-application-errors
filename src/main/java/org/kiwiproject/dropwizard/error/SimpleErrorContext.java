package org.kiwiproject.dropwizard.error;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.checkCommonArguments;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerCleanupJobOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerResources;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.setPersistentHostInformationFrom;

import io.dropwizard.core.setup.Environment;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;

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

    SimpleErrorContext(Environment environment,
                       ServiceDetails serviceDetails,
                       ApplicationErrorDao errorDao,
                       ErrorContextOptions options) {

        checkCommonArguments(environment, serviceDetails, options);
        checkArgumentNotNull(errorDao, "ApplicationErrorDao must not be null");
        setPersistentHostInformationFrom(serviceDetails);

        this.errorDao = errorDao;
        this.dataStoreType = options.getDataStoreType();
        this.healthCheck = registerRecentErrorsHealthCheckOrNull(environment, serviceDetails, errorDao, options);

        registerCleanupJobOrNull(environment, errorDao, options);
        registerResources(environment, errorDao, options);
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
