package org.kiwiproject.dropwizard.error.resource;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.ApplicationErrorPage;
import org.kiwiproject.jaxrs.KiwiStandardResponses;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

// TODO Javadocs...

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

    @GET
    @Path("/{id}")
    @Timed
    @ExceptionMetered
    public Response getById(@PathParam("id") OptionalLong id) {
        Optional<ApplicationError> errorOptional = errorDao.getById(id.orElseThrow());
        return KiwiStandardResponses.standardGetResponse("id", id, errorOptional, ApplicationError.class);
    }

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

    @PUT
    @Path("/resolve/{id}")
    @Timed
    @ExceptionMetered
    public Response resolve(@PathParam("id") OptionalLong id) {
        var resolved = errorDao.resolve(id.orElseThrow());
        return KiwiStandardResponses.standardPutResponse(resolved);
    }

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
