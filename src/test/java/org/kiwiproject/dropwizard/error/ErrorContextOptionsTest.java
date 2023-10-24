package org.kiwiproject.dropwizard.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kiwiproject.dropwizard.error.config.CleanupConfig;
import org.kiwiproject.dropwizard.error.health.TimeWindow;
import org.kiwiproject.dropwizard.error.model.DataStoreType;

import java.time.temporal.ChronoUnit;

@DisplayName("ErrorContextOptions")
class ErrorContextOptionsTest {

    @Test
    void shouldBuildWithDefaultValues() {
        var options = ErrorContextOptions.builder().build();

        assertAll(
            () -> assertThat(options.getDataStoreType()).isEqualTo(DataStoreType.SHARED),
            () -> assertThat(options.isAddErrorsResource()).isTrue(),
            () -> assertThat(options.isAddGotErrorsResource()).isTrue(),
            () -> assertThat(options.isAddHealthCheck()).isTrue(),
            () -> assertThat(options.getTimeWindowValue()).isEqualTo(TimeWindow.DEFAULT_TIME_WINDOW_MINUTES),
            () -> assertThat(options.getTimeWindowUnit()).isEqualTo(ChronoUnit.MINUTES),
            () -> assertThat(options.isAddCleanupJob()).isTrue(),
            () -> assertThat(options.getCleanupConfig()).usingRecursiveComparison().isEqualTo(new CleanupConfig())
        );
    }

    @Test
    void shouldRequireDataStoreType() {
        assertThatNullPointerException()
                .isThrownBy(() -> ErrorContextOptions.builder().dataStoreType(null).build());
    }

    @Test
    void shouldRequireTimeWindowUnit() {
        assertThatNullPointerException()
                .isThrownBy(() -> ErrorContextOptions.builder().timeWindowUnit(null).build());
    }
}
