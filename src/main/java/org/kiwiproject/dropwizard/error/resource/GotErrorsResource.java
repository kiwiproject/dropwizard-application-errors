package org.kiwiproject.dropwizard.error.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import org.kiwiproject.dropwizard.error.model.DataStoreType;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * A simple JAX-RS resource for clients to determine if application errors are available or not.
 * <p>
 * Clients can check if application errors are available using a HTTP GET request that returns a
 * {@code 200 OK} response if available. The response entity contains information on whether the
 * underlying data store is shared or not. A shared database means it is shared between separate
 * service/application instances, whereas a database that is <em>not</em> shared is local to the
 * specific service/application instance being queried.
 * <p>
 * For example, an in-memory H2 database is not shared, whereas a Postgres database used by all
 * instances of one specific service/application (e.g. an "Order Service" that has 3 running
 * instances that all use the same Postgres database).
 * <p>
 * Obviously, a {@code 404 Not Found} is returned if this resource is not registered and available.
 */
@Path("/kiwi/got-errors")
@Produces(APPLICATION_JSON)
public class GotErrorsResource {

    private final DataStoreType dataStoreType;

    /**
     * Construct a new instance.
     *
     * @param dataStoreType the DataStoreType of the service/application this resource is registered with
     */
    public GotErrorsResource(DataStoreType dataStoreType) {
        this.dataStoreType = dataStoreType;
    }

    /**
     * Check if application errors are available. Clients always receive a 200 response from this endpoint.
     *
     * @return the Response
     */
    @GET
    public Response gotErrors() {
        var entity = Map.of("shared", dataStoreType.shared());
        return Response.ok(entity).build();
    }
}
