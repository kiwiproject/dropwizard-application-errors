package org.kiwiproject.dropwizard.error.test.mockito;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.experimental.UtilityClass;
import org.assertj.core.api.SoftAssertions;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.mockito.ArgumentMatcher;

// TODO docs...

/**
 * A collection of Mockito matchers for matching arguments of type {@link ApplicationError}.
 */
@UtilityClass
public class ApplicationErrorMatchers {

    /**
     * @param description
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description) {
        return actualError -> {
            assertThat(actualError.getDescription()).isEqualTo(description);
            return true;
        };
    }

    /**
     * @param description
     * @param exceptionType
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            return true;
        };
    }

    /**
     * @param description
     * @param exceptionType
     * @param exceptionMessage
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType,
                                                                            String exceptionMessage) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            assertThat(actualError.getExceptionMessage()).isEqualTo(exceptionMessage);
            return true;
        };
    }

    /**
     * @param description
     * @param exceptionType
     * @param exceptionMessage
     * @param exceptionCauseType
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType,
                                                                            String exceptionMessage,
                                                                            Class<?> exceptionCauseType) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            assertThat(actualError.getExceptionMessage()).isEqualTo(exceptionMessage);
            assertThat(actualError.getExceptionCauseType()).isEqualTo(exceptionCauseType.getName());
            return true;
        };
    }

    /**
     * @param description
     * @param exceptionType
     * @param exceptionMessage
     * @param exceptionCauseType
     * @param exceptionCauseMessage
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType,
                                                                            String exceptionMessage,
                                                                            Class<?> exceptionCauseType,
                                                                            String exceptionCauseMessage) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            assertThat(actualError.getExceptionMessage()).isEqualTo(exceptionMessage);
            assertThat(actualError.getExceptionCauseType()).isEqualTo(exceptionCauseType.getName());
            assertThat(actualError.getExceptionCauseMessage()).isEqualTo(exceptionCauseMessage);
            return true;
        };
    }

    /**
     * @param description
     * @param hostName
     * @param ipAddress
     * @param port
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            String hostName,
                                                                            String ipAddress,
                                                                            int port) {
        return actualError -> {
            assertThat(actualError.getDescription()).isEqualTo(description);
            assertHostInfo(actualError, hostName, ipAddress, port);
            return true;
        };
    }

    /**
     * @param description
     * @param exceptionType
     * @param hostName
     * @param ipAddress
     * @param port
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType,
                                                                            String hostName,
                                                                            String ipAddress,
                                                                            int port) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            assertHostInfo(actualError, hostName, ipAddress, port);
            return true;
        };
    }

    /**
     * @param description
     * @param exceptionType
     * @param exceptionMessage
     * @param exceptionCauseType
     * @param exceptionCauseMessage
     * @param hostName
     * @param ipAddress
     * @param port
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType,
                                                                            String exceptionMessage,
                                                                            Class<?> exceptionCauseType,
                                                                            String exceptionCauseMessage,
                                                                            String hostName,
                                                                            String ipAddress,
                                                                            int port) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            assertHostInfo(actualError, hostName, ipAddress, port);
            assertThat(actualError.isResolved()).isFalse();
            assertThat(actualError.getExceptionMessage()).isEqualTo(exceptionMessage);
            assertThat(actualError.getExceptionCauseType()).isEqualTo(exceptionCauseType.getName());
            assertThat(actualError.getExceptionCauseMessage()).isEqualTo(exceptionCauseMessage);
            return true;
        };
    }

    /**
     * @param softly
     * @param description
     * @param exceptionType
     * @param exceptionMessage
     * @param exceptionCauseType
     * @param exceptionCauseMessage
     * @param hostName
     * @param ipAddress
     * @param port
     * @return
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(SoftAssertions softly,
                                                                            String description,
                                                                            Class<?> exceptionType,
                                                                            String exceptionMessage,
                                                                            Class<?> exceptionCauseType,
                                                                            String exceptionCauseMessage,
                                                                            String hostName,
                                                                            String ipAddress,
                                                                            int port) {
        return actualError -> {
            softly.assertThat(actualError.getDescription()).isEqualTo(description);
            softly.assertThat(actualError.getHostName()).isEqualTo(hostName);
            softly.assertThat(actualError.getIpAddress()).isEqualTo(ipAddress);
            softly.assertThat(actualError.getPort()).isEqualTo(port);
            softly.assertThat(actualError.isResolved()).isFalse();
            softly.assertThat(actualError.getExceptionType()).isEqualTo(exceptionType.getName());
            softly.assertThat(actualError.getExceptionMessage()).isEqualTo(exceptionMessage);
            softly.assertThat(actualError.getExceptionCauseType()).isEqualTo(exceptionCauseType.getName());
            softly.assertThat(actualError.getExceptionCauseMessage()).isEqualTo(exceptionCauseMessage);
            return true;
        };
    }

    private static void assertDescriptionAndExceptionType(ApplicationError actualError,
                                                          String description,
                                                          Class<?> exceptionType) {
        assertThat(actualError.getDescription()).isEqualTo(description);
        assertThat(actualError.getExceptionType()).isEqualTo(exceptionType.getName());
    }

    private static void assertHostInfo(ApplicationError actualError, String hostName, String ipAddress, int port) {
        assertThat(actualError.getHostName()).isEqualTo(hostName);
        assertThat(actualError.getIpAddress()).isEqualTo(ipAddress);
        assertThat(actualError.getPort()).isEqualTo(port);
    }
}
