package org.kiwiproject.dropwizard.error;

import static org.kiwiproject.base.KiwiPreconditions.checkArgumentNotNull;
import static org.kiwiproject.base.KiwiStrings.f;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.checkCommonArguments;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerCleanupJobOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerRecentErrorsHealthCheckOrNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.registerResources;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.setPersistentHostInformationFrom;

import io.dropwizard.core.setup.Environment;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.extension.NoSuchExtensionException;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.jdbi3.Jdbi3ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.error.model.ServiceDetails;

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
                      ErrorContextOptions options) {

        checkCommonArguments(environment, serviceDetails, options);
        checkArgumentNotNull(jdbi, "Jdbi (version 3) instance cannot be null");
        setPersistentHostInformationFrom(serviceDetails);

        this.dataStoreType = options.getDataStoreType();
        this.errorDao = getOnDemandErrorDao(jdbi);
        this.healthCheck = registerRecentErrorsHealthCheckOrNull(environment, serviceDetails, errorDao, options);

        registerCleanupJobOrNull(environment, errorDao, options);
        registerResources(environment, errorDao, options);
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
