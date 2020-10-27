package org.kiwiproject.dropwizard.error.model;

/**
 * Used to indicate whether the data store is shared (e.g. a typical PostgreSQL database shared across multiple
 * clients) or not shared, such as an in-memory H2 database that is only accessible from within a single JVM.
 */
public enum DataStoreType {

    /**
     * Indicates the application error data store is shared across multiple clients, and any of them can be used to
     * create or find errors.
     */
    SHARED(true),

    /**
     * Indicates the application error data store is not shared and errors will be local to that client, e.g. an
     * error created by one client will never be seen by any other client.
     */
    NOT_SHARED(false);

    private final boolean shared;

    DataStoreType(boolean shared) {
        this.shared = shared;
    }

    /**
     * @return {@code true} if this data store type is shared, otherwise {@code false}
     */
    public boolean shared() {
        return shared;
    }
}
