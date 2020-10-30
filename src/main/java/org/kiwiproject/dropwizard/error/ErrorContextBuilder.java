package org.kiwiproject.dropwizard.error;

import static java.util.Objects.nonNull;
import static org.kiwiproject.dropwizard.error.ErrorContextUtilities.checkCommonArguments;
import static org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc.createInMemoryH2Database;
import static org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc.isH2DataStore;

import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorJdbc;
import org.kiwiproject.dropwizard.error.health.TimeWindow;
import org.kiwiproject.dropwizard.error.model.DataStoreType;
import org.kiwiproject.dropwizard.jdbi3.Jdbi3Builders;

import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

/**
 * Builder for {@link ErrorContext} implementations.
 * <p>
 * To start building, you call {@link #newInstance()}. After that, there must be at the minimum three methods used:
 * <ol>
 * <li>{@link #environment(Environment)}</li>
 * <li>{@link #serviceDetails(ServiceDetails)}</li>
 * <li>And one of:
 * <ul>
 * <li>{@link #buildInMemoryH2()}</li>
 * <li>{@link #buildWithDataStoreFactory(DataSourceFactory)}</li>
 * <li>{@link #buildWithJdbi3(Jdbi)}</li>
 * </ul>
 * </ol>
 * <p>
 * Example for an in-memory H2 version of {@link ErrorContext}.
 * <pre>
 * var serviceDetails = ServiceDetails.from(theHostname, theIpAddress, thePortNumber);
 * var errorContext = ErrorContextBuilder.newInstance()
 *     .environment(theDropwizardEnvironment)
 *     .serviceDetails(serviceDetails)
 *     .buildInMemoryH2();
 * </pre>
 * <p>
 * Example for a JDBI 3 version of {@link ErrorContext} (using Kiwi's {@link Jdbi3Builders} to build the
 * {@link Jdbi} instance).
 * <pre>
 * var jdbi = Jdbi3Builders.buildManagedJdbi3(
 *     theDropwizardEnvironment,
 *     theDataSourceFactory,
 *     theHealthCheckName);
 *
 * var serviceDetails = ServiceDetails.from(theHostname,theIpAddress, thePortNumber);
 * var errorContext = ErrorContextBuilder.newInstance()
 *     .environment(theDropwizardEnvironment)
 *     .serviceDetails (serviceDetails)
 *     .buildWithJdbi3(jdbi);
 * </pre>
 * <p>
 * All of the terminal build methods use {@link DataStoreType} to determine if the {@link ErrorContext} instance is
 * {@link DataStoreType#SHARED shared} (i.e. multiple instances of the same service read and write to the same database)
 * or {@link DataStoreType#NOT_SHARED not shared} (i.e. each service instance has its own segregated database). You can
 * change the defaults (listed below) by explicitly calling the {@link #dataStoreType(DataStoreType)} method with the
 * store type you want.
 * Defaults:
 * <ul>
 * <li>{@link #buildInMemoryH2()} -> {@link DataStoreType#NOT_SHARED} (<b>NOTE:</b> this cannot be overridden)</li>
 * <li>{@link #buildWithDataStoreFactory(DataSourceFactory)}
 * <ul>
 * <li>If the database defined by the {@link DataSourceFactory} is an H2 instance -> {@link DataStoreType#NOT_SHARED}</li>
 * <li>Otherwise, {@link DataStoreType#SHARED}</li>
 * </ul>
 * </li>
 * <li>{@link #buildWithJdbi3(Jdbi)} -> {@link DataStoreType#SHARED}</li>
 */
@Slf4j
public class ErrorContextBuilder {

    private static final String DEFAULT_DATABASE_HEALTH_CHECK_NAME = "applicationErrorsDatabase";

    private Environment environment;
    private ServiceDetails serviceDetails;
    private DataStoreType dataStoreType;
    private boolean dataStoreTypeAlreadySet;
    private boolean addHealthCheck = true;
    private long timeWindowValue = TimeWindow.DEFAULT_TIME_WINDOW_MINUTES;
    private TemporalUnit timeWindowUnit = ChronoUnit.MINUTES;
    private boolean healthCheckTimeWindowAlreadySet;

    public static ErrorContextBuilder newInstance() {
        return new ErrorContextBuilder();
    }

    /**
     * Sets the Dropwizard {@link Environment} to use with this builder.
     *
     * @param environment the {@link Environment}
     * @return this builder
     */
    public ErrorContextBuilder environment(Environment environment) {
        this.environment = environment;
        return this;
    }

    /**
     * Sets the {@link ServiceDetails} to use with this builder.
     *
     * @param serviceDetails the {@link ServiceDetails}
     * @return this builder
     */
    public ErrorContextBuilder serviceDetails(ServiceDetails serviceDetails) {
        this.serviceDetails = serviceDetails;
        return this;
    }

    /**
     * Explicitly configures the {@link DataStoreType} to use with this builder.
     *
     * @param dataStoreType the {@link DataStoreType}
     * @return this builder
     * @implNote The builder implementations have default values; using this will override those defaults.
     * @see #buildInMemoryH2()
     * @see #buildWithDataStoreFactory(DataSourceFactory)
     * @see #buildWithJdbi3(Jdbi)
     */
    public ErrorContextBuilder dataStoreType(DataStoreType dataStoreType) {
        if (nonNull(dataStoreType)) {
            this.dataStoreType = dataStoreType;
            this.dataStoreTypeAlreadySet = true;
        }

        return this;
    }

    /**
     * Configures the resulting {@link ErrorContext} so that it does not create/register a health check with Dropwizard.
     *
     * @return this builder
     */
    public ErrorContextBuilder skipHealthCheck() {
        this.addHealthCheck = false;
        return this;
    }

    /**
     * Configures the {@link TimeWindow} for the health check. If an error occurs within this time window, the
     * health check will report as unhealthy. If there are no errors inside of this window, the health check will
     * report as healthy.
     *
     * @param timeWindow the {@link TimeWindow}
     * @return this builder
     * @implNote This will override any value set by {@link #timeWindowValue(long)} and
     * {@link #timeWindowUnit(TemporalUnit)}. Also, it will recast the values given in terms of
     * {@link ChronoUnit#MINUTES minutes}.
     */
    public ErrorContextBuilder timeWindow(TimeWindow timeWindow) {
        if (nonNull(timeWindow)) {
            this.timeWindowValue = timeWindow.getDuration().toMinutes();
            this.timeWindowUnit = ChronoUnit.MINUTES;
            this.healthCheckTimeWindowAlreadySet = true;
        }

        return this;
    }

    /**
     * Configures the length of time for the health check. If an error occurs within this time window, the
     * health check will report as unhealthy. If there are no errors inside of this window, the health check will
     * report as healthy.
     *
     * @param timeWindowValue the length of time
     * @return this builder
     * @implNote This value will be overwritten/ignored if {@link #timeWindow(TimeWindow)} is invoked.
     */
    public ErrorContextBuilder timeWindowValue(long timeWindowValue) {
        if (healthCheckTimeWindowAlreadySet) {
            logTimeWindowAlreadySetWarning("timeWindowValue", timeWindowValue);
        } else {
            this.timeWindowValue = timeWindowValue;
        }

        return this;
    }

    /**
     * Configures the {@link TemporalUnit} for the health check. If an error occurs within this time window, the
     * health check will report as unhealthy. If there are no errors inside of this window, the health check will
     * report as healthy.
     *
     * @param timeWindowUnit the {@link TemporalUnit}
     * @return this builder
     * @implNote This value will be overwritten/ignored if {@link #timeWindow(TimeWindow)} is invoked.
     */
    public ErrorContextBuilder timeWindowUnit(TemporalUnit timeWindowUnit) {
        if (healthCheckTimeWindowAlreadySet) {
            logTimeWindowAlreadySetWarning("timeWindowUnit", timeWindowUnit);
        } else if (nonNull(timeWindowUnit)) {
            this.timeWindowUnit = timeWindowUnit;
        }

        return this;
    }

    private static void logTimeWindowAlreadySetWarning(String fieldName, Object fieldValue) {
        LOG.warn("Ignoring {}={} because a TimeWindow has been set already and takes priority." +
                        " Call only (1) timeWindow(), or (2) timeWindowValue() and/or timeWindowUnit().",
                fieldName, fieldValue);
    }

    /**
     * Build an {@link ErrorContext} backed by an in-memory H2 database that uses JDBI version 3.
     *
     * @return a new {@link ErrorContext} instance
     * @implNote Always sets dataStoreType to {@link DataStoreType#NOT_SHARED}, since this builds an
     * in-memory database that can only be accessed from within the service in which it resides.
     */
    public ErrorContext buildInMemoryH2() {
        if (dataStoreTypeAlreadySet && dataStoreType == DataStoreType.SHARED) {
            forceH2DatabaseToBeNotSharedWithWarning();
        } else {
            dataStoreType = DataStoreType.NOT_SHARED;
        }

        checkCommonArguments(environment, serviceDetails, dataStoreType, timeWindowValue, timeWindowUnit);

        var dataSourceFactory = createInMemoryH2Database();
        var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory, DEFAULT_DATABASE_HEALTH_CHECK_NAME);

        return newJdbi3ErrorContext(jdbi);
    }

    /**
     * Build an {@link ErrorContext} using given the {@code dataSourceFactory} that uses JDBI version 3.
     *
     * @return a new {@link ErrorContext} instance
     * @implNote If you do not invoke {@link #dataStoreType(DataStoreType)} prior to calling this method, this method
     * will attempt to determine which {@link DataStoreType} it should use by calling
     * {@link ApplicationErrorJdbc#dataStoreTypeOf(DataSourceFactory)}.
     */
    public ErrorContext buildWithDataStoreFactory(DataSourceFactory dataSourceFactory) {
        if (dataStoreTypeAlreadySet && isH2DataStore(dataSourceFactory)) {
            forceH2DatabaseToBeNotSharedWithWarning();
        } else if (!dataStoreTypeAlreadySet) {
            dataStoreType = ApplicationErrorJdbc.dataStoreTypeOf(dataSourceFactory);
        }

        checkCommonArguments(environment, serviceDetails, dataStoreType, timeWindowValue, timeWindowUnit);

        LOG.info("Creating a {} JDBI (version 3) ErrorContext instance from the dataSourceFactory", dataStoreType);
        var jdbi = Jdbi3Builders.buildManagedJdbi(environment, dataSourceFactory, DEFAULT_DATABASE_HEALTH_CHECK_NAME);

        return newJdbi3ErrorContext(jdbi);
    }

    private void forceH2DatabaseToBeNotSharedWithWarning() {
        LOG.warn("An in-memory H2 database was requested with a SHARED data store type." +
                " This will be converted to a NOT_SHARED data store type.");
        this.dataStoreType = DataStoreType.NOT_SHARED;
    }

    /**
     * Build an {@link ErrorContext} that uses JDBI version 3.
     *
     * @return a new {@link ErrorContext} instance
     * @implNote If you do not invoke {@link #dataStoreType(DataStoreType)} prior to calling this method, this method
     * will default to using a value of {@link DataStoreType#SHARED}. Otherwise we would need to open a database
     * connection, inspect the database metadata, etc. to figure out the database, and we don't want to do all this.
     * If you are using an in-memory database, then be sure to configure the data store type before calling.
     */
    public ErrorContext buildWithJdbi3(Jdbi jdbi) {
        if (!dataStoreTypeAlreadySet) {
            dataStoreType = DataStoreType.SHARED;
        }

        checkCommonArguments(environment, serviceDetails, dataStoreType, timeWindowValue, timeWindowUnit);

        return newJdbi3ErrorContext(jdbi);
    }

    private Jdbi3ErrorContext newJdbi3ErrorContext(Jdbi jdbi) {
        return new Jdbi3ErrorContext(
                environment,
                serviceDetails,
                jdbi,
                dataStoreType,
                addHealthCheck,
                timeWindowValue,
                timeWindowUnit);
    }
}
