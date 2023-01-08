package org.kiwiproject.dropwizard.error.resource;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertInternalServerErrorResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertNotFoundResponse;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;
import static org.kiwiproject.test.jaxrs.exception.JaxrsExceptionTestHelper.assertContainsError;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorDao;
import org.kiwiproject.dropwizard.error.dao.ApplicationErrorStatus;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.ApplicationError.Resolved;
import org.kiwiproject.dropwizard.error.model.ApplicationErrorPage;
import org.kiwiproject.jaxrs.KiwiGenericTypes;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@DisplayName("ApplicationErrorResource")
@ExtendWith(DropwizardExtensionsSupport.class)
class ApplicationErrorResourceTest {

    private static final ApplicationErrorDao ERROR_DAO = mock(ApplicationErrorDao.class);
    private static final ApplicationErrorResource ERROR_RESOURCE = new ApplicationErrorResource(ERROR_DAO);

    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .bootstrapLogging(false)
            .addResource(ERROR_RESOURCE)
            .build();

    @BeforeEach
    void setUp() {
        reset(ERROR_DAO);
    }

    @Nested
    class GetById {

        @Test
        void shouldReturnNotFoundResponse_WhenNotFound() {
            when(ERROR_DAO.getById(anyLong())).thenReturn(Optional.empty());

            var response = RESOURCES.client()
                    .target("/kiwi/application-errors/42")
                    .request()
                    .get();

            assertNotFoundResponse(response);
            assertContainsError(response, 404, "ApplicationError with id OptionalLong[42] not found");
        }

        @Test
        void shouldReturnOkResponse_WhenFound() {
            var id = 42L;
            var error = ApplicationError.builder().id(id).description("oops").build();
            when(ERROR_DAO.getById(id)).thenReturn(Optional.of(error));

            var response = RESOURCES.client()
                    .target("/kiwi/application-errors/{id}")
                    .resolveTemplate("id", id)
                    .request()
                    .get();

            assertOkResponse(response);

            var entity = response.readEntity(ApplicationError.class);
            assertThat(entity).isEqualTo(error);
        }
    }

    @Nested
    class GetErrors {

        @Test
        void shouldGetAllAllErrors() {
            int pageNumber = 1;
            int pageSize = 15;
            long totalCount = 42L;
            var errors = IntStream.rangeClosed(1, pageSize)
                    .mapToObj(value -> {
                        var randomBoolean = ThreadLocalRandom.current().nextBoolean();
                        return newApplicationError("error " + value, Resolved.of(randomBoolean));
                    })
                    .collect(toList());

            when(ERROR_DAO.getErrors(ApplicationErrorStatus.ALL, pageNumber, pageSize)).thenReturn(errors);
            when(ERROR_DAO.count(ApplicationErrorStatus.ALL)).thenReturn(totalCount);

            var response = RESOURCES.client().target("/kiwi/application-errors")
                    .queryParam("status", ApplicationErrorStatus.ALL.name())
                    .queryParam("pageNumber", pageNumber)
                    .queryParam("pageSize", pageSize)
                    .request()
                    .get();

            assertApplicationsErrorsResponse(response, pageNumber, pageSize, totalCount, errors);
        }

        @Test
        void shouldGetUnresolvedErrors() {
            var pageNumber = 1;
            var pageSize = 15;
            var totalCount = 42L;
            var errors = IntStream.rangeClosed(1, pageSize)
                    .mapToObj(value -> newApplicationError("error " + value, Resolved.NO))
                    .collect(toList());

            when(ERROR_DAO.getErrors(ApplicationErrorStatus.UNRESOLVED, pageNumber, pageSize)).thenReturn(errors);
            when(ERROR_DAO.count(ApplicationErrorStatus.UNRESOLVED)).thenReturn(totalCount);

            var response = RESOURCES.client().target("/kiwi/application-errors")
                    .queryParam("status", ApplicationErrorStatus.UNRESOLVED.name())
                    .queryParam("pageNumber", pageNumber)
                    .queryParam("pageSize", pageSize)
                    .request()
                    .get();

            assertApplicationsErrorsResponse(response, pageNumber, pageSize, totalCount, errors);
        }

        @Test
        void shouldGetResolvedErrors() {
            var pageNumber = 1;
            var pageSize = 15;
            var totalCount = 42L;
            var errors = IntStream.rangeClosed(1, pageSize)
                    .mapToObj(value -> newApplicationError("error " + value, Resolved.YES))
                    .collect(toList());

            when(ERROR_DAO.getErrors(ApplicationErrorStatus.RESOLVED, pageNumber, pageSize)).thenReturn(errors);
            when(ERROR_DAO.count(ApplicationErrorStatus.RESOLVED)).thenReturn(totalCount);

            var response = RESOURCES.client().target("/kiwi/application-errors")
                    .queryParam("status", ApplicationErrorStatus.RESOLVED.name())
                    .queryParam("pageNumber", pageNumber)
                    .queryParam("pageSize", pageSize)
                    .request()
                    .get();

            assertApplicationsErrorsResponse(response, pageNumber, pageSize, totalCount, errors);
        }

        @Test
        void shouldReturnInternalServerError_WhenGivenInvalidStatusParameter() {
            var response = RESOURCES.client().target("/kiwi/application-errors")
                    .queryParam("status", "BOGUS")
                    .request()
                    .get();
            assertInternalServerErrorResponse(response);
        }
    }

    @Nested
    class Resolve {

        @Test
        void shouldResolve() {
            long id = 42;
            var error = newApplicationError("an error", Resolved.YES);
            when(ERROR_DAO.resolve(anyLong())).thenReturn(error);

            var response = RESOURCES.client().target("/kiwi/application-errors/resolve/{id}")
                    .resolveTemplate("id", id)
                    .request()
                    .put(Entity.json("{}"));

            assertOkResponse(response);

            var resolvedError = response.readEntity(ApplicationError.class);

            assertThat(resolvedError).isEqualTo(error);

            verify(ERROR_DAO).resolve(id);
        }

        @Test
        void shouldReturnInternalServerErrorResponse_WhenNotFound() {
            var errorMessage = "Nothing here...move along";
            when(ERROR_DAO.resolve(anyLong())).thenThrow(new IllegalStateException(errorMessage));

            var response = RESOURCES.client().target("/kiwi/application-errors/resolve/42")
                    .request()
                    .put(Entity.json("{}"));

            assertInternalServerErrorResponse(response);
        }
    }

    @Test
    void shouldResolveAll() {
        var resolvedCount = 42;
        when(ERROR_DAO.resolveAllUnresolvedErrors()).thenReturn(resolvedCount);

        var response = RESOURCES.client().target("/kiwi/application-errors/resolve")
                .request()
                .put(Entity.json("{}"));

        assertOkResponse(response);

        var entity = response.readEntity(KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);
        assertThat(entity).isEqualTo(Map.of("resolvedCount", resolvedCount));
    }

    private void assertApplicationsErrorsResponse(Response response,
                                                  int expectedPageNumber,
                                                  int expectedPageSize,
                                                  long expectedTotalCount,
                                                  List<ApplicationError> expectedErrors) {
        assertOkResponse(response);

        var applicationErrors = response.readEntity(ApplicationErrorPage.class);
        assertThat(applicationErrors.getPageNumber()).isEqualTo(expectedPageNumber);
        assertThat(applicationErrors.getPageSize()).isEqualTo(expectedPageSize);
        assertThat(applicationErrors.getTotalCount()).isEqualTo(expectedTotalCount);

        List<String> errorDescriptions = expectedErrors.stream().map(ApplicationError::getDescription).collect(toList());

        assertThat(applicationErrors.getItems())
                .describedAs("should have %d errors with matching descriptions", expectedPageSize)
                .hasSize(expectedPageSize)
                .extracting(ApplicationError::getDescription)
                .containsExactlyElementsOf(errorDescriptions);
    }


    private ApplicationError newApplicationError(String description, Resolved resolved) {
        return ApplicationError.builder()
                .description(description)
                .resolved(resolved.toBoolean())
                .build();
    }
}
