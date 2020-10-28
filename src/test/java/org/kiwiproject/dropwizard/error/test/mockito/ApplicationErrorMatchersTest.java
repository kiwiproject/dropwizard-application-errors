package org.kiwiproject.dropwizard.error.test.mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.dropwizard.error.test.mockito.ApplicationErrorMatchers.matchesApplicationError;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kiwiproject.dropwizard.error.model.ApplicationError;
import org.kiwiproject.dropwizard.error.model.PersistentHostInformation;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension;
import org.kiwiproject.dropwizard.error.test.junit.jupiter.ApplicationErrorExtension.HostInfo;
import org.opentest4j.AssertionFailedError;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

// TODO cleanup/reformat long lines...

@DisplayName("ApplicationErrorMatchers")
@ExtendWith(ApplicationErrorExtension.class)
class ApplicationErrorMatchersTest {

    private ErrorService service;
    private String hostName;
    private String ipAddress;
    private int port;

    @BeforeEach
    void setUp(@HostInfo PersistentHostInformation hostInfo) {
        this.service = mock(ErrorService.class);
        this.hostName = hostInfo.getHostName();
        this.ipAddress = hostInfo.getIpAddress();
        this.port = hostInfo.getPort();
    }

    @Nested
    class MatchesApplicationError {

        @Nested
        class ByDescription {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error");
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error")));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error");
                service.create(error);
                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("foo bar"))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByDescriptionAndExceptionType {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error", new IOException());
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error", IOException.class)));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error", new IOException());
                service.create(error);
                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("foo bar", MalformedURLException.class))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByDescriptionAndExceptionInfo {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error", new IOException("foo"));
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "foo")));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error", new IOException("foo"));
                service.create(error);
                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "BAR"))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByDescriptionAndExceptionInfoWithCauseType {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error",
                        new IOException("foo", new FileNotFoundException("foo not found")));
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "foo", FileNotFoundException.class)));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error",
                        new IOException("foo", new FileNotFoundException("foo not found")));
                service.create(error);
                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "foo", MalformedURLException.class))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByDescriptionAndDetailedExceptionInfo {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error",
                        new IOException("foo", new FileNotFoundException("foo not found")));
                service.create(error);

                verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "foo", FileNotFoundException.class, "foo not found")));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error",
                        new IOException("foo", new FileNotFoundException("foo not found")));
                service.create(error);
                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "foo", FileNotFoundException.class, "BOO"))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByDescriptionAndHostInfo {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error");
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error", hostName, ipAddress, port)));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error");

                service.create(error);
                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("my error", hostName, "0.0.0.0", port))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByDescriptionAndExceptionTypeAndHostInfo {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error", new IOException());
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error", IOException.class, hostName, ipAddress, port)));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error", new IOException());
                service.create(error);

                assertThatThrownBy(() -> verify(service).create(argThat(matchesApplicationError("my error", IOException.class, hostName, "0.0.0.0", port))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class ByAllParameters {

            @Test
            void shouldSucceed_WhenErrorMatches() {
                var error = ApplicationError.newUnresolvedError("my error",
                        new IOException("foo", new FileNotFoundException("foo not found")));
                service.create(error);
                verify(service).create(argThat(matchesApplicationError("my error", IOException.class, "foo", FileNotFoundException.class, "foo not found", hostName, ipAddress, port)));
            }

            @Test
            void shouldFail_WhenErrorDoesNotMatch() {
                var error = ApplicationError.newUnresolvedError("my error",
                        new IOException("foo", new FileNotFoundException("foo not found")));
                service.create(error);

                assertThatThrownBy(() -> verify(service).create(argThat(
                        matchesApplicationError("my error", IOException.class, "foo", FileNotFoundException.class, "BAR not found", hostName, ipAddress, port))))
                        .isInstanceOf(AssertionFailedError.class);
            }
        }

        @Nested
        class UsingSoftAssertions {

            @Nested
            class ByAllParameters {

                @Test
                void shouldSucceed_WhenErrorMatches() {
                    var error = ApplicationError.newUnresolvedError("my error",
                            new IOException("foo", new FileNotFoundException("foo not found")));
                    service.create(error);
                    var softly = new SoftAssertions();
                    verify(service).create(argThat(matchesApplicationError(softly, "my error", IOException.class, "foo", FileNotFoundException.class, "foo not found", hostName, ipAddress, port)));
                    var errorsCollected = softly.errorsCollected();
                    assertThat(errorsCollected).isEmpty();
                }

                @Test
                void shouldFail_WhenErrorDoesNotMatch() {
                    var error = ApplicationError.newUnresolvedError("my error",
                            new IOException("foo", new FileNotFoundException("foo not found")));
                    service.create(error);
                    var softly = new SoftAssertions();
                    verify(service).create(argThat(matchesApplicationError(softly, "YOUR error", IOException.class, "BAR", MalformedURLException.class, "foo not found", "OTHER-HOST", ipAddress, 65000)));
                    var errorsCollected = softly.errorsCollected();
                    assertThat(errorsCollected).isNotEmpty();
                }
            }
        }
    }

    interface ErrorService {
        void create(ApplicationError error);
    }
}
