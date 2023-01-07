package org.kiwiproject.dropwizard.error;

import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.health.RecentErrorsHealthCheck;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

import java.util.Optional;

/**
 * This library provides an easy way to store application errors in your service's local (e.g. Postgres) database.
 * This interface defines the general contract, and instances are usually built using {@link ErrorContextBuilder},
 * though you can build everything programmatically if you want to.
 * <p>
 * This acts much like a Dropwizard bundle in that it creates an {@link ApplicationErrorDao} for use anywhere in
 * your application, and registers an
 * {@link org.kiwiproject.dropwizard.error.resource.ApplicationErrorResource ApplicationErrorResource} which
 * exposes application errors via REST. The {@link #errorDao()} method provides the DAO once an {@link ErrorContext}
 * has been built by one of the build methods in {@link ErrorContextBuilder}.
 * <p>
 * It also registers a health check that reports healthy if no errors have occurred in a configurable time window. The
 * default value is the last 15 minutes. If desired, you can disable the creation of the health check by calling
 * {@link ErrorContextBuilder#skipHealthCheck()} when constructing the instance.
 * <p>
 * We currently support storing errors to a relational database with JDBI 3. If your application does not currently
 * have a database or uses something else, we also provide several other options including an in-memory H2 database,
 * an in-memory implementation that uses a {@link java.util.concurrent.ConcurrentMap} and a no-op implementation
 * that does nothing.
 * <p>
 * <strong>JDBI Note:</strong>
 * To start creating application errors you will first need to create the database
 * table (unless you are using the in-memory H2 database). See {@code dropwizard-app-errors-migrations.xml} in the
 * source code for more details; this is a Liquibase migration file. Once the table exists, build an instance
 * using {@link ErrorContextBuilder}. You can then supply the {@link ApplicationErrorDao} to anywhere
 * in your application, e.g. other services or DAOs, resources, etc. Once your application has started, clients can
 * retrieve and resolve application errors via the REST endpoint.
 *
 * @implNote The reason this is <em>not</em> a Dropwizard bundle is mainly because it would not have the necessary
 * components (such as a Dropwizard {@code DataSourceFactory} or JDBI 3 {@code Jdbi} instance) during the
 * Dropwizard bundle initialization lifecycle.
 * @see ErrorContextBuilder
 */
public interface ErrorContext {

    /**
     * Return the type of data store this instance uses.
     *
     * @return the {@link DataStoreType}
     */
    DataStoreType dataStoreType();

    /**
     * Return the {@link ApplicationErrorDao} singleton that can be shared by an entire application.
     *
     * @return the thread-safe ApplicationErrorDao
     */
    ApplicationErrorDao errorDao();

    /**
     * Return the {@link RecentErrorsHealthCheck}, mainly so that the time window information can be obtained if it
     * is needed for some reason, e.g. logging it at application startup. Note if
     * {@link ErrorContextBuilder#skipHealthCheck()} was called this will return an empty {@link Optional}.
     *
     * @return an Optional containing the {@link RecentErrorsHealthCheck}, or an empty Optional
     */
    Optional<RecentErrorsHealthCheck> recentErrorsHealthCheck();

}
