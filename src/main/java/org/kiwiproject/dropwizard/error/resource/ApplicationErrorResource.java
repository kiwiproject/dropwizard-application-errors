package org.kiwiproject.dropwizard.error.resource;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.ApplicationErrorPage;
import org.kiwiproject.jaxrs.KiwiStandardResponses;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * JAX-RS resource class for retrieving application errors, as well as marking them resolved.
 */
@Path("/kiwi/application-errors")
@Produces(APPLICATION_JSON)
public class ApplicationErrorResource {

    private final ApplicationErrorDao errorDao;

    public ApplicationErrorResource(ApplicationErrorDao errorDao) {
        this.errorDao = errorDao;
    }

    /**
     * GET endpoint to retrieve an application error by ID.
     *
     * @param id the ID as a path parameter
     * @return the Response
     */
    @GET
    @Path("/{id}")
    @Timed
    @ExceptionMetered
    public Response getById(@PathParam("id") OptionalLong id) {
        Optional<ApplicationError> errorOptional = errorDao.getById(id.orElseThrow());
        return KiwiStandardResponses.standardGetResponse("id", id, errorOptional, ApplicationError.class);
    }

    /**
     * GET endpoint to paginate application errors.
     *
     * @param statusParam status query parameter indicating which application errors to include
     * @param pageNumber  the page number query parameter, starting from one
     * @param pageSize    the page size query parameter
     * @return the Response
     * @see ApplicationErrorStatus
     */
    @GET
    @Timed
    @ExceptionMetered
    public Response getErrors(@QueryParam("status") @DefaultValue("UNRESOLVED") String statusParam,
                              @QueryParam("pageNumber") @DefaultValue("1") OptionalInt pageNumber,
                              @QueryParam("pageSize") @DefaultValue("25") OptionalInt pageSize) {

        var status = ApplicationErrorStatus.from(statusParam);
        var thePageNumber = pageNumber.orElseThrow();
        var thePageSize = pageSize.orElseThrow();
        var errors = errorDao.getErrors(status, thePageNumber, thePageSize);
        var count = errorDao.count(status);

        var appErrors = ApplicationErrorPage.builder()
                .pageNumber(thePageNumber)
                .pageSize(thePageSize)
                .totalCount(count)
                .items(errors)
                .build();

        return Response.ok(appErrors).build();
    }

    /**
     * Resolve an application error by ID.
     *
     * @param id the ID path parameter
     * @return the Response
     */
    @PUT
    @Path("/resolve/{id}")
    @Timed
    @ExceptionMetered
    public Response resolve(@PathParam("id") OptionalLong id) {
        var resolvedError = errorDao.resolve(id.orElseThrow());
        return KiwiStandardResponses.standardPutResponse(resolvedError);
    }

    /**
     * Resolve <em>all</em> unresolved application errors.
     *
     * @return the Response
     */
    @PUT
    @Path("/resolve")
    @Timed
    @ExceptionMetered
    public Response resolveAllUnresolved() {
        var count = errorDao.resolveAllUnresolvedErrors();
        var entity = Map.of("resolvedCount", count);
        return KiwiStandardResponses.standardPutResponse(entity);
    }
}
