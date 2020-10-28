package org.kiwiproject.dropwizard.error.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kiwiproject.test.validation.ValidationTestHelper.assertPropertyViolations;

import io.dropwizard.util.Duration;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

@DisplayName("TimeWindow")
@ExtendWith(SoftAssertionsExtension.class)
class TimeWindowTest {

    @Test
    void shouldHaveDefaultDuration() {
        assertThat(new TimeWindow().getDuration().toMinutes()).isEqualTo(15);
    }

    @Test
    void shouldConstructWithAllArgsConstructor() {
        var window = new TimeWindow(Duration.minutes(30));

        assertThat(window.getDuration().toMinutes()).isEqualTo(30);
    }

    @ParameterizedTest
    @CsvSource({
            " , 1",
            "-1, 1",
            "0, 1",
            "1, 0",
            "2, 0",
            "30, 0",
    })
    void shouldValidateDuration(Long minutes, int expectedNumViolations) {
        var duration = Optional.ofNullable(minutes)
                .map(Duration::minutes)
                .orElse(null);
        var window = new TimeWindow(duration);

        assertPropertyViolations(window, "duration", expectedNumViolations);
    }
}
