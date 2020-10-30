package org.kiwiproject.dropwizard.error;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiPreconditions.requireNotNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.checkCommonArguments;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerResources;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.setPersistentHostInformationFrom;

import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdbi3.Jdbi3ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

import java.time.temporal.TemporalUnit;
import java.util.Optional;

/**
 * JDBI 3 implementation of {@link ErrorContext}.
 *
 * @implNote This is not public and is subject to change.
 */
@Slf4j
class Jdbi3ErrorContext implements ErrorContext {

    private final DataStoreType dataStoreType;
    private final ApplicationErrorDao errorDao;
    private final RecentErrorsHealthCheck healthCheck;

    Jdbi3ErrorContext(Environment environment,
                      ServiceDetails serviceDetails,
                      Jdbi jdbi,
                      DataStoreType dataStoreType,
                      boolean addHealthCheck,
                      long timeWindowValue,
                      TemporalUnit timeWindowUnit) {

        checkCommonArguments(environment, serviceDetails, timeWindowValue, timeWindowUnit);
        checkArgumentNotNull(jdbi, "Jdbi (version 3) instance cannot be null");
        setPersistentHostInformationFrom(serviceDetails);

        this.dataStoreType = requireNotNull(dataStoreType, "dataStoreType cannot be null");
        this.errorDao = jdbi.onDemand(Jdbi3ApplicationErrorDao.class);
        this.healthCheck = registerRecentErrorsHealthCheckOrNull(
                addHealthCheck, environment, errorDao, serviceDetails, timeWindowValue, timeWindowUnit);

        registerResources(environment, errorDao, dataStoreType);
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
