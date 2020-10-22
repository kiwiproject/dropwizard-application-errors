package org.kiwiproject.dropwizard.error.model;

import static com.google.common.base.Preconditions.checkArgument;
import static org.kiwiproject.base.KiwiPreconditions.requireNotBlank;

import lombok.Value;

/**
 * Defines information about the host on which application errors are created for a Dropwizard application while
 * it is running on a specific host and port. This information is set one time when a Dropwizard first starts, and
 * is used for any new application errors created while that application is alive.
 */
@Value
public class PersistentHostInformation {

    String hostName;
    String ipAddress;
    int port;

    public PersistentHostInformation(String hostName, String ipAddress, int port) {
        this.hostName = requireNotBlank(hostName, "hostName must not be blank");
        this.ipAddress = requireNotBlank(ipAddress, "ipAddress must not be blank");
        checkArgument(isValidPort(port), "port must be a valid port");
        this.port = port;
    }

    static boolean isValidPort(int value) {
        return value >= 0 && value <= 65_535;
    }
}
