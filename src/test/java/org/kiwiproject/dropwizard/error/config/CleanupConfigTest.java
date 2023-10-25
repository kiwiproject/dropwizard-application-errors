package org.kiwiproject.dropwizard.error.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.util.Duration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.kiwiproject.test.validation.ValidationTestHelper.assertNoViolations;
import static org.kiwiproject.test.validation.ValidationTestHelper.assertOnePropertyViolation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kiwiproject.reflect.KiwiReflection;

@DisplayName("CleanupConfig")
class CleanupConfigTest {

    private CleanupConfig config;

    @BeforeEach
    void setUp() {
        config = new CleanupConfig();
    }

    @Test
    void shouldHaveDefaults() {
        var config = new CleanupConfig();

        assertAll(
            () -> assertThat(config.getCleanupStrategy()).isEqualTo(CleanupConfig.CleanupStrategy.ALL_ERRORS),
            () -> assertThat(config.getResolvedErrorExpiration()).isEqualTo(Duration.days(14)),
            () -> assertThat(config.getUnresolvedErrorExpiration()).isEqualTo(Duration.days(60)),
            () -> assertThat(config.getCleanupJobName()).isEqualTo("Application-Errors-Cleanup-Job-%d"),
            () -> assertThat(config.getInitialJobDelay()).isEqualTo(Duration.minutes(1)),
            () -> assertThat(config.getJobInterval()).isEqualTo(Duration.days(1))
        );
    }

    @Test
    void shouldValidateRequiredFields() {
        KiwiReflection.invokeMutatorMethodsWithNull(config);

        assertAll(
            () -> assertOnePropertyViolation(config, "cleanupStrategy"),
            () -> assertOnePropertyViolation(config, "resolvedErrorExpiration"),
            () -> assertOnePropertyViolation(config, "unresolvedErrorExpiration"),
            () -> assertOnePropertyViolation(config, "cleanupJobName"),
            () -> assertOnePropertyViolation(config, "initialJobDelay"),
            () -> assertOnePropertyViolation(config, "jobInterval")
        );
    }

    @ParameterizedTest
    @ValueSource(longs = {60, 61, 90, 120})
    void shouldPassValidationWhenDurationsAreAtOrAboveTheMinimumAllowed(long seconds) {
        var duration = Duration.seconds(seconds);

        config.setResolvedErrorExpiration(duration);
        config.setUnresolvedErrorExpiration(duration);
        config.setInitialJobDelay(duration);
        config.setJobInterval(duration);

        assertNoViolations(config);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 30, 59})
    void shouldValidateMinimumDurations(long seconds) {
        var duration = Duration.seconds(seconds);

        config.setResolvedErrorExpiration(duration);
        config.setUnresolvedErrorExpiration(duration);
        config.setInitialJobDelay(duration);
        config.setJobInterval(duration);

        assertAll(
            () -> assertOnePropertyViolation(config, "resolvedErrorExpiration"),
            () -> assertOnePropertyViolation(config, "unresolvedErrorExpiration"),
            () -> assertOnePropertyViolation(config, "initialJobDelay"),
            () -> assertOnePropertyViolation(config, "jobInterval")
        );
    }
}
