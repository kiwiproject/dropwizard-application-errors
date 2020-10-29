package org.kiwiproject.dropwizard.error.test.mockito;

import static org.assertj.core.api.Assertions.assertThat;

import lombok.experimental.UtilityClass;
import org.assertj.core.api.SoftAssertions;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.mockito.ArgumentMatcher;

/**
 * A collection of Mockito matchers for matching arguments of type {@link ApplicationError}.
 */
@UtilityClass
public class ApplicationErrorMatchers {

    /**
     * Return an {@link ArgumentMatcher} matching only the given description.
     *
     * @param description the description to match
     * @return the ArgumentMatcher instance
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description) {
        return actualError -> {
            assertThat(actualError.getDescription()).isEqualTo(description);
            return true;
        };
    }

    /**
     * Return an {@link ArgumentMatcher} matching the given description and exception type.
     *
     * @param description   the description to match
     * @param exceptionType the exception type to match (FQCN)
     * @return the ArgumentMatcher instance
     */
    public static ArgumentMatcher<ApplicationError> matchesApplicationError(String description,
                                                                            Class<?> exceptionType) {
        return actualError -> {
            assertDescriptionAndExceptionType(actualError, description, exceptionType);
            return true;
        };
    }

    /**
     * Return an {@link ArgumentMatcher} matching the given description, exception type, and exception message.
     *
     * @param description      the description to match
     * @param exceptionType    the exception type to match (FQCN)
     * @param exceptionMessage the exception message to match
     * @return the ArgumentMatcher instance
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
     * Return an {@link ArgumentMatcher} matching the given description, exception type, exception message, and
     * exception cause type.
     *
     * @param description        the description to match
     * @param exceptionType      the exception type to match (FQCN)
     * @param exceptionMessage   the exception message to match
     * @param exceptionCauseType the exception cause type to match (FQCN)
     * @return the ArgumentMatcher instance
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
     * Return an {@link ArgumentMatcher} matching the given description, exception type, exception message, and
     * exception cause type and message.
     *
     * @param description           the description to match
     * @param exceptionType         the exception type to match (FQCN)
     * @param exceptionMessage      the exception message to match
     * @param exceptionCauseType    the exception cause type to match (FQCN)
     * @param exceptionCauseMessage the exception cause message to match
     * @return the ArgumentMatcher instance
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
     * Return an {@link ArgumentMatcher} matching the given description and host information.
     *
     * @param description the description to match
     * @param hostName    the host name to match
     * @param ipAddress   the IP address to match
     * @param port        the port to match
     * @return the ArgumentMatcher instance
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
     * Return an {@link ArgumentMatcher} matching the given description, exception type, and host information.
     *
     * @param description   the description to match
     * @param exceptionType the exception type to match (FQCN)
     * @param hostName      the host name to match
     * @param ipAddress     the IP address to match
     * @param port          the port to match
     * @return the ArgumentMatcher instance
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
     * Return an {@link ArgumentMatcher} matching the given description, exception type and message, exception
     * cause type and message, and host information.
     *
     * @param description           the description to match
     * @param exceptionType         the exception type to match (FQCN)
     * @param exceptionMessage      the exception message to match
     * @param exceptionCauseType    the exception cause type to match (FQCN)
     * @param exceptionCauseMessage the exception cause message to match
     * @param hostName              the host name to match
     * @param ipAddress             the IP address to match
     * @param port                  the port to match
     * @return the ArgumentMatcher instance
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
     * Return an {@link ArgumentMatcher} that "softly" matches the given description, exception type and message,
     * exception cause type and message, and host information.
     *
     * @param softly                the AssertJ {@link SoftAssertions} to collect errors
     * @param description           the description to match
     * @param exceptionType         the exception type to match (FQCN)
     * @param exceptionMessage      the exception message to match
     * @param exceptionCauseType    the exception cause type to match (FQCN)
     * @param exceptionCauseMessage the exception cause message to match
     * @param hostName              the host name to match
     * @param ipAddress             the IP address to match
     * @param port                  the port to match
     * @return the ArgumentMatcher instance
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
