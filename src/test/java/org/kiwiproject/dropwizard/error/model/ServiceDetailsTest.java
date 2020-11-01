package org.kiwiproject.dropwizard.error.model;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceDetails")
class ServiceDetailsTest {

    @Test
    void shouldCreateFromFactory() {
        var serviceDetails = ServiceDetails.from("app01.test", "192.168.1.142", 9069);

        assertSoftly(softly -> {
            softly.assertThat(serviceDetails.getHostName()).isEqualTo("app01.test");
            softly.assertThat(serviceDetails.getIpAddress()).isEqualTo("192.168.1.142");
            softly.assertThat(serviceDetails.getApplicationPort()).isEqualTo(9069);
        });
    }
}
