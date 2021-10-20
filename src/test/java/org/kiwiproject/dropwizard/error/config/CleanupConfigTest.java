package org.kiwiproject.dropwizard.error.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.util.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CleanupConfig")
class CleanupConfigTest {

    @Test
    void shouldHaveDefaults() {
        var config = new CleanupConfig();

        assertThat(config.getCleanupStrategy()).isEqualTo(CleanupConfig.CleanupStrategy.RESOLVED_ONLY);
        assertThat(config.getApplicationErrorExpiration()).isEqualTo(Duration.days(30));
    }
}
