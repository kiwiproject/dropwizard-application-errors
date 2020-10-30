package org.kiwiproject.dropwizard.error;

import static com.google.common.base.Preconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;

import io.dropwizard.setup.Environment;
import lombok.experimental.UtilityClass;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.dropwizard.error.resource.ApplicationErrorResource;
import org.kiwiproject.dropwizard.error.resource.GotErrorsResource;

import java.time.temporal.TemporalUnit;

/**
 * Shared utilities for use by {@link ErrorContext} implementations.
 *
 * @implNote This is not public and is subject to change.
 */
@UtilityClass
class ErrorContextUtilities {

    static void checkCommonArguments(Environment environment,
                                     ServiceDetails serviceDetails,
                                     DataStoreType dataStoreType,
                                     long timeWindowValue,
                                     TemporalUnit timeWindowUnit) {

        checkArgumentNotNull(environment, "Dropwizard Environment cannot be null");
        checkArgumentNotNull(serviceDetails, "serviceDetails cannot be null");
        checkArgumentNotNull(dataStoreType, "dataStoreType cannot ne null");
        checkArgument(timeWindowValue > 0, "timeWindowValue must be positive");
        checkArgumentNotNull(timeWindowUnit, "timeWindowUnit cannot be null");
    }

    static PersistentHostInformation setPersistentHostInformationFrom(ServiceDetails serviceDetails) {
        checkArgumentNotNull(serviceDetails);

        ApplicationError.setPersistentHostInformation(
                serviceDetails.getHostName(),
                serviceDetails.getIpAddress(),
                serviceDetails.getApplicationPort());

        return ApplicationError.getPersistentHostInformation();
    }

    static void registerResources(Environment environment,
                                  ApplicationErrorDao errorDao,
                                  DataStoreType dataStoreType) {

        checkArgumentNotNull(environment);
        checkArgumentNotNull(errorDao);
        checkArgumentNotNull(dataStoreType);

        environment.jersey().register(new ApplicationErrorResource(errorDao));
        environment.jersey().register(new GotErrorsResource(dataStoreType));
    }

    static RecentErrorsHealthCheck registerRecentErrorsHealthCheckOrNull(boolean addHealthCheck,
                                                                         Environment environment,
                                                                         ApplicationErrorDao errorDao,
                                                                         ServiceDetails serviceDetails,
                                                                         long timeWindowValue,
                                                                         TemporalUnit timeWindowUnit) {

        if (addHealthCheck) {
            var healthCheck = new RecentErrorsHealthCheck(errorDao, serviceDetails, timeWindowValue, timeWindowUnit);
            environment.healthChecks().register("recentApplicationErrors", healthCheck);
            return healthCheck;
        }

        return null;
    }
}
