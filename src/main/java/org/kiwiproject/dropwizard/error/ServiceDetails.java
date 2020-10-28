package org.kiwiproject.dropwizard.error;

import lombok.Builder;
import lombok.Value;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;

/**
 * Encapsulates service information to use when setting {@code PersistentHostInformation}.
 * <p>
 * You can construct using the {@code builder()} or using the {@code from} factory method.
 *
 * @see org.kiwiproject.dropwizard.error.model.PersistentHostInformation
 * @see org.kiwiproject.dropwizard.error.model.ApplicationError#setPersistentHostInformation(PersistentHostInformation)
 */
@Value
@Builder
public class ServiceDetails {

    String hostName;
    String ipAddress;
    int applicationPort;

    /**
     * Factory method to create a new instance.
     *
     * @param hostName        the host name this application is running on
     * @param ipAddress       the IP address of the host this application is running on
     * @param applicationPort the port this application is listening on
     * @return a new instance
     */
    public static ServiceDetails from(String hostName, String ipAddress, int applicationPort) {
        return ServiceDetails.builder()
                .hostName(hostName)
                .ipAddress(ipAddress)
                .applicationPort(applicationPort)
                .build();
    }
}
