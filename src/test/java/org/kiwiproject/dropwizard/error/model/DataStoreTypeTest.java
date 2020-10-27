package org.kiwiproject.dropwizard.error.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("DataStoreType")
class DataStoreTypeTest {

    @Nested
    class Shared {

        @ParameterizedTest
        @CsvSource({
                "SHARED, true",
                "NOT_SHARED, false"
        })
        void shouldHaveExpectedValue(DataStoreType dataStoreType, boolean expectedValue) {
            assertThat(dataStoreType.shared())
                    .describedAs("Expected %s to have value %b", dataStoreType, expectedValue)
                    .isEqualTo(expectedValue);
        }
    }
}
