package org.kiwiproject.dropwizard.error.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.test.junit.jupiter.params.provider.BlankStringArgumentsProvider;

@DisplayName("ApplicationErrorStatus")
class ApplicationErrorStatusTest {

    @Nested
    class FromFactoryMethod {

        @ParameterizedTest
        @ValueSource(strings = {"unresolved", "Unresolved", "UNRESOLVED", "UnReSoLvEd"})
        void shouldParseUnresolvedValues_To_UNRESOLVED(String value) {
            check(value, ApplicationErrorStatus.UNRESOLVED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"resolved", "Resolved", "RESOLVED", "ReSoLvEd"})
        void shouldParseResolvedValues_To_RESOLVED(String value) {
            check(value, ApplicationErrorStatus.RESOLVED);
        }

        @ParameterizedTest
        @ArgumentsSource(BlankStringArgumentsProvider.class)
        void shouldParseBlankValues_To_ALL(String value) {
            check(value, ApplicationErrorStatus.ALL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"all", "All", "ALL", "aLL"})
        void shouldParseAllValues_To_ALL(String value) {
            check(value, ApplicationErrorStatus.ALL);
        }

        @ParameterizedTest
        @ValueSource(strings = {"R", "UR", "Resolve", "Yes", "No", "Foo", "123"})
        void shouldThrow_IllegalArgumentException_WhenInvalidInput(String value) {
            assertThatThrownBy(() -> ApplicationErrorStatus.from(value))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }

    private void check(String value, ApplicationErrorStatus expectedStatus) {
        ApplicationErrorStatus status;
        try {
            status = ApplicationErrorStatus.from(value);
        } catch (IllegalArgumentException e) {
            status = null;
        }

        assertThat(status)
                .describedAs("value [%s] did not map to status %s", value, expectedStatus)
                .isNotNull()
                .isEqualTo(expectedStatus);
    }

}
