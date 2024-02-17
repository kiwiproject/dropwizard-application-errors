package org.kiwiproject.dropwizard.error.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kiwiproject.dropwizard.error.model.ApplicationError.Resolved;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@DisplayName("ApplicationError")
@ExtendWith(SoftAssertionsExtension.class)
class ApplicationErrorTest {

    private ApplicationError error;
    private String description;
    private String hostName;
    private String ipAddress;
    private Integer port;
    private Throwable throwable;

    @BeforeEach
    void setUp() throws UnknownHostException {
        description = "Something bad happened..";
        setupHostInformation();
        setupThrowable();

        ApplicationError.setPersistentHostInformation(hostName, ipAddress, port);
    }

    @AfterEach
    void tearDown() {
        ApplicationError.clearPersistentHostInformation();
    }

    private void setupHostInformation() throws UnknownHostException {
        var localHost = InetAddress.getLocalHost();
        hostName = localHost.getHostName();
        ipAddress = localHost.getHostAddress();
        port = 8080;
    }

    private void setupThrowable() {
        var cause = new IOException("I/O error");
        throwable = new UncheckedIOException("File not found or something", cause);
    }

    @Nested
    class ResolvedEnum {

        @ParameterizedTest
        @CsvSource({
                "true, YES",
                "false, NO"
        })
        void shouldCreateInstanceFromBoolean(boolean value, Resolved expected) {
            assertThat(Resolved.of(value))
                    .describedAs("Expected %s for value %b", expected, value)
                    .isEqualTo(expected);
        }
    }

    @Nested
    class HostInformation {

        @Test
        void shouldSetAndGet() {
            var hostInfo = new PersistentHostInformation("host-42", "192.168.1.42", 9042);
            ApplicationError.setPersistentHostInformation(hostInfo);

            var hostInfoFromApplicationError = ApplicationError.getPersistentHostInformation();

            assertThat(hostInfoFromApplicationError).isEqualTo(hostInfo);
        }

        @Test
        void shouldReturnSameObject() {
            var hostInfo = new PersistentHostInformation("host-84", "192.168.1.84", 9084);
            ApplicationError.setPersistentHostInformation(hostInfo);

            var first = ApplicationError.getPersistentHostInformation();
            var second = ApplicationError.getPersistentHostInformation();
            var third = ApplicationError.getPersistentHostInformation();

            assertThat(first)
                    .describedAs("Each PersistentHostInformation should be the same")
                    .isSameAs(second)
                    .isSameAs(third);
        }

        @Nested
        class WhenNotSet {

            @BeforeEach
            void setUp() {
                ApplicationError.clearPersistentHostInformation();
            }

            @Test
            void shouldThrowException_CreatingNewUnresolvedApplicationError_WithOnlyDescription() {
                var thrown = catchThrowable(() -> ApplicationError.newUnresolvedError("this will fail"));
                assertIllegalStateException(thrown);
            }

            @Test
            void shouldThrowException_CreatingNewUnresolvedApplicationError_WithDescriptionAndCause() {
                var thrown = catchThrowable(() -> ApplicationError.newUnresolvedError("this will fail", new RuntimeException("oops")));
                assertIllegalStateException(thrown);
            }

            private void assertIllegalStateException(Throwable thrown) {
                assertThat(thrown).isExactlyInstanceOf(IllegalStateException.class)
                        .hasMessage("Persistent host properties have not been set. Please call setPersistentHostInformation first. " +
                                "If this is a test be sure the test class has @ExtendWith(ApplicationErrorExtension.class) to set the properties.");
            }
        }
    }

    @Nested
    class TimestampGetters {

        @Test
        void shouldReturnNull_WhenTimestampFields_AreNull(SoftAssertions softly) {
            error = ApplicationError.builder().build();
            softly.assertThat(error.getCreatedAt()).isNull();
            softly.assertThat(error.getCreatedAtMillis()).isNull();
            softly.assertThat(error.getUpdatedAt()).isNull();
            softly.assertThat(error.getUpdatedAtMillis()).isNull();
        }

        @Test
        void shouldReturnEpochMillis_WhenTimestampFields_AreNotNull(SoftAssertions softly) {
            var now = ZonedDateTime.now(ZoneOffset.UTC);
            error = ApplicationError.builder()
                    .createdAt(now.minusHours(1))
                    .updatedAt(now)
                    .build();
            softly.assertThat(error.getCreatedAtMillis()).isEqualTo(now.minusHours(1).toInstant().toEpochMilli());
            softly.assertThat(error.getUpdatedAtMillis()).isEqualTo(now.toInstant().toEpochMilli());
        }
    }

    @Nested
    class WithId {

        @Test
        void shouldCreateNewApplicationErrorWithGivenId() {
            error = ApplicationError.newUnresolvedError(description, throwable);

            var errorHavingId = error.withId(42L);

            assertThat(errorHavingId.getId()).isEqualTo(42L);
            assertThat(errorHavingId)
                    .usingRecursiveComparison()
                    .ignoringFields("id")
                    .isEqualTo(error);
        }
    }

    @Nested
    class NewUnresolvedError {

        @Test
        void shouldCreateWithOnlyDescription_AndPresetPersistentHostInformation(SoftAssertions softly) {
            error = ApplicationError.newUnresolvedError(description, throwable);

            softly.assertThat(error.isResolved()).isFalse();
            assertCommonProperties(softly);
            assertExceptionProperties(softly);
        }

        @Test
        void shouldCreateWithOnlyDescription_AndCustomPersistentHostInformation(SoftAssertions softly) {
            ApplicationError.setPersistentHostInformation("acme.com", "192.168.1.1", 8080);

            error = ApplicationError.newUnresolvedError("oops");

            softly.assertThat(error.isResolved()).isFalse();
            softly.assertThat(error.getHostName()).isEqualTo("acme.com");
            softly.assertThat(error.getIpAddress()).isEqualTo("192.168.1.1");
            softly.assertThat(error.getPort()).isEqualTo(8080);
        }


        @Test
        void shouldCreateWithDescriptionAndThrowable(SoftAssertions softly) {
            error = ApplicationError.newUnresolvedError(description, throwable);

            softly.assertThat(error.isResolved()).isFalse();
            assertCommonProperties(softly);
            assertExceptionProperties(softly);
        }

        @Test
        void shouldCreateWithAllArguments(SoftAssertions softly) {
            error = ApplicationError.newUnresolvedError(description, hostName, ipAddress, port, throwable);

            softly.assertThat(error.isResolved()).isFalse();
            assertCommonProperties(softly);
            assertExceptionProperties(softly);
        }

        @Test
        void shouldPermitNullThrowable(SoftAssertions softly) {
            error = ApplicationError.newUnresolvedError(description, hostName, ipAddress, port, null);

            softly.assertThat(error.isResolved()).isFalse();
            assertNoExceptionProperties(softly);
        }
    }

    @Nested
    class NewError {

        @ParameterizedTest
        @CsvSource({
                " , NO, host-1, 192.168.1.42, 9042, 'description must not be blank' ",
                " '', NO, host-1, 192.168.1.42, 9042, 'description must not be blank'",
                " Oops, , host-1, 192.168.1.50, 9050, 'resolved must not be null' ",
                " Oops, NO, , 192.168.1.60, 9060, 'hostName must not be blank' ",
                " Oops, NO, '', 192.168.1.60, 9060, 'hostName must not be blank' ",
                " Oops, NO, host-1, , 9070, 'ipAddress must not be blank' ",
                " Oops, NO, host-1, '', 9070, 'ipAddress must not be blank' ",
                " Oops, NO, host-1, 192.168.1.80, -1, 'port must be a valid port' ",
                " Oops, NO, host-1, 192.168.1.80, 65536, 'port must be a valid port' ",
        })
        void shouldValidateArguments(String description,
                                     Resolved resolved,
                                     String hostName,
                                     String ipAddress,
                                     int port,
                                     String expectedErrorMessage) {
            
            assertThatThrownBy(() ->
                    ApplicationError.newError(description, resolved, hostName, ipAddress, port, null))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage(expectedErrorMessage);
        }

        @Test
        void shouldCreate_WithPresetHostInformation(SoftAssertions softly) {
            error = ApplicationError.newError(description, Resolved.YES, throwable);

            softly.assertThat(error.isResolved()).isTrue();
            assertCommonProperties(softly);
            assertExceptionProperties(softly);
        }

        @Test
        void shouldCreateResolvedError_AndCustomPersistentHostInformation(SoftAssertions softly) {
            error = ApplicationError.newError(description, Resolved.YES, hostName, ipAddress, port, throwable);

            softly.assertThat(error.isResolved()).isTrue();
            assertCommonProperties(softly);
            assertExceptionProperties(softly);
        }

        @Test
        void shouldPermitNullThrowable(SoftAssertions softly) {
            error = ApplicationError.newError(description, Resolved.NO, null);

            softly.assertThat(error.isResolved()).isFalse();
            assertCommonProperties(softly);
            assertNoExceptionProperties(softly);
        }
    }

    private void assertCommonProperties(SoftAssertions softly) {
        softly.assertThat(error.getId()).isNull();
        softly.assertThat(error.getCreatedAt()).isNotNull();
        softly.assertThat(error.getUpdatedAt()).isNotNull();
        softly.assertThat(error.getNumTimesOccurred()).isOne();
        softly.assertThat(error.getDescription()).isEqualTo(description);
        softly.assertThat(error.getHostName()).isEqualTo(hostName);
        softly.assertThat(error.getIpAddress()).isEqualTo(ipAddress);
        softly.assertThat(error.getPort()).isEqualTo(port);
    }

    private void assertExceptionProperties(SoftAssertions softly) {
        softly.assertThat(error.getExceptionType()).isEqualTo(throwable.getClass().getName());
        softly.assertThat(error.getExceptionMessage()).isEqualTo(throwable.getMessage());
        softly.assertThat(error.getExceptionCauseType()).isEqualTo(throwable.getCause().getClass().getName());
        softly.assertThat(error.getExceptionCauseMessage()).isEqualTo(throwable.getCause().getMessage());
        softly.assertThat(error.getStackTrace()).isEqualTo(ExceptionUtils.getStackTrace(throwable));
    }

    private void assertNoExceptionProperties(SoftAssertions softly) {
        softly.assertThat(error.getExceptionType()).isNull();
        softly.assertThat(error.getExceptionMessage()).isNull();
        softly.assertThat(error.getExceptionCauseType()).isNull();
        softly.assertThat(error.getExceptionCauseMessage()).isNull();
        softly.assertThat(error.getStackTrace()).isNull();
    }

}
