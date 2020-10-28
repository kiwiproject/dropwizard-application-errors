package org.kiwiproject.dropwizard.error.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.kiwiproject.jaxrs.KiwiGenericTypes.MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE;
import static org.kiwiproject.test.jaxrs.JaxrsTestHelper.assertOkResponse;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

@DisplayName("GotErrorsResource")
@ExtendWith(DropwizardExtensionsSupport.class)
class GotErrorsResourceTest {

    private static final DataStoreType DATA_STORE_TYPE = DataStoreType.NOT_SHARED;

    private static final ResourceExtension RESOURCES = ResourceExtension.builder()
            .addResource(new GotErrorsResource(DATA_STORE_TYPE))
            .build();

    @Test
    void shouldReturnOkResponse() {
        var response = RESOURCES.client()
                .target("/kiwi/got-errors?")
                .request()
                .get();

        assertOkResponse(response);

        var entity = response.readEntity(MAP_OF_STRING_TO_OBJECT_GENERIC_TYPE);

        assertThat(entity).containsOnly(
                entry("shared", DATA_STORE_TYPE.shared())
        );
    }
}
