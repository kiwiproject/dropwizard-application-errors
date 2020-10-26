package org.kiwiproject.dropwizard.error.test.junit.jupiter;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension.HostInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This test uses the {@link ApplicationErrorExtension} as it is meant to be used in tests.
 */
@DisplayName("ApplicationErrorExtension: Integration")
@ExtendWith(ApplicationErrorExtension.class)
@ExtendWith(SoftAssertionsExtension.class)
class ApplicationErrorExtensionIntegrationTest {

    @Test
    void shouldSetPersistentHostInformation(@HostInfo PersistentHostInformation hostInfo,
                                            SoftAssertions softly) throws UnknownHostException {

        InetAddress localhost = InetAddress.getLocalHost();

        softly.assertThat(hostInfo.getHostName()).isEqualTo(localhost.getHostName());
        softly.assertThat(hostInfo.getIpAddress()).isEqualTo(localhost.getHostAddress());
        softly.assertThat(hostInfo.getPort()).isEqualTo(8080);
    }
}
