package org.kiwiproject.dropwizard.error.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("PersistentHostInformation")
class PersistentHostInformationTest {

    @Nested
    class Constructor {

        @ParameterizedTest
        @CsvSource({
                " , , -1",
                "acme.com, 192.168.1.42, -1",
                "acme.com, 192.168.1.42, 65536",
                "acme.com, , 9090",
                "acme.com, '', 9090",
                " , 192.168.1.42, 9090",
                " '', 192.168.1.42, 9090",
        })
        void shouldNotPermitInvalidArguments(String hostName, String ipAddress, int port) {
            assertThatThrownBy(() -> new PersistentHostInformation(hostName, ipAddress, port))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
        }
    }
}
